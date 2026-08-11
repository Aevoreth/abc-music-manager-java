package com.aevoreth.abcmm.domain.library;

import java.util.List;

import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;

/**
 * Song fields for the Song Detail dialog (Python {@code get_song_for_detail}).
 */
public record SongDetailInfo(
        long id,
        String title,
        String composers,
        String transcriber,
        Integer durationSeconds,
        int partCount,
        List<AbcPartMetadata> parts,
        Integer rating,
        Long statusId,
        String statusName,
        String notes,
        String lyrics,
        String exportTimestamp) {

    public SongDetailInfo {
        title = title == null ? "" : title;
        composers = composers == null ? "" : composers;
        parts = parts == null ? List.of() : List.copyOf(parts);
        partCount = Math.max(0, partCount > 0 ? partCount : parts.size());
    }
}
