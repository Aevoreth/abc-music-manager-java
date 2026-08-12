package com.aevoreth.abcmm.domain.scan;

import java.util.List;
import java.util.Objects;

/**
 * A peer group of duplicate files. No canonical/preferred member is assigned automatically.
 */
public record DuplicateGroup(
        String groupId,
        DuplicateMatchType matchType,
        List<DuplicateFile> files) {

    public DuplicateGroup {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(matchType, "matchType");
        files = files == null ? List.of() : List.copyOf(files);
    }

    public String displayTitle() {
        for (DuplicateFile file : files) {
            if (file.metadata() != null && file.metadata().title() != null && !file.metadata().title().isBlank()) {
                return file.metadata().title();
            }
        }
        return groupId;
    }
}
