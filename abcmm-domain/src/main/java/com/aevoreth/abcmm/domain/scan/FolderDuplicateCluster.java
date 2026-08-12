package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Two or more directory trees that appear to be duplicates of each other.
 * No folder is preferred by alphabetical or scan order.
 */
public record FolderDuplicateCluster(
        String clusterId,
        Path libraryRoot,
        List<Path> folderPaths,
        int identicalFileCount,
        int differingFileCount,
        int uniqueFileCount,
        List<String> sampleTitles) {

    public FolderDuplicateCluster {
        Objects.requireNonNull(clusterId, "clusterId");
        Objects.requireNonNull(libraryRoot, "libraryRoot");
        folderPaths = folderPaths == null ? List.of() : List.copyOf(folderPaths);
        sampleTitles = sampleTitles == null ? List.of() : List.copyOf(sampleTitles);
    }
}
