package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;

class SqliteSongLayoutSnapshotTest {

    @TempDir
    Path tempDir;

    @Test
    void getOrCreateReusesOneLayoutPerSongAndBand() throws Exception {
        Path dbPath = tempDir.resolve("one-per-band.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo first = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            SongLayoutInfo again = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Other name");
            assertEquals(first.id(), again.id());
            assertEquals(1, fx.songLayouts.listSongLayouts(fx.songId).size());

            SongLayoutInfo otherBand = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutB, "Band B");
            assertNotEquals(first.id(), otherBand.id());
            assertEquals(2, fx.songLayouts.listSongLayouts(fx.songId).size());

            Optional<SongLayoutInfo> found = fx.songLayouts.findSongLayout(fx.songId, fx.layoutA);
            assertTrue(found.isPresent());
            assertEquals(first.id(), found.get().id());
            assertTrue(fx.songLayouts.findSongLayout(fx.songId + 99, fx.layoutA).isEmpty());
        }
    }

    @Test
    void addItemSnapshotsMatchingSongLayout() throws Exception {
        Path dbPath = tempDir.resolve("add-snapshot.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerB, null);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutA, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);

            List<SetlistItemInfo> items = fx.setlists.listItems(fx.setlistId);
            assertEquals(1, items.size());
            assertEquals(songLayout.id(), items.get(0).songLayoutId());

            List<SetlistBandAssignmentInfo> copied = fx.setlists.listBandAssignments(itemId);
            assertEquals(2, copied.size());
            assertEquals(1, partFor(copied, fx.playerA));
            assertEquals(null, partFor(copied, fx.playerB));
        }
    }

    @Test
    void addItemDoesNotSnapshotWhenBandDoesNotMatch() throws Exception {
        Path dbPath = tempDir.resolve("add-no-match.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutB, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);
            assertEquals(null, fx.setlists.listItems(fx.setlistId).get(0).songLayoutId());
            assertTrue(fx.setlists.listBandAssignments(itemId).isEmpty());
        }
    }

    @Test
    void addItemDoesNotSnapshotWhenSetlistHasNoBand() throws Exception {
        Path dbPath = tempDir.resolve("add-no-band.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);

            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);
            assertEquals(null, fx.setlists.listItems(fx.setlistId).get(0).songLayoutId());
            assertTrue(fx.setlists.listBandAssignments(itemId).isEmpty());
        }
    }

    @Test
    void libraryAndSetlistAssignmentsStayIndependent() throws Exception {
        Path dbPath = tempDir.resolve("independent.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutA, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);
            assertEquals(1, partFor(fx.setlists.listBandAssignments(itemId), fx.playerA));

            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 3);
            assertEquals(1, partFor(fx.setlists.listBandAssignments(itemId), fx.playerA));
            assertEquals(3, fx.songLayouts.listAssignments(songLayout.id()).stream()
                    .filter(a -> a.playerId() == fx.playerA)
                    .findFirst()
                    .orElseThrow()
                    .partNumber());

            fx.setlists.upsertBandAssignment(itemId, fx.playerA, 2);
            assertEquals(2, partFor(fx.setlists.listBandAssignments(itemId), fx.playerA));
            assertEquals(3, fx.songLayouts.listAssignments(songLayout.id()).stream()
                    .filter(a -> a.playerId() == fx.playerA)
                    .findFirst()
                    .orElseThrow()
                    .partNumber());
        }
    }

    @Test
    void remapSetlistBandSnapshotsMatchingSongLayout() throws Exception {
        Path dbPath = tempDir.resolve("remap.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo layoutA = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(layoutA.id(), fx.playerA, 1);
            SongLayoutInfo layoutB = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutB, "Band B");
            fx.songLayouts.setAssignment(layoutB.id(), fx.playerC, 4);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutA, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);
            fx.setlists.upsertBandAssignment(itemId, fx.playerA, 9);

            fx.setlists.remapItemsToBandLayout(fx.setlistId, fx.layoutB);
            List<SetlistBandAssignmentInfo> after = fx.setlists.listBandAssignments(itemId);
            assertEquals(layoutB.id(), fx.setlists.listItems(fx.setlistId).get(0).songLayoutId());
            assertEquals(4, partFor(after, fx.playerC));
            assertEquals(null, partFor(after, fx.playerA));
        }
    }

    @Test
    void duplicateSameBandCopiesSetlistAssignmentsNotSongLayout() throws Exception {
        Path dbPath = tempDir.resolve("dup-same.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutA, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);
            fx.setlists.upsertBandAssignment(itemId, fx.playerA, 7);

            long copyId = fx.setlists.duplicateSetlist(
                    fx.setlistId, "Copy", fx.layoutA, false, null, null, null, null, null);
            long copyItemId = fx.setlists.listItems(copyId).get(0).id();
            assertEquals(7, partFor(fx.setlists.listBandAssignments(copyItemId), fx.playerA));
            assertEquals(1, fx.songLayouts.listAssignments(songLayout.id()).stream()
                    .filter(a -> a.playerId() == fx.playerA)
                    .findFirst()
                    .orElseThrow()
                    .partNumber());
        }
    }

    @Test
    void deleteSongLayoutUnlinksItemsButKeepsSetlistAssignments() throws Exception {
        Path dbPath = tempDir.resolve("delete-layout.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Fixture fx = Fixture.create(database);
            SongLayoutInfo songLayout = fx.songLayouts.getOrCreateSongLayout(
                    fx.songId, fx.layoutA, "Band A");
            fx.songLayouts.setAssignment(songLayout.id(), fx.playerA, 1);

            fx.setlists.updateSetlist(
                    fx.setlistId, "Set", fx.layoutA, null, 0, false, null, null, null, null, null);
            long itemId = fx.setlists.addItem(fx.setlistId, fx.songId, 0, null, null);

            fx.songLayouts.deleteSongLayout(songLayout.id());
            assertTrue(fx.songLayouts.findSongLayout(fx.songId, fx.layoutA).isEmpty());
            assertEquals(null, fx.setlists.listItems(fx.setlistId).get(0).songLayoutId());
            assertEquals(1, partFor(fx.setlists.listBandAssignments(itemId), fx.playerA));
        }
    }

    private static Integer partFor(List<SetlistBandAssignmentInfo> rows, long playerId) {
        for (SetlistBandAssignmentInfo row : rows) {
            if (row.playerId() == playerId) {
                return row.partNumber();
            }
        }
        return null;
    }

    private static final class Fixture {
        final SqliteSongLayoutRepository songLayouts;
        final SqliteSetlistRepository setlists;
        final long songId;
        final long layoutA;
        final long layoutB;
        final long playerA;
        final long playerB;
        final long playerC;
        final long setlistId;

        private Fixture(
                SqliteSongLayoutRepository songLayouts,
                SqliteSetlistRepository setlists,
                long songId,
                long layoutA,
                long layoutB,
                long playerA,
                long playerB,
                long playerC,
                long setlistId) {
            this.songLayouts = songLayouts;
            this.setlists = setlists;
            this.songId = songId;
            this.layoutA = layoutA;
            this.layoutB = layoutB;
            this.playerA = playerA;
            this.playerB = playerB;
            this.playerC = playerC;
            this.setlistId = setlistId;
        }

        static Fixture create(SqliteDatabase database) throws Exception {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSongLayoutRepository songLayouts = new SqliteSongLayoutRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);

            long bandA = bands.addBand("Band A", null);
            long bandB = bands.addBand("Band B", null);
            long layoutA = bands.getOrCreatePrimaryLayout(bandA).id();
            long layoutB = bands.getOrCreatePrimaryLayout(bandB).id();
            long playerA = players.addPlayer("Alice", null, null);
            long playerB = players.addPlayer("Bob", null, null);
            long playerC = players.addPlayer("Cara", null, null);
            bands.setSlot(layoutA, playerA, 0, 0, 9, 7);
            bands.setSlot(layoutA, playerB, 10, 0, 9, 7);
            bands.setSlot(layoutB, playerC, 0, 0, 9, 7);

            long songId = insertSong(database);
            long setlistId = setlists.addSetlist("Set", null);
            return new Fixture(
                    songLayouts, setlists,
                    songId, layoutA, layoutB, playerA, playerB, playerC, setlistId);
        }

        private static long insertSong(SqliteDatabase database) throws Exception {
            String now = SqliteTimestamps.now();
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT INTO Song (title, composers, duration_seconds, transcriber, rating, status_id,
                       notes, lyrics, last_played_at, total_plays, parts, created_at, updated_at)
                    VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, 0, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "Test Song");
                statement.setString(2, "Composer");
                statement.setInt(3, 60);
                statement.setString(4, "[]");
                statement.setString(5, now);
                statement.setString(6, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    return keys.getLong(1);
                }
            }
        }
    }
}
