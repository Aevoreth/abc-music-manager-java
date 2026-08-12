package com.aevoreth.abcmm.domain.scan;

import java.util.List;

/**
 * Outcome of applying a {@link DuplicateCleanupPlan}.
 */
public record CleanupApplyResult(
        int filesKept,
        int filesKeptSeparate,
        int filesIgnored,
        int filesTrashed,
        int foldersExcluded,
        int foldersRemovedFromLibrary,
        int foldersTrashed,
        List<String> errors,
        String message) {

    public CleanupApplyResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        message = message == null ? "" : message;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
