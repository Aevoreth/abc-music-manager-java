package com.aevoreth.abcmm.domain.library;

/**
 * Songbook account target from the {@code AccountTarget} table.
 */
public record AccountTargetInfo(long id, String accountName, String pluginDataPath, boolean enabled) {
}
