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
import java.util.stream.Collectors;

/**
 * Detects duplicated directory trees from a complete primary-library inventory.
 * Does not prefer any folder by alphabetical or scan order.
 */
public final class FolderDuplicateDetector {

    private FolderDuplicateDetector() {
    }

    /**
     * @param absolutePath absolute file path
     * @param relativeToLibrary path relative to library root, lower-case, {@code /}-separated
     * @param fileHash SHA-256 or null
     * @param logicalIdentityKey grouping key
     * @param title display title
     */
    public record FolderFileEntry(
            Path absolutePath,
            String relativeToLibrary,
            String fileHash,
            String logicalIdentityKey,
            String title) {
    }

    public static List<FolderDuplicateCluster> detect(
            Path libraryRoot,
            List<FolderFileEntry> primaryFiles) {
        if (libraryRoot == null || primaryFiles == null || primaryFiles.isEmpty()) {
            return List.of();
        }
        Path root = libraryRoot.toAbsolutePath().normalize();

        // Map each directory under root → entries with paths relative to that directory
        Map<Path, List<RelEntry>> byFolder = new LinkedHashMap<>();
        for (FolderFileEntry file : primaryFiles) {
            if (file.relativeToLibrary() == null || file.relativeToLibrary().isBlank()) {
                continue;
            }
            Path abs = file.absolutePath().toAbsolutePath().normalize();
            Path parent = abs.getParent();
            while (parent != null && parent.startsWith(root) && !parent.equals(root)) {
                String relToFolder;
                try {
                    relToFolder = parent.relativize(abs).toString().replace('\\', '/')
                            .toLowerCase(Locale.ROOT);
                } catch (RuntimeException ex) {
                    break;
                }
                byFolder.computeIfAbsent(parent, k -> new ArrayList<>())
                        .add(new RelEntry(relToFolder, file.fileHash(), file.logicalIdentityKey(), file.title()));
                parent = parent.getParent();
            }
        }

        Map<String, List<Path>> foldersBySignature = new LinkedHashMap<>();
        Map<Path, List<RelEntry>> folderEntries = new HashMap<>();
        for (Map.Entry<Path, List<RelEntry>> e : byFolder.entrySet()) {
            Path folder = e.getKey();
            List<RelEntry> entries = dedupeByRel(e.getValue());
            if (entries.size() < 2) {
                continue;
            }
            folderEntries.put(folder, entries);
            String signature = buildSignature(entries);
            foldersBySignature.computeIfAbsent(signature, k -> new ArrayList<>()).add(folder);
        }

        List<FolderDuplicateCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<Path>> e : foldersBySignature.entrySet()) {
            List<Path> folders = filterAntichain(e.getValue());
            if (folders.size() < 2) {
                continue;
            }
            folders = new ArrayList<>(folders);
            folders.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));

            Path sample = folders.get(0);
            List<RelEntry> sampleEntries = folderEntries.getOrDefault(sample, List.of());
            Counts counts = compareFolders(folders, folderEntries);
            List<String> titles = sampleEntries.stream()
                    .map(RelEntry::title)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(5)
                    .toList();
            clusters.add(new FolderDuplicateCluster(
                    "folder-" + shortId(e.getKey()),
                    root,
                    folders,
                    counts.identical,
                    counts.differing,
                    counts.unique,
                    titles));
        }
        clusters.sort(Comparator.comparing(FolderDuplicateCluster::clusterId));
        return clusters;
    }

    private static List<RelEntry> dedupeByRel(List<RelEntry> entries) {
        Map<String, RelEntry> map = new LinkedHashMap<>();
        for (RelEntry entry : entries) {
            map.putIfAbsent(entry.rel(), entry);
        }
        return new ArrayList<>(map.values());
    }

    private static String buildSignature(List<RelEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(RelEntry::rel))
                .map(en -> en.rel() + "|"
                        + (en.fileHash() != null && !en.fileHash().isBlank()
                                ? "h:" + en.fileHash()
                                : "i:" + nullToEmpty(en.logicalIdentityKey())))
                .collect(Collectors.joining("\n"));
    }

    private static Counts compareFolders(
            List<Path> folders,
            Map<Path, List<RelEntry>> folderEntries) {
        if (folders.size() < 2) {
            return new Counts(0, 0, 0);
        }
        Map<String, List<String>> hashesByRel = new HashMap<>();
        Map<String, Integer> presence = new HashMap<>();
        for (Path folder : folders) {
            Set<String> seenRel = new HashSet<>();
            for (RelEntry entry : folderEntries.getOrDefault(folder, List.of())) {
                String rel = entry.rel();
                seenRel.add(rel);
                hashesByRel.computeIfAbsent(rel, k -> new ArrayList<>())
                        .add(entry.fileHash() == null ? "" : entry.fileHash());
            }
            for (String rel : seenRel) {
                presence.merge(rel, 1, Integer::sum);
            }
        }
        int identical = 0;
        int differing = 0;
        int unique = 0;
        for (Map.Entry<String, Integer> e : presence.entrySet()) {
            if (e.getValue() < folders.size()) {
                unique++;
                continue;
            }
            List<String> hashes = hashesByRel.getOrDefault(e.getKey(), List.of());
            Set<String> distinct = new HashSet<>(hashes);
            if (distinct.size() <= 1) {
                identical++;
            } else {
                differing++;
            }
        }
        return new Counts(identical, differing, unique);
    }

    private static List<Path> filterAntichain(List<Path> paths) {
        List<Path> uniq = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path p : paths) {
            String key = p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                uniq.add(p);
            }
        }
        return uniq.stream()
                .filter(p -> uniq.stream().noneMatch(o -> !o.equals(p) && isStrictParent(o, p)))
                .toList();
    }

    private static boolean isStrictParent(Path ancestor, Path descendant) {
        Path a = ancestor.toAbsolutePath().normalize();
        Path d = descendant.toAbsolutePath().normalize();
        return !a.equals(d) && d.startsWith(a);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String shortId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()
                .substring(0, 8);
    }

    private record RelEntry(String rel, String fileHash, String logicalIdentityKey, String title) {
    }

    private record Counts(int identical, int differing, int unique) {
    }
}
