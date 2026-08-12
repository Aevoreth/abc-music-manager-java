package com.aevoreth.abcmm.domain.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class DuplicateGroupingTest {

    @Test
    void scanOrderDoesNotCreatePreferredMember() {
        Path aabc = Path.of("C:/Music/aabc.abc");
        Path aabcc = Path.of("C:/Music/aabcc.abc");
        Path abc = Path.of("C:/Music/abc.abc");
        String hash = "abc123";
        AbcFileMetadata meta = new AbcFileMetadata("ABC", "Ada", null, 60, null, List.of(
                new AbcPartMetadata(1, "P1", 1L, "Lute", null)));

        // Intentionally inventory in reverse alphabetical / "first scanned" order
        List<DuplicateGrouping.InventoryPeer> inventory = List.of(
                peer(aabc, meta, hash),
                peer(aabcc, meta, hash),
                peer(abc, meta, hash));

        List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(inventory, List.of());
        assertEquals(1, groups.size());
        DuplicateGroup group = groups.get(0);
        assertEquals(DuplicateMatchType.EXACT_FILE, group.matchType());
        assertEquals(3, group.files().size());
        assertTrue(group.files().stream().map(DuplicateFile::path).toList()
                .containsAll(List.of(aabc, aabcc, abc)));
        assertTrue(group.files().stream().noneMatch(DuplicateFile::currentlyIndexed));
    }

    @Test
    void aabcAabccAbcBecomeOnePeerGroup() {
        AbcFileMetadata meta = new AbcFileMetadata("ABC", "Ada", null, 60, null, List.of(
                new AbcPartMetadata(1, "P1", 1L, "Lute", null)));
        String hash = "samehash";
        List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(List.of(
                peer(Path.of("C:/Music/aabc.abc"), meta, hash),
                peer(Path.of("C:/Music/aabcc.abc"), meta, hash),
                peer(Path.of("C:/Music/abc.abc"), meta, hash)), List.of());
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).files().size());
    }

    @Test
    void exactHashAcrossDirectories() {
        AbcFileMetadata meta = new AbcFileMetadata("Tune", "Bea", null, null, null, List.of(
                new AbcPartMetadata(1, "P1", 1L, "Lute", null)));
        String hash = "deadbeef";
        List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(List.of(
                peer(Path.of("C:/Music/Main/tune.abc"), meta, hash),
                peer(Path.of("C:/Music/Old/tune.abc"), meta, hash)), List.of());
        assertEquals(1, groups.size());
        assertEquals(DuplicateMatchType.EXACT_FILE, groups.get(0).matchType());
        assertEquals(2, groups.get(0).files().size());
    }

    @Test
    void strongMetadataMatchWhenHashesDiffer() {
        AbcFileMetadata meta = new AbcFileMetadata("Same", "Ada", null, null, null, List.of(
                new AbcPartMetadata(1, "P1", 1L, "Lute", null),
                new AbcPartMetadata(2, "P2", 1L, "Lute", null)));
        List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(List.of(
                peer(Path.of("C:/Music/one.abc"), meta, "hash1"),
                peer(Path.of("C:/Music/two.abc"), meta, "hash2")), List.of());
        assertEquals(1, groups.size());
        assertEquals(DuplicateMatchType.STRONG_METADATA_MATCH, groups.get(0).matchType());
    }

    private static DuplicateGrouping.InventoryPeer peer(Path path, AbcFileMetadata meta, String hash) {
        return new DuplicateGrouping.InventoryPeer(
                path, meta, hash, null, null, false, Path.of("C:/Music"), true);
    }
}
