package com.aevoreth.abcmm.domain.scan;

import java.util.List;

/**
 * Parsed ABC song metadata (no note bodies), matching Python {@code ParsedSong}.
 */
public record AbcFileMetadata(
        String title,
        String composers,
        String transcriber,
        Integer durationSeconds,
        String exportTimestamp,
        List<AbcPartMetadata> parts) {

    public AbcFileMetadata {
        title = title == null || title.isBlank() ? "Unknown" : title;
        composers = composers == null || composers.isBlank() ? "Unknown" : composers;
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
