package com.aevoreth.abcmm.domain.library;

/**
 * Folder exclude rule from the {@code FolderRule} table (read-only for this milestone).
 */
public record FolderRuleInfo(long id, String path, boolean enabled, boolean includeInExport) {
}
