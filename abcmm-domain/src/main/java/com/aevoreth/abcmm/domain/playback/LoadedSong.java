package com.aevoreth.abcmm.domain.playback;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Song metadata loaded from an ABC file for display and playback control.
 */
public final class LoadedSong {

    private final String title;
    private final String composer;
    private final Duration duration;
    private final List<PartInfo> parts;

    public LoadedSong(String title, String composer, Duration duration, List<PartInfo> parts) {
        this.title = title == null ? "" : title;
        this.composer = composer == null ? "" : composer;
        this.duration = duration == null ? Duration.ZERO : duration;
        this.parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
    }

    public String title() {
        return title;
    }

    public String composer() {
        return composer;
    }

    public Duration duration() {
        return duration;
    }

    public List<PartInfo> parts() {
        return parts;
    }

    public int partCount() {
        return parts.size();
    }
}
