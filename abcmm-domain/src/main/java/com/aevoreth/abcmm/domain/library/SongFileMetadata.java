package com.aevoreth.abcmm.domain.library;

/**
 * Song metadata keyed by {@code SongFile.file_path} for PluginData export
 * (Python {@code get_song_metadata_for_file_path}).
 */
public record SongFileMetadata(
        String title,
        String composers,
        String transcriber,
        Integer durationSeconds,
        String partsJson) {
}
