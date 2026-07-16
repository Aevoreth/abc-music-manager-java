package com.aevoreth.abcmm.domain.scan;

/**
 * Progress / summary counters for a library scan.
 */
public record ScanProgress(
        int filesScanned,
        int songsAdded,
        int songsUpdated,
        int songsRemoved,
        String message) {

    public ScanProgress {
        message = message == null ? "" : message;
    }
}
