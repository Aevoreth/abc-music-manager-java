package com.aevoreth.abcmm.domain.scan;

/**
 * A new primary-library file that matches an existing song's logical identity
 * (normalized title + composers + part count).
 */
public record DuplicateCandidate(
        long existingSongId,
        String existingTitle,
        String newPath,
        String newTitle,
        String composers,
        int partCount) {

    public DuplicateCandidate {
        existingTitle = existingTitle == null ? "" : existingTitle;
        newPath = newPath == null ? "" : newPath;
        newTitle = newTitle == null ? "" : newTitle;
        composers = composers == null ? "" : composers;
    }
}
