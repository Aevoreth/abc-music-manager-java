package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheet;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheetBuilder;

class SetPlayPartsSheetBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void showsSongLayoutPartsWhenTheSetlistOverrideIsSparse() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("parts.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSongLayoutRepository layouts = new SqliteSongLayoutRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);

            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long alice = players.addPlayer("Alice", null, null);
            long bob = players.addPlayer("Bob", null, null);
            bands.setSlot(layoutId, alice, 0, 0, 9, 7);
            bands.setSlot(layoutId, bob, 10, 0, 9, 7);
            long songId = insertSong(database);
            SongLayoutInfo songLayout = layouts.getOrCreateSongLayout(songId, layoutId, "Band");
            layouts.setAssignment(songLayout.id(), alice, 1);
            layouts.setAssignment(songLayout.id(), bob, 2);

            long setlistId = setlists.addSetlist("Set", null);
            setlists.updateSetlist(
                    setlistId, "Set", layoutId, null, 0, false, null, null, null, null, null, null);
            long sparse = setlists.addItem(setlistId, songId, 0, null, songLayout.id());
            setlists.upsertBandAssignment(sparse, bob, 1);
            long cleared = setlists.addItem(setlistId, songId, 1, null, songLayout.id());
            setlists.upsertBandAssignment(cleared, alice, null);

            SetlistInfo setlist = setlists.listSetlists().stream()
                    .filter(candidate -> candidate.id() == setlistId)
                    .findFirst()
                    .orElseThrow();
            List<SetlistItemInfo> items = setlists.listItems(setlistId);
            SetPlayPartsSheet sheet = SetPlayPartsSheetBuilder.build(
                    setlist, items, setlists, bands, players, layouts, null);

            int aliceColumn = column(sheet, "Alice");
            int bobColumn = column(sheet, "Bob");
            assertEquals("1: Fiddle", sheet.rows().get(0).cells().get(aliceColumn));
            assertEquals("1: Fiddle", sheet.rows().get(0).cells().get(bobColumn));
            assertEquals("", sheet.rows().get(1).cells().get(aliceColumn));
            assertEquals("2: Harp", sheet.rows().get(1).cells().get(bobColumn));
        }
    }

    private static int column(SetPlayPartsSheet sheet, String title) {
        for (int i = 0; i < sheet.columns().size(); i++) {
            if (title.equals(sheet.columns().get(i).title())) {
                return i;
            }
        }
        throw new AssertionError(title);
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
            statement.setString(1, "Song");
            statement.setString(2, "Composer");
            statement.setInt(3, 60);
            statement.setString(4, """
                    [{"part_number":1,"part_name":"Fiddle"},{"part_number":2,"part_name":"Harp"}]
                    """);
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
