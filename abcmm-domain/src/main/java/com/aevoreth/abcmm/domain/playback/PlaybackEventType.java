package com.aevoreth.abcmm.domain.playback;

/**
 * High-level playback notifications for UI and session auto-advance.
 */
public enum PlaybackEventType {
    STATE_CHANGED,
    POSITION_CHANGED,
    TEMPO_CHANGED,
    SONG_LOADED,
    SONG_ENDED
}
