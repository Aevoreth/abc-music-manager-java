package com.aevoreth.abcmm.domain.export;

import java.util.List;
import java.util.Objects;

/**
 * One song row for {@code SongbookData.plugindata} (Python {@code plugindata_writer} shape).
 */
public record PluginDataSongEntry(
        String filepath,
        String filename,
        List<Track> tracks,
        String transcriber,
        String artist) {

    public PluginDataSongEntry {
        Objects.requireNonNull(filepath, "filepath");
        Objects.requireNonNull(filename, "filename");
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        transcriber = transcriber == null ? "" : transcriber;
        artist = artist == null || artist.isBlank() ? "Unknown" : artist;
    }

    /**
     * Track id/name pair written under {@code Tracks}.
     */
    public record Track(String id, String name) {
        public Track {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
        }
    }
}
