package com.aevoreth.abcmm.domain.scan;

import java.util.List;

/**
 * Complete user-approved cleanup plan. Applied only after confirmation.
 */
public record DuplicateCleanupPlan(
        List<FileResolution> fileResolutions,
        List<FolderResolution> folderResolutions) {

    public DuplicateCleanupPlan {
        fileResolutions = fileResolutions == null ? List.of() : List.copyOf(fileResolutions);
        folderResolutions = folderResolutions == null ? List.of() : List.copyOf(folderResolutions);
    }

    public static DuplicateCleanupPlan empty() {
        return new DuplicateCleanupPlan(List.of(), List.of());
    }

    public boolean isEmpty() {
        return fileResolutions.isEmpty() && folderResolutions.isEmpty();
    }
}
