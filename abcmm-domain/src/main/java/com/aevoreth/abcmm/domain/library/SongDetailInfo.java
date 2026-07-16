package com.aevoreth.abcmm.domain.library;

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
        Integer rating,
        Long statusId,
        String statusName,
        String notes,
        String lyrics,
        String exportTimestamp) {

    public SongDetailInfo {
        title = title == null ? "" : title;
        composers = composers == null ? "" : composers;
        partCount = Math.max(0, partCount);
    }
}
