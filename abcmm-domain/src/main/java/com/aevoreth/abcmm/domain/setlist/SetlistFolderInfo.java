package com.aevoreth.abcmm.domain.setlist;

/**
 * Setlist folder (category) from the {@code SetlistFolder} table.
 */
public record SetlistFolderInfo(long id, String name, int sortOrder) {
}
