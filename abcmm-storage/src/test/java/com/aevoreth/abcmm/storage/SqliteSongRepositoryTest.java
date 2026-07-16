package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
            assertEquals(List.of("Melody", "Part 2", "Part 3"), songs.get(0).partNames());
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
    void fixtureFileExists() throws Exception {
        Path db = FixtureDatabases.createLibraryFixture(tempDir.resolve("library.sqlite"));
        assertTrue(Files.isRegularFile(db));
    }
}
