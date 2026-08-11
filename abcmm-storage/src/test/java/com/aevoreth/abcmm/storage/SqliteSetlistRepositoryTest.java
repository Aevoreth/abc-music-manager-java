package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;

class SqliteSetlistRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void moveSetlistToFolderAndReorderFolders() throws Exception {
        Path dbPath = tempDir.resolve("setlists.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteSetlistRepository repo = new SqliteSetlistRepository(database);

            long folderA = repo.addFolder("A");
            long folderB = repo.addFolder("B");
            long set1 = repo.addSetlist("One", folderA);
            long set2 = repo.addSetlist("Two", folderA);
            long set3 = repo.addSetlist("Three", null);

            // New setlists are inserted at sort_order 0 (bumping others).
            assertEquals(List.of(set2, set1), idsInFolder(repo, folderA));
            assertEquals(List.of(set3), idsInFolder(repo, null));

            repo.moveSetlistToFolder(set1, folderB, 0);
            assertEquals(List.of(set2), idsInFolder(repo, folderA));
            assertEquals(List.of(set1), idsInFolder(repo, folderB));

            repo.moveSetlistToFolder(set3, folderB, 0);
            assertEquals(List.of(set3, set1), idsInFolder(repo, folderB));
            assertEquals(List.of(), idsInFolder(repo, null));

            SetlistInfo moved = repo.listSetlists().stream()
                    .filter(s -> s.id() == set3)
                    .findFirst()
                    .orElseThrow();
            assertEquals(folderB, moved.folderId());
            assertEquals(0, moved.sortOrder());

            repo.moveSetlistToFolder(set1, folderB, 0);
            assertEquals(List.of(set1, set3), idsInFolder(repo, folderB));

            repo.moveSetlistToFolder(set2, null, 0);
            assertEquals(List.of(set2), idsInFolder(repo, null));
            assertNull(repo.listSetlists().stream()
                    .filter(s -> s.id() == set2)
                    .findFirst()
                    .orElseThrow()
                    .folderId());

            repo.reorderFolders(List.of(folderB, folderA));
            List<SetlistFolderInfo> folders = repo.listFolders();
            assertEquals(folderB, folders.get(0).id());
            assertEquals(0, folders.get(0).sortOrder());
            assertEquals(folderA, folders.get(1).id());
            assertEquals(1, folders.get(1).sortOrder());
        }
    }

    @Test
    void duplicateAndMergeSetlistSongs() throws Exception {
        Path dbPath = tempDir.resolve("setlist-copy.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            seedSong(database, 1, "Alpha");
            seedSong(database, 2, "Beta");
            seedSong(database, 3, "Gamma");

            SqliteSetlistRepository repo = new SqliteSetlistRepository(database);
            long source = repo.addSetlist("Friday", null);
            repo.updateSetlist(source, "Friday", null, null, 0, false, 20, "notes", "2026-08-01", "19:00", 3600);
            repo.addItem(source, 1, 0, null, null);
            repo.addItem(source, 2, 1, 15, null);

            long target = repo.addSetlist("Saturday", null);
            repo.addItem(target, 3, 0, null, null);

            long copyId = repo.duplicateSetlist(
                    source, "Copy of Friday", null, true, 20, "notes", "2026-08-01", "19:00", 3600);
            SetlistInfo copy = repo.listSetlists().stream()
                    .filter(s -> s.id() == copyId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("Copy of Friday", copy.name());
            assertEquals(true, copy.locked());
            assertEquals(2, repo.listItems(copyId).size());
            assertEquals(1, repo.listItems(copyId).get(0).songId());
            assertEquals(15, repo.listItems(copyId).get(1).overrideChangeDurationSeconds());

            int prepended = repo.mergeSetlistSongs(target, source, true);
            assertEquals(2, prepended);
            List<Long> songIds = repo.listItems(target).stream()
                    .map(item -> item.songId())
                    .toList();
            assertEquals(List.of(1L, 2L, 3L), songIds);

            int appended = repo.mergeSetlistSongs(target, source, false);
            assertEquals(2, appended);
            songIds = repo.listItems(target).stream()
                    .map(item -> item.songId())
                    .toList();
            assertEquals(List.of(1L, 2L, 3L, 1L, 2L), songIds);

            long copyNoLayout = repo.duplicateSetlist(
                    source, "No Layout Copy", null, false, 20, null, null, null, null);
            assertEquals(2, repo.listItems(copyNoLayout).size());
            assertEquals(1, repo.listItems(copyNoLayout).get(0).songId());
            assertNull(repo.listSetlists().stream()
                    .filter(s -> s.id() == copyNoLayout)
                    .findFirst()
                    .orElseThrow()
                    .bandLayoutId());
        }
    }

    private static void seedSong(SqliteDatabase database, long id, String title) throws Exception {
        try (var statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (id, title, composers, duration_seconds, parts, created_at, updated_at)
                VALUES (?, ?, '', 60, '[]', '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, title);
            statement.executeUpdate();
        }
        try (var statement = database.connection().prepareStatement(
                """
                INSERT INTO SongFile (song_id, file_path, is_primary_library, is_set_copy,
                                     scan_excluded, created_at, updated_at)
                VALUES (?, ?, 1, 0, 0, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, "C:/music/" + title + ".abc");
            statement.executeUpdate();
        }
    }

    private static List<Long> idsInFolder(SqliteSetlistRepository repo, Long folderId)
            throws Exception {
        return repo.listSetlists().stream()
                .filter(s -> folderId == null ? s.folderId() == null : folderId.equals(s.folderId()))
                .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                .map(SetlistInfo::id)
                .toList();
    }
}
