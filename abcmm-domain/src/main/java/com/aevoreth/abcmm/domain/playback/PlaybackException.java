package com.aevoreth.abcmm.domain.playback;

/**
 * Failure while loading or controlling ABC playback.
 */
public class PlaybackException extends Exception {

    public PlaybackException(String message) {
        super(message);
    }

    public PlaybackException(String message, Throwable cause) {
        super(message, cause);
    }
}
