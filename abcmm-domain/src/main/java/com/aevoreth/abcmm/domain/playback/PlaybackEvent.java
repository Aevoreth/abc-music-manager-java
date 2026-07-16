package com.aevoreth.abcmm.domain.playback;

import java.util.Objects;

/**
 * Immutable notification from {@link AbcPlaybackEngine}.
 */
public final class PlaybackEvent {

    private final PlaybackEventType type;
    private final PlaybackState state;

    public PlaybackEvent(PlaybackEventType type, PlaybackState state) {
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
    }

    public PlaybackEventType type() {
        return type;
    }

    public PlaybackState state() {
        return state;
    }
}
