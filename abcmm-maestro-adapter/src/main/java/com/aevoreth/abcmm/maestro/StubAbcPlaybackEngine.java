package com.aevoreth.abcmm.maestro;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PartInfo;
import com.aevoreth.abcmm.domain.playback.PlaybackEvent;
import com.aevoreth.abcmm.domain.playback.PlaybackEventType;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackListener;
import com.aevoreth.abcmm.domain.playback.PlaybackPosition;
import com.aevoreth.abcmm.domain.playback.PlaybackState;

/**
 * Bootstrap playback engine stub (no audio). Kept for unit tests.
 */
public final class StubAbcPlaybackEngine implements AbcPlaybackEngine {

    private PlaybackState state = PlaybackState.IDLE;
    private LoadedSong loadedSong;
    private double volume = 1.0;
    private float tempoFactor = 1.0f;
    private Duration position = Duration.ZERO;
    private final boolean[] muted = new boolean[64];
    private final boolean[] solo = new boolean[64];
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public LoadedSong load(Path abcFile) throws PlaybackException {
        Objects.requireNonNull(abcFile, "abcFile");
        if (!Files.isRegularFile(abcFile)) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("ABC file not found: " + abcFile);
        }
        String fileName = abcFile.getFileName() == null ? abcFile.toString() : abcFile.getFileName().toString();
        String title = stripExtension(fileName);
        loadedSong = new LoadedSong(
                title,
                "",
                Duration.ofSeconds(60),
                List.of(new PartInfo(0, "Part 1", "")));
        position = Duration.ZERO;
        state = PlaybackState.LOADED;
        fire(PlaybackEventType.SONG_LOADED);
        fire(PlaybackEventType.STATE_CHANGED);
        return loadedSong;
    }

    @Override
    public void play() throws PlaybackException {
        requireLoaded("play");
        state = PlaybackState.PLAYING;
        fire(PlaybackEventType.STATE_CHANGED);
    }

    @Override
    public void pause() throws PlaybackException {
        requireLoaded("pause");
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
            fire(PlaybackEventType.STATE_CHANGED);
        }
    }

    @Override
    public void stop() throws PlaybackException {
        requireLoaded("stop");
        position = Duration.ZERO;
        state = PlaybackState.STOPPED;
        fire(PlaybackEventType.STATE_CHANGED);
        fire(PlaybackEventType.POSITION_CHANGED);
    }

    @Override
    public void seek(Duration newPosition) throws PlaybackException {
        requireLoaded("seek");
        Objects.requireNonNull(newPosition, "position");
        if (newPosition.isNegative()) {
            throw new PlaybackException("Seek position must be non-negative");
        }
        position = newPosition;
        fire(PlaybackEventType.POSITION_CHANGED);
    }

    @Override
    public void setPartMuted(int partIndex, boolean mutedFlag) throws PlaybackException {
        requireLoaded("setPartMuted");
        validatePartIndex(partIndex);
        muted[partIndex] = mutedFlag;
    }

    @Override
    public void setPartSolo(int partIndex, boolean soloFlag) throws PlaybackException {
        requireLoaded("setPartSolo");
        validatePartIndex(partIndex);
        solo[partIndex] = soloFlag;
    }

    @Override
    public boolean isPartMuted(int partIndex) {
        if (loadedSong == null || partIndex < 0 || partIndex >= muted.length) {
            return false;
        }
        return muted[partIndex];
    }

    @Override
    public boolean isPartSolo(int partIndex) {
        if (loadedSong == null || partIndex < 0 || partIndex >= solo.length) {
            return false;
        }
        return solo[partIndex];
    }

    @Override
    public void setVolume(double newVolume) throws PlaybackException {
        if (newVolume < 0.0 || newVolume > 1.0) {
            throw new PlaybackException("Volume must be between 0.0 and 1.0");
        }
        this.volume = newVolume;
    }

    @Override
    public double getVolume() {
        return volume;
    }

    @Override
    public void setTempoFactor(float newTempoFactor) throws PlaybackException {
        if (newTempoFactor < 0.5f || newTempoFactor > 2.0f) {
            throw new PlaybackException("Tempo factor must be between 0.5 and 2.0");
        }
        this.tempoFactor = newTempoFactor;
        fire(PlaybackEventType.TEMPO_CHANGED);
    }

    @Override
    public float getTempoFactor() {
        return tempoFactor;
    }

    @Override
    public PlaybackState getState() {
        return state;
    }

    @Override
    public PlaybackPosition getPosition() {
        Duration duration = loadedSong == null ? Duration.ZERO : loadedSong.duration();
        return new PlaybackPosition(position, duration);
    }

    @Override
    public LoadedSong getLoadedSong() {
        return loadedSong;
    }

    @Override
    public void addPlaybackListener(PlaybackListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    /** Test helper: simulate natural end of song. */
    public void fireSongEnded() {
        state = PlaybackState.STOPPED;
        fire(PlaybackEventType.SONG_ENDED);
        fire(PlaybackEventType.STATE_CHANGED);
    }

    @Override
    public void close() {
        loadedSong = null;
        position = Duration.ZERO;
        state = PlaybackState.IDLE;
    }

    private void fire(PlaybackEventType type) {
        PlaybackEvent event = new PlaybackEvent(type, state);
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackEvent(event);
        }
    }

    private void requireLoaded(String action) throws PlaybackException {
        if (loadedSong == null) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("Cannot " + action + " before load()");
        }
    }

    private void validatePartIndex(int partIndex) throws PlaybackException {
        if (partIndex < 0 || partIndex >= loadedSong.partCount()) {
            throw new PlaybackException("Invalid part index: " + partIndex);
        }
    }

    private static String stripExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".abc")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
