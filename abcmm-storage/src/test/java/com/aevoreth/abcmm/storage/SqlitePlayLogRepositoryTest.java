package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.PlayLogEntry;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;

class SqlitePlayLogRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void logPlayRefreshesLastPlayedAndTotalPlays() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            SqlitePlayLogRepository playLog = new SqlitePlayLogRepository(database);
            SqliteSongRepository songs = new SqliteSongRepository(database, false);

            playLog.logPlay(1, null, null);
            LibrarySong song = songs.findSongById(1).orElseThrow();
            assertEquals(1, song.totalPlays());
            assertTrue(song.lastPlayedAt() != null && !song.lastPlayedAt().isBlank());

            String earlier = "2020-01-01T00:00:00Z";
            playLog.logPlayAt(1, earlier, null, "note");
            List<PlayLogEntry> history = playLog.getPlayHistory(1, 10);
            assertEquals(2, history.size());
            song = songs.findSongById(1).orElseThrow();
            assertEquals(2, song.totalPlays());
            // last_played_at is MAX(played_at); the first logPlay() used "now"
            assertTrue(song.lastPlayedAt().compareTo(earlier) > 0);
        }
    }

    @Test
    void updateAndDeleteRefreshAggregates() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            SqlitePlayLogRepository playLog = new SqlitePlayLogRepository(database);
            SqliteSongRepository songs = new SqliteSongRepository(database, false);

            playLog.logPlayAt(2, "2021-06-01T12:00:00Z", null, null);
            playLog.logPlayAt(2, "2022-06-01T12:00:00Z", null, null);
            List<PlayLogEntry> history = playLog.getPlayHistory(2, 10);
            assertEquals(2, history.size());

            long olderId = history.stream()
                    .filter(e -> e.playedAt().startsWith("2021"))
                    .findFirst()
                    .orElseThrow()
                    .id();
            playLog.updatePlay(olderId, "2023-01-01T00:00:00Z", "edited");
            LibrarySong song = songs.findSongById(2).orElseThrow();
            assertEquals("2023-01-01T00:00:00Z", song.lastPlayedAt());
            assertEquals(2, song.totalPlays());

            playLog.deletePlay(olderId);
            song = songs.findSongById(2).orElseThrow();
            assertEquals(1, song.totalPlays());
            assertEquals("2022-06-01T12:00:00Z", song.lastPlayedAt());
        }
    }

    @Test
    void updateSongAppMetadataPersistsRatingAndNotes() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            SqliteSongRepository songs = new SqliteSongRepository(database, false);
            songs.updateSongAppMetadata(1, SongAppMetadataUpdate.ratingOnly(4));
            songs.updateSongAppMetadata(1, SongAppMetadataUpdate.full(4, 2L, "n1", "l1"));

            var detail = songs.getSongForDetail(1).orElseThrow();
            assertEquals(4, detail.rating());
            assertEquals(2L, detail.statusId());
            assertEquals("n1", detail.notes());
            assertEquals("l1", detail.lyrics());
            assertEquals(1, songs.listUnlockedSetlistsContainingSong(1).size());
            assertEquals("Gig", songs.listUnlockedSetlistsContainingSong(1).get(0).name());
        }
    }
}
