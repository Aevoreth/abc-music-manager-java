package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One folder disposition in a {@link DuplicateCleanupPlan}.
 */
public record FolderResolution(
        String clusterId,
        Path folderPath,
        FolderDisposition disposition) {

    public FolderResolution {
        Objects.requireNonNull(clusterId, "clusterId");
        Objects.requireNonNull(folderPath, "folderPath");
        Objects.requireNonNull(disposition, "disposition");
    }
}
