package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure inventory-first duplicate grouping. Scan / filesystem / alphabetical order
 * never assigns a preferred member.
 */
public final class DuplicateGrouping {

    private DuplicateGrouping() {
    }

    /**
     * Inventory peer used for grouping. Paths already known in the DB may carry a song id.
     */
    public record InventoryPeer(
            Path path,
            AbcFileMetadata metadata,
            String fileHash,
            String contentFingerprint,
            Long currentSongId,
            boolean currentlyIndexed,
            Path libraryRoot,
            boolean primaryLibrary) {

        public InventoryPeer {
            Objects.requireNonNull(path, "path");
            metadata = metadata == null
                    ? new AbcFileMetadata("Unknown", "Unknown", null, null, null, null)
                    : metadata;
        }

        public String logicalIdentityKey() {
            return DuplicateGrouping.logicalIdentityKey(metadata.title(), metadata.composers(),
                    metadata.parts() == null ? 0 : metadata.parts().size());
        }
    }

    /**
     * An existing SongFile association used to enrich metadata groups.
     */
    public record IndexedAssociation(
            long songId,
            Path path,
            String title,
            String composers,
            int partCount,
            Path libraryRoot) {

        public String logicalIdentityKey() {
            return DuplicateGrouping.logicalIdentityKey(title, composers, partCount);
        }
    }

    public static String logicalIdentityKey(String title, String composers, int partCount) {
        String normTitle = normalizeTitle(title);
        String comps = composers == null ? "" : composers.strip();
        return normTitle + "\0" + comps + "\0" + partCount;
    }

    public static List<DuplicateGroup> buildGroups(
            List<InventoryPeer> inventory,
            List<IndexedAssociation> indexedAssociations) {
        List<InventoryPeer> primary = inventory.stream()
                .filter(InventoryPeer::primaryLibrary)
                .toList();

        List<DuplicateGroup> groups = new ArrayList<>();
        Set<Path> claimed = new HashSet<>();

        Map<String, List<InventoryPeer>> byHash = new LinkedHashMap<>();
        for (InventoryPeer peer : primary) {
            if (peer.fileHash() == null || peer.fileHash().isBlank()) {
                continue;
            }
            byHash.computeIfAbsent(peer.fileHash(), k -> new ArrayList<>()).add(peer);
        }
        List<Map.Entry<String, List<InventoryPeer>>> hashEntries = new ArrayList<>(byHash.entrySet());
        hashEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, List<InventoryPeer>> entry : hashEntries) {
            List<InventoryPeer> peers = sortPeersStable(entry.getValue());
            if (peers.size() < 2) {
                continue;
            }
            groups.add(new DuplicateGroup(
                    "exact-" + shortId(entry.getKey()),
                    DuplicateMatchType.EXACT_FILE,
                    toDuplicateFiles(peers)));
            peers.forEach(p -> claimed.add(p.path()));
        }

        Map<String, List<InventoryPeer>> byIdentity = new LinkedHashMap<>();
        for (InventoryPeer peer : primary) {
            if (claimed.contains(peer.path())) {
                continue;
            }
            byIdentity.computeIfAbsent(peer.logicalIdentityKey(), k -> new ArrayList<>()).add(peer);
        }

        Map<String, List<IndexedAssociation>> indexedByIdentity = new HashMap<>();
        if (indexedAssociations != null) {
            for (IndexedAssociation assoc : indexedAssociations) {
                indexedByIdentity.computeIfAbsent(assoc.logicalIdentityKey(), k -> new ArrayList<>()).add(assoc);
            }
        }

        List<String> identityKeys = new ArrayList<>(byIdentity.keySet());
        // Also consider identities that only appear via indexed associations colliding with inventory
        // (already covered via byIdentity keys from inventory peers).
        identityKeys.sort(Comparator.naturalOrder());
        for (String key : identityKeys) {
            List<InventoryPeer> peers = sortPeersStable(byIdentity.get(key));
            List<IndexedAssociation> assocs = indexedByIdentity.getOrDefault(key, List.of());

            Map<Path, DuplicateFile> filesByPath = new LinkedHashMap<>();
            for (InventoryPeer peer : peers) {
                filesByPath.put(peer.path(), toDuplicateFile(peer));
            }
            for (IndexedAssociation assoc : assocs) {
                if (assoc.path() == null) {
                    continue;
                }
                if (claimed.contains(assoc.path())) {
                    continue;
                }
                DuplicateFile existing = filesByPath.get(assoc.path());
                if (existing != null) {
                    if (existing.currentSongId() == null) {
                        filesByPath.put(assoc.path(), new DuplicateFile(
                                existing.path(),
                                existing.metadata(),
                                existing.fileHash(),
                                existing.contentFingerprint(),
                                assoc.songId(),
                                true,
                                existing.libraryRoot()));
                    }
                    continue;
                }
                // Indexed path not present in this inventory identity bucket (e.g. missing/moved file).
                filesByPath.put(assoc.path(), new DuplicateFile(
                        assoc.path(),
                        new AbcFileMetadata(assoc.title(), assoc.composers(), null, null, null, null),
                        null,
                        null,
                        assoc.songId(),
                        true,
                        assoc.libraryRoot()));
            }

            List<DuplicateFile> files = new ArrayList<>(filesByPath.values());
            files.sort(Comparator.comparing(f -> f.path().toString(), String.CASE_INSENSITIVE_ORDER));
            if (files.size() < 2) {
                continue;
            }
            groups.add(new DuplicateGroup(
                    "meta-" + shortId(key),
                    DuplicateMatchType.STRONG_METADATA_MATCH,
                    files));
            peers.forEach(p -> claimed.add(p.path()));
        }

        return groups;
    }

    private static List<InventoryPeer> sortPeersStable(List<InventoryPeer> peers) {
        List<InventoryPeer> sorted = new ArrayList<>(peers);
        sorted.sort(Comparator.comparing(p -> p.path().toString(), String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static List<DuplicateFile> toDuplicateFiles(List<InventoryPeer> peers) {
        List<DuplicateFile> files = new ArrayList<>(peers.size());
        for (InventoryPeer peer : peers) {
            files.add(toDuplicateFile(peer));
        }
        return files;
    }

    private static DuplicateFile toDuplicateFile(InventoryPeer peer) {
        return new DuplicateFile(
                peer.path(),
                peer.metadata(),
                peer.fileHash(),
                peer.contentFingerprint(),
                peer.currentSongId(),
                peer.currentlyIndexed(),
                peer.libraryRoot());
    }

    private static String normalizeTitle(String title) {
        return (title == null ? "" : title).strip().toLowerCase(Locale.ROOT);
    }

    private static String shortId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()
                .substring(0, 8);
    }
}
