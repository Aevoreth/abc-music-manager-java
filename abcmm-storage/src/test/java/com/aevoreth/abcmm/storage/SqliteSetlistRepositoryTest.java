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

    private static List<Long> idsInFolder(SqliteSetlistRepository repo, Long folderId)
            throws Exception {
        return repo.listSetlists().stream()
                .filter(s -> folderId == null ? s.folderId() == null : folderId.equals(s.folderId()))
                .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                .map(SetlistInfo::id)
                .toList();
    }
}
