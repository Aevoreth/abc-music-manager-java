package com.aevoreth.abcmm.domain.library;

/**
 * Library status label from the {@code Status} table.
 */
public record StatusInfo(long id, String name, String color, int sortOrder) {
}
