package com.aevoreth.abcmm.domain.playback;

/**
 * High-level playback transport state for the ABC Music Manager UI.
 */
public enum PlaybackState {
    IDLE,
    LOADED,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR
}
