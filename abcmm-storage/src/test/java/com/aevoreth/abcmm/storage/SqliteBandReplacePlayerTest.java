package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;

class SqliteBandReplacePlayerTest {

    @TempDir
    Path tempDir;

    @Test
    void replacePlayerTransfersSlotAndAssignments() throws Exception {
        Path dbPath = tempDir.resolve("replace-player.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSongLayoutRepository songLayouts = new SqliteSongLayoutRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);

            long bandId = bands.addBand("Test Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long playerA = players.addPlayer("Alice", null, null);
            long playerB = players.addPlayer("Bob", null, null);
            bands.setSlot(layoutId, playerA, 2, 3, 9, 7);
            bands.updateLayout(layoutId, "Test Band", "[" + playerA + "]");

            long instrumentId = firstInstrumentId(database);
            players.setPlayerInstrument(playerA, instrumentId, true, false, null);
            players.setPlayerInstrument(playerB, instrumentId, false, false, null);

            long songId = insertSong(database);
            var songLayout = songLayouts.getOrCreateSongLayout(songId, layoutId, "Default");
            songLayouts.setAssignment(songLayout.id(), playerA, 1);

            long setlistId = setlists.addSetlist("Test Set", null);
            long itemId = setlists.addItem(setlistId, songId, 0, null, songLayout.id());
            setlists.upsertBandAssignment(itemId, playerA, 2);

            bands.replacePlayerInBandLayout(layoutId, bandId, playerA, playerB);

            List<BandLayoutSlotInfo> slots = bands.listSlots(layoutId);
            assertEquals(1, slots.size());
            assertEquals(playerB, slots.get(0).playerId());
            assertEquals(2, slots.get(0).x());
            assertEquals(3, slots.get(0).y());

            List<SongLayoutAssignmentInfo> songAssigns = songLayouts.listAssignments(songLayout.id());
            assertEquals(1, songAssigns.size());
            assertEquals(playerB, songAssigns.get(0).playerId());
            assertEquals(1, songAssigns.get(0).partNumber());

            List<SetlistBandAssignmentInfo> setlistAssigns = setlists.listBandAssignments(itemId);
            assertEquals(1, setlistAssigns.size());
            assertEquals(playerB, setlistAssigns.get(0).playerId());
            assertEquals(2, setlistAssigns.get(0).partNumber());

            String exportOrder;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT export_column_order FROM BandLayout WHERE id = ?")) {
                statement.setLong(1, layoutId);
                try (ResultSet rs = statement.executeQuery()) {
                    assertTrue(rs.next());
                    exportOrder = rs.getString(1);
                }
            }
            assertEquals("[" + playerB + "]", exportOrder);

            assertEquals(1, instrumentFlag(database, playerA, instrumentId));
            assertEquals(0, instrumentFlag(database, playerB, instrumentId));
        }
    }

    @Test
    void replacePlayerRejectsNewPlayerAlreadyOnLayout() throws Exception {
        Path dbPath = tempDir.resolve("replace-reject-dup.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);

            long bandId = bands.addBand("Test Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long playerA = players.addPlayer("Alice", null, null);
            long playerB = players.addPlayer("Bob", null, null);
            bands.setSlot(layoutId, playerA, 0, 0, 9, 7);
            bands.setSlot(layoutId, playerB, 5, 5, 9, 7);

            LibraryException ex = assertThrows(
                    LibraryException.class,
                    () -> bands.replacePlayerInBandLayout(layoutId, bandId, playerA, playerB));
            assertTrue(ex.getMessage().contains("already on layout"));
        }
    }

    @Test
    void replacePlayerRejectsOldPlayerNotOnLayout() throws Exception {
        Path dbPath = tempDir.resolve("replace-reject-missing.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);

            long bandId = bands.addBand("Test Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long playerA = players.addPlayer("Alice", null, null);
            long playerB = players.addPlayer("Bob", null, null);

            LibraryException ex = assertThrows(
                    LibraryException.class,
                    () -> bands.replacePlayerInBandLayout(layoutId, bandId, playerA, playerB));
            assertTrue(ex.getMessage().contains("not on layout"));
        }
    }

    private static long insertSong(SqliteDatabase database) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (title, composers, duration_seconds, transcriber, rating, status_id, notes, lyrics,
                   last_played_at, total_plays, parts, created_at, updated_at)
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

    private static long firstInstrumentId(SqliteDatabase database) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id FROM Instrument ORDER BY id LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private static int instrumentFlag(SqliteDatabase database, long playerId, long instrumentId)
            throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT has_instrument FROM PlayerInstrument
                WHERE player_id = ? AND instrument_id = ?
                """)) {
            statement.setLong(1, playerId);
            statement.setLong(2, instrumentId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }
}
