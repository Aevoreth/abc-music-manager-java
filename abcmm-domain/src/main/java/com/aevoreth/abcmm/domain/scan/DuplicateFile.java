package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One peer file in a {@link DuplicateGroup}. No member is preferred by scan order.
 */
public record DuplicateFile(
        Path path,
        AbcFileMetadata metadata,
        String fileHash,
        String contentFingerprint,
        Long currentSongId,
        boolean currentlyIndexed,
        Path libraryRoot) {

    public DuplicateFile {
        Objects.requireNonNull(path, "path");
        metadata = metadata == null
                ? new AbcFileMetadata("Unknown", "Unknown", null, null, null, null)
                : metadata;
        libraryRoot = libraryRoot == null ? path.getRoot() : libraryRoot;
    }
}
