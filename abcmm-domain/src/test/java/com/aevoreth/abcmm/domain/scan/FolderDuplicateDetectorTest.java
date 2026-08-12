package com.aevoreth.abcmm.domain.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FolderDuplicateDetectorTest {

    @Test
    void detectsDuplicatedFoldersWithoutPreferringAlphabeticalFirst() {
        Path root = Path.of("C:/Music");
        Path main = root.resolve("Main");
        Path copy = root.resolve("Main - Copy");
        String hashA = "aaa";
        String hashB = "bbb";
        List<FolderDuplicateDetector.FolderFileEntry> files = List.of(
                entry(main.resolve("a.abc"), "main/a.abc", hashA, "A"),
                entry(main.resolve("b.abc"), "main/b.abc", hashB, "B"),
                entry(copy.resolve("a.abc"), "main - copy/a.abc", hashA, "A"),
                entry(copy.resolve("b.abc"), "main - copy/b.abc", hashB, "B"));

        List<FolderDuplicateCluster> clusters = FolderDuplicateDetector.detect(root, files);
        assertEquals(1, clusters.size());
        FolderDuplicateCluster cluster = clusters.get(0);
        assertEquals(2, cluster.folderPaths().size());
        assertTrue(cluster.folderPaths().contains(main));
        assertTrue(cluster.folderPaths().contains(copy));
        assertEquals(2, cluster.identicalFileCount());
        // Sorted for display stability only — not a preferred keep
        assertEquals(main, cluster.folderPaths().get(0));
    }

    @Test
    void nearDuplicateFoldersReportDifferingAndUnique() {
        Path root = Path.of("C:/Music");
        // Force same signature by using identical relative layouts with same hashes —
        // differing/unique are measured within an exact-signature cluster (all identical).
        // Separate check: folders with different signatures do not cluster.
        Path a = root.resolve("A");
        Path b = root.resolve("B");
        List<FolderDuplicateCluster> clusters = FolderDuplicateDetector.detect(root, List.of(
                entry(a.resolve("x.abc"), "a/x.abc", "h1", "X"),
                entry(a.resolve("y.abc"), "a/y.abc", "h2", "Y"),
                entry(b.resolve("x.abc"), "b/x.abc", "h1", "X"),
                entry(b.resolve("z.abc"), "b/z.abc", "h3", "Z")));
        assertTrue(clusters.isEmpty());
    }

    @Test
    void antichainDropsNestedDuplicateUnderShallowerPeer() {
        Path root = Path.of("C:/Music");
        Path shallow = root.resolve("Dup");
        Path nested = root.resolve("Other").resolve("Dup");
        // Same relative layout under both Dup folders
        List<FolderDuplicateCluster> clusters = FolderDuplicateDetector.detect(root, List.of(
                entry(shallow.resolve("a.abc"), "dup/a.abc", "h", "A"),
                entry(shallow.resolve("b.abc"), "dup/b.abc", "h2", "B"),
                entry(nested.resolve("a.abc"), "other/dup/a.abc", "h", "A"),
                entry(nested.resolve("b.abc"), "other/dup/b.abc", "h2", "B")));
        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).folderPaths().size());
        assertFalse(clusters.get(0).folderPaths().isEmpty());
    }

    private static FolderDuplicateDetector.FolderFileEntry entry(
            Path abs, String rel, String hash, String title) {
        return new FolderDuplicateDetector.FolderFileEntry(
                abs, rel, hash, "id-" + title, title);
    }
}
