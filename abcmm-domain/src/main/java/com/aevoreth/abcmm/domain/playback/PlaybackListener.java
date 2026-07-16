package com.aevoreth.abcmm.domain.playback;

/**
 * Listener for {@link AbcPlaybackEngine} transport and load events.
 */
@FunctionalInterface
public interface PlaybackListener {

    void onPlaybackEvent(PlaybackEvent event);
}
