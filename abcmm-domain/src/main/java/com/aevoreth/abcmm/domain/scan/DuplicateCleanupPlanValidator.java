package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a cleanup plan against an analysis before filesystem/DB changes.
 */
public final class DuplicateCleanupPlanValidator {

    private DuplicateCleanupPlanValidator() {
    }

    /**
     * Full validation: every duplicate group must be fully resolved in the plan.
     * Folder clusters may be omitted (left for file-level review).
     */
    public static List<String> validate(DuplicateAnalysis analysis, DuplicateCleanupPlan plan) {
        List<String> errors = validatePartial(analysis, plan);
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(plan, "plan");

        Set<String> groupsInPlan = plan.fileResolutions().stream()
                .map(FileResolution::groupId)
                .collect(Collectors.toSet());
        for (DuplicateGroup group : analysis.groups()) {
            if (!groupsInPlan.contains(group.groupId())) {
                errors.add("Unresolved duplicate group: " + group.groupId());
            }
        }
        return errors;
    }

    /**
     * Partial validation for "Apply rules and rescan": only groups/clusters included in the
     * plan must be complete and internally consistent. Omitted items remain for a later pass.
     */
    public static List<String> validatePartial(DuplicateAnalysis analysis, DuplicateCleanupPlan plan) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(plan, "plan");
        List<String> errors = new ArrayList<>();

        Map<String, DuplicateGroup> groupsById = analysis.groups().stream()
                .collect(Collectors.toMap(DuplicateGroup::groupId, g -> g, (a, b) -> a));
        Map<String, FolderDuplicateCluster> clustersById = analysis.folderClusters().stream()
                .collect(Collectors.toMap(FolderDuplicateCluster::clusterId, c -> c, (a, b) -> a));

        Map<String, Set<Path>> resolvedPathsByGroup = new HashMap<>();
        for (FileResolution resolution : plan.fileResolutions()) {
            DuplicateGroup group = groupsById.get(resolution.groupId());
            if (group == null) {
                errors.add("Unknown duplicate group: " + resolution.groupId());
                continue;
            }
            boolean pathInGroup = group.files().stream()
                    .anyMatch(f -> f.path().equals(resolution.path()));
            if (!pathInGroup) {
                errors.add("Path not in group " + resolution.groupId() + ": " + resolution.path());
            }
            Set<Path> paths = resolvedPathsByGroup.computeIfAbsent(resolution.groupId(), k -> new HashSet<>());
            if (!paths.add(resolution.path())) {
                errors.add("Duplicate resolution for path in group " + resolution.groupId() + ": " + resolution.path());
            }
            if (resolution.disposition() == FileDisposition.KEEP
                    && resolution.bindSongId() != null) {
                boolean songInGroup = group.files().stream()
                        .anyMatch(f -> resolution.bindSongId().equals(f.currentSongId()));
                if (!songInGroup) {
                    errors.add("bindSongId " + resolution.bindSongId()
                            + " is not associated with group " + resolution.groupId());
                }
            }
        }

        for (Map.Entry<String, Set<Path>> entry : resolvedPathsByGroup.entrySet()) {
            DuplicateGroup group = groupsById.get(entry.getKey());
            if (group == null) {
                continue;
            }
            Set<Path> resolved = entry.getValue();
            if (resolved.size() != group.files().size()) {
                Set<Path> all = group.files().stream().map(DuplicateFile::path).collect(Collectors.toSet());
                all.removeAll(resolved);
                errors.add("Incomplete group " + group.groupId() + " in partial plan; missing: " + all);
            }

            List<Long> songIds = group.files().stream()
                    .map(DuplicateFile::currentSongId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (songIds.size() > 1) {
                Map<Long, Long> keepBinds = new HashMap<>();
                for (FileResolution resolution : plan.fileResolutions()) {
                    if (!resolution.groupId().equals(group.groupId())) {
                        continue;
                    }
                    if (resolution.disposition() == FileDisposition.KEEP) {
                        Long bind = resolution.bindSongId();
                        if (bind == null) {
                            errors.add("Group " + group.groupId()
                                    + " has multiple Song IDs; KEEP requires explicit bindSongId for "
                                    + resolution.path());
                        } else {
                            Long previous = keepBinds.put(bind, bind);
                            if (previous != null) {
                                errors.add("Song " + bind + " bound to multiple KEEP paths in group "
                                        + group.groupId());
                            }
                        }
                    }
                }
            }
        }

        Map<String, Set<Path>> resolvedFoldersByCluster = new HashMap<>();
        for (FolderResolution resolution : plan.folderResolutions()) {
            FolderDuplicateCluster cluster = clustersById.get(resolution.clusterId());
            if (cluster == null) {
                errors.add("Unknown folder cluster: " + resolution.clusterId());
                continue;
            }
            boolean pathInCluster = cluster.folderPaths().stream()
                    .anyMatch(p -> p.equals(resolution.folderPath()));
            if (!pathInCluster) {
                errors.add("Folder not in cluster " + resolution.clusterId() + ": " + resolution.folderPath());
            }
            Set<Path> paths = resolvedFoldersByCluster.computeIfAbsent(
                    resolution.clusterId(), k -> new HashSet<>());
            if (!paths.add(resolution.folderPath())) {
                errors.add("Duplicate folder resolution: " + resolution.folderPath());
            }
        }

        for (Map.Entry<String, Set<Path>> entry : resolvedFoldersByCluster.entrySet()) {
            FolderDuplicateCluster cluster = clustersById.get(entry.getKey());
            if (cluster == null) {
                continue;
            }
            if (entry.getValue().size() != cluster.folderPaths().size()) {
                errors.add("Incomplete folder cluster " + cluster.clusterId()
                        + " in partial plan; resolve every folder or omit the cluster");
            }
            long decided = plan.folderResolutions().stream()
                    .filter(r -> r.clusterId().equals(cluster.clusterId()))
                    .filter(r -> r.disposition() != FolderDisposition.REVIEW_INDIVIDUALLY)
                    .count();
            long keepCount = plan.folderResolutions().stream()
                    .filter(r -> r.clusterId().equals(cluster.clusterId()))
                    .filter(r -> r.disposition() == FolderDisposition.KEEP_AND_SCAN)
                    .count();
            if (decided > 0 && keepCount == 0) {
                errors.add("Folder cluster " + cluster.clusterId()
                        + " has bulk actions but no KEEP_AND_SCAN folder");
            }
        }

        if (plan.isEmpty()) {
            errors.add("Partial plan is empty — choose at least one folder or file disposition first");
        }

        return errors;
    }
}
