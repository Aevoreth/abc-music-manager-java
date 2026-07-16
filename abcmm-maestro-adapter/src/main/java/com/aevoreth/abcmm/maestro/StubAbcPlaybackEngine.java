package com.aevoreth.abcmm.maestro;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PartInfo;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackPosition;
import com.aevoreth.abcmm.domain.playback.PlaybackState;

/**
 * Bootstrap playback engine stub.
 *
 * <p><strong>Not</strong> a completed Maestro integration: this class does not
 * synthesize audio or claim working play/pause/seek. It exists so the adapter
 * module and application shell compile and so domain contracts can be tested
 * while Maestro source compilation / wiring is finished.
 *
 * <p>When real integration lands, replace usage via {@link MaestroPlaybackEngines}
 * with an implementation that wraps Maestro sequencers and never returns
 * {@code com.digero.*} types.
 */
public final class StubAbcPlaybackEngine implements AbcPlaybackEngine {

    private PlaybackState state = PlaybackState.IDLE;
    private LoadedSong loadedSong;
    private double volume = 1.0;
    private Duration position = Duration.ZERO;

    @Override
    public LoadedSong load(Path abcFile) throws PlaybackException {
        Objects.requireNonNull(abcFile, "abcFile");
        if (!Files.isRegularFile(abcFile)) {
            state = PlaybackState.ERROR;
            throw new PlaybackException("ABC file not found: " + abcFile);
        }
        String fileName = abcFile.getFileName() == null ? abcFile.toString() : abcFile.getFileName().toString();
        String title = stripExtension(fileName);
        loadedSong = new LoadedSong(
                title,
                "",
                Duration.ZERO,
                List.of(new PartInfo(0, "Part 1", "")));
        position = Duration.ZERO;
        state = PlaybackState.LOADED;
        return loadedSong;
    }

    @Override
    public void play() throws PlaybackException {
        requireLoaded("play");
        state = PlaybackState.PLAYING;
    }

    @Override
    public void pause() throws PlaybackException {
        requireLoaded("pause");
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
        }
    }

    @Override
    public void stop() throws PlaybackException {
        requireLoaded("stop");
        position = Duration.ZERO;
        state = PlaybackState.STOPPED;
    }

    @Override
    public void seek(Duration newPosition) throws PlaybackException {
        requireLoaded("seek");
        Objects.requireNonNull(newPosition, "position");
        if (newPosition.isNegative()) {
            throw new PlaybackException("Seek position must be non-negative");
        }
        position = newPosition;
    }

    @Override
    public void setPartMuted(int partIndex, boolean muted) throws PlaybackException {
        requireLoaded("setPartMuted");
        validatePartIndex(partIndex);
    }

    @Override
    public void setPartSolo(int partIndex, boolean solo) throws PlaybackException {
        requireLoaded("setPartSolo");
        validatePartIndex(partIndex);
    }

    @Override
    public void setVolume(double newVolume) throws PlaybackException {
        if (newVolume < 0.0 || newVolume > 1.0) {
            throw new PlaybackException("Volume must be between 0.0 and 1.0");
        }
        this.volume = newVolume;
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

    public double volume() {
        return volume;
    }

    @Override
    public void close() {
        loadedSong = null;
        position = Duration.ZERO;
        state = PlaybackState.IDLE;
    }

    private void requireLoaded(String action) throws PlaybackException {
        if (loadedSong == null) {
            state = PlaybackState.ERROR;
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
