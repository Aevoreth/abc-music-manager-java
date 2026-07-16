package com.aevoreth.abcmm.domain.playback;

import java.time.Duration;
import java.util.Objects;

/**
 * Current playback cursor within a loaded song.
 */
public final class PlaybackPosition {

    private final Duration position;
    private final Duration duration;

    public PlaybackPosition(Duration position, Duration duration) {
        this.position = Objects.requireNonNullElse(position, Duration.ZERO);
        this.duration = Objects.requireNonNullElse(duration, Duration.ZERO);
        if (this.position.isNegative() || this.duration.isNegative()) {
            throw new IllegalArgumentException("position and duration must be non-negative");
        }
    }

    public static PlaybackPosition zero() {
        return new PlaybackPosition(Duration.ZERO, Duration.ZERO);
    }

    public Duration position() {
        return position;
    }

    public Duration duration() {
        return duration;
    }
}
