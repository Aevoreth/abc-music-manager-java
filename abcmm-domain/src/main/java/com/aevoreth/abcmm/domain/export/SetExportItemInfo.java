package com.aevoreth.abcmm.domain.export;

/**
 * Setlist item joined with song metadata needed for CSV / rename during export.
 */
public record SetExportItemInfo(
        long itemId,
        long setlistId,
        long songId,
        String title,
        String composers,
        String transcriber,
        Integer durationSeconds,
        int partCount,
        String partsJson,
        String notes,
        String statusName,
        int position) {
}
