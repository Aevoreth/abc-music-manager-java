package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file disposition in a {@link DuplicateCleanupPlan}.
 *
 * @param bindSongId when disposition is {@link FileDisposition#KEEP}, optionally bind/remap this Song id
 */
public record FileResolution(
        String groupId,
        Path path,
        FileDisposition disposition,
        Long bindSongId) {

    public FileResolution {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(disposition, "disposition");
    }

    public FileResolution(String groupId, Path path, FileDisposition disposition) {
        this(groupId, path, disposition, null);
    }
}
