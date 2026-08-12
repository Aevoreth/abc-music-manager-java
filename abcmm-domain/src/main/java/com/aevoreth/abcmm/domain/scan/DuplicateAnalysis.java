package com.aevoreth.abcmm.domain.scan;

import java.util.List;

/**
 * Result of inventory + duplicate analysis. Safe known-path upserts may already have run;
 * peer groups have no preferred member.
 */
public record DuplicateAnalysis(
        List<DuplicateGroup> groups,
        List<FolderDuplicateCluster> folderClusters,
        int inventoriedFileCount,
        int songsUpdated,
        Long defaultStatusId) {

    public DuplicateAnalysis {
        groups = groups == null ? List.of() : List.copyOf(groups);
        folderClusters = folderClusters == null ? List.of() : List.copyOf(folderClusters);
    }

    public boolean hasDuplicates() {
        return !groups.isEmpty() || !folderClusters.isEmpty();
    }
}
