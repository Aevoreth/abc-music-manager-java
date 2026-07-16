package com.aevoreth.abcmm.domain.playback;

/**
 * Notified when the play queue or current index changes.
 */
@FunctionalInterface
public interface PlaybackSessionListener {

    void onSessionChanged();
}
