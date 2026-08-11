package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;

class SqliteSongRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void listsPrimaryLibrarySongsAndExcludesOthers() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            List<LibrarySong> songs = repository.listLibrarySongs(LibraryFilter.cleared());
            assertEquals(2, songs.size());
            assertEquals("Alpha March", songs.get(0).title());
            assertEquals("Beta Waltz", songs.get(1).title());
            assertEquals(3, songs.get(0).partCount());
            assertEquals("New", songs.get(0).statusName());
            assertEquals("Ada", songs.get(0).transcriber());
            assertEquals(180, songs.get(0).durationSeconds());
            assertTrue(songs.get(0).inUpcomingSet());
            assertEquals(List.of("1: Melody", "2: Part 2", "3: Part 3"), songs.get(0).partNames());
        }
    }

    @Test
    void filtersByTitleTokenAndStatusAndInSet() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            LibraryFilter titleFilter = LibraryFilter.cleared();
            titleFilter.setTitleOrComposer("alpha");
            assertEquals(1, repository.listLibrarySongs(titleFilter).size());

            LibraryFilter statusFilter = LibraryFilter.cleared();
            statusFilter.setStatusIds(List.of(2L));
            assertEquals(1, repository.listLibrarySongs(statusFilter).size());
            assertEquals("Beta Waltz", repository.listLibrarySongs(statusFilter).get(0).title());

            LibraryFilter inSet = LibraryFilter.cleared();
            inSet.setInSet(LibraryFilter.InSet.YES);
            assertEquals(1, repository.listLibrarySongs(inSet).size());
            assertEquals("Alpha March", repository.listLibrarySongs(inSet).get(0).title());
        }
    }

    @Test
    void filtersByRatingAndParts() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            LibraryFilter rating = LibraryFilter.cleared();
            rating.setRatingFrom(5);
            rating.setRatingTo(5);
            assertEquals(1, repository.listLibrarySongs(rating).size());

            LibraryFilter parts = LibraryFilter.cleared();
            parts.setPartsMin(3);
            parts.setPartsMax(3);
            assertEquals(1, repository.listLibrarySongs(parts).size());
            assertEquals("Alpha March", repository.listLibrarySongs(parts).get(0).title());
        }
    }

    @Test
    void filtersByTranscriberAndLastPlayedNever() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            assertEquals(List.of("Ada", "Ben"), repository.listUniqueTranscribers());

            LibraryFilter byTranscriber = LibraryFilter.cleared();
            byTranscriber.setTranscribers(List.of("Ada"));
            assertEquals(1, repository.listLibrarySongs(byTranscriber).size());
            assertEquals("Alpha March", repository.listLibrarySongs(byTranscriber).get(0).title());

            LibraryFilter neverPlayed = LibraryFilter.cleared();
            neverPlayed.setLastPlayedNever(true);
            assertEquals(1, repository.listLibrarySongs(neverPlayed).size());
            assertEquals("Alpha March", repository.listLibrarySongs(neverPlayed).get(0).title());
        }
    }

    @Test
    void rejectsMissingAndWrongVersionDatabases() throws Exception {
        Path missing = tempDir.resolve("missing.sqlite");
        assertThrows(LibraryException.class, () -> SqliteDatabase.openReadOnly(missing));

        Path wrong = FixtureDatabases.createWrongVersionFixture(tempDir.resolve("wrong.sqlite"));
        LibraryException ex = assertThrows(LibraryException.class, () -> SqliteDatabase.openReadOnly(wrong));
        assertTrue(ex.getMessage().contains("Unsupported schema version"));
    }

    @Test
    void listsStatusesFolderRulesAndAccountTargets() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            assertEquals(2, repository.listStatuses().size());
            assertEquals(1, repository.listFolderRules().size());
            assertEquals("Archive", repository.listFolderRules().get(0).path());
            assertEquals(1, repository.listAccountTargets().size());
            assertEquals("Main", repository.listAccountTargets().get(0).accountName());
        }
    }

    @Test
    void resolvesPrimaryAbcPathAndFindsSongById() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            Optional<Path> path = repository.resolvePrimaryAbcPath(1);
            assertTrue(path.isPresent());
            assertEquals(Path.of("/music/alpha.abc"), path.get());

            Optional<LibrarySong> song = repository.findSongById(1);
            assertTrue(song.isPresent());
            assertEquals("Alpha March", song.get().title());

            assertTrue(repository.resolvePrimaryAbcPath(999).isEmpty());
            assertTrue(repository.findSongById(999).isEmpty());
        }
    }

    @Test
    void findsSongIdByExactFilePath() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db);
             SqliteSongRepository repository = new SqliteSongRepository(database)) {
            assertEquals(Optional.of(1L), repository.findSongIdByFilePath("/music/alpha.abc"));
            assertEquals(Optional.of(2L), repository.findSongIdByFilePath("/music/beta.abc"));
            assertTrue(repository.findSongIdByFilePath("/music/missing.abc").isEmpty());
            assertTrue(repository.findSongIdByFilePath("").isEmpty());
            assertTrue(repository.findSongIdByFilePath(null).isEmpty());
        }
    }

    @Test
    void renamePrimaryAbcFileMovesDiskAndUpdatesDb() throws Exception {
        Path music = tempDir.resolve("music");
        Files.createDirectories(music);
        Path oldFile = music.resolve("old-name.abc");
        Files.writeString(oldFile, "%%song-title       Tune\nX:1\n");

        Path db = tempDir.resolve("rename.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            String now = "2024-01-01T00:00:00Z";
            try (var insertSong = database.connection().prepareStatement(
                    """
                            INSERT INTO Song (title, composers, duration_seconds, parts, created_at, updated_at)
                            VALUES ('Tune', 'Ada', 60, '[]', ?, ?)
                            """);
                 var insertFile = database.connection().prepareStatement(
                         """
                                 INSERT INTO SongFile (song_id, file_path, is_primary_library, is_set_copy,
                                    scan_excluded, created_at, updated_at)
                                 VALUES (1, ?, 1, 0, 0, ?, ?)
                                 """)) {
                insertSong.setString(1, now);
                insertSong.setString(2, now);
                insertSong.executeUpdate();
                insertFile.setString(1, oldFile.toString());
                insertFile.setString(2, now);
                insertFile.setString(3, now);
                insertFile.executeUpdate();
            }

            SqliteSongRepository repository = new SqliteSongRepository(database, false);
            Path renamed = repository.renamePrimaryAbcFile(1, "new-name.abc");
            assertEquals(music.resolve("new-name.abc"), renamed);
            assertTrue(Files.isRegularFile(renamed));
            assertTrue(Files.notExists(oldFile));
            assertEquals(Optional.of(renamed), repository.resolvePrimaryAbcPath(1));

            // No-op when only missing .abc extension would be appended to same stem.
            Path again = repository.renamePrimaryAbcFile(1, "new-name");
            assertEquals(renamed, again);
        }
    }

    @Test
    void sanitizeAbcFileNameRejectsPathsAndAppendsExtension() throws Exception {
        assertEquals("song.abc", SqliteSongRepository.sanitizeAbcFileName("song"));
        assertEquals("song.abc", SqliteSongRepository.sanitizeAbcFileName("song.abc"));
        assertEquals("C:/x/a.abc", SqliteSongRepository.replaceFileName("C:/x/old.abc", "a.abc"));
        assertEquals("C:\\x\\a.abc", SqliteSongRepository.replaceFileName("C:\\x\\old.abc", "a.abc"));
        assertThrows(LibraryException.class, () -> SqliteSongRepository.sanitizeAbcFileName("a/b.abc"));
        assertThrows(LibraryException.class, () -> SqliteSongRepository.sanitizeAbcFileName("bad:name.abc"));
    }
}
