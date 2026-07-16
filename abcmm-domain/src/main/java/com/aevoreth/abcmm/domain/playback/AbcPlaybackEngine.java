package com.aevoreth.abcmm.domain.playback;

import java.nio.file.Path;
import java.time.Duration;

/**
 * ABC Music Manager-owned playback engine boundary.
 * Implementations live in {@code abcmm-maestro-adapter} and must not expose Maestro types.
 */
public interface AbcPlaybackEngine extends AutoCloseable {

    LoadedSong load(Path abcFile) throws PlaybackException;

    void play() throws PlaybackException;

    void pause() throws PlaybackException;

    void stop() throws PlaybackException;

    void seek(Duration position) throws PlaybackException;

    void setPartMuted(int partIndex, boolean muted) throws PlaybackException;

    void setPartSolo(int partIndex, boolean solo) throws PlaybackException;

    boolean isPartMuted(int partIndex);

    boolean isPartSolo(int partIndex);

    void setVolume(double volume) throws PlaybackException;

    double getVolume();

    void setTempoFactor(float tempoFactor) throws PlaybackException;

    float getTempoFactor();

    PlaybackState getState();

    PlaybackPosition getPosition();

    LoadedSong getLoadedSong();

    void addPlaybackListener(PlaybackListener listener);

    void removePlaybackListener(PlaybackListener listener);

    /**
     * Releases synthesizer / sequencer resources. Safe to call more than once.
     */
    @Override
    void close();
}
