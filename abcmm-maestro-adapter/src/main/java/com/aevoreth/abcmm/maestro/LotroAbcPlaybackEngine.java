package com.aevoreth.abcmm.maestro;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PartInfo;
import com.aevoreth.abcmm.domain.playback.PlaybackEvent;
import com.aevoreth.abcmm.domain.playback.PlaybackEventType;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackListener;
import com.aevoreth.abcmm.domain.playback.PlaybackPosition;
import com.aevoreth.abcmm.domain.playback.PlaybackState;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SynthesizerFactory;
import com.digero.common.midi.VolumeTransceiver;
import com.digero.common.util.FileParseException;
import com.digero.common.util.SoundFontDownloader;

/**
 * Maestro-backed playback using {@link AbcToMidi} and {@link LotroSequencerWrapper}.
 */
public final class LotroAbcPlaybackEngine implements AbcPlaybackEngine {

    private static final Logger LOG = Logger.getLogger(LotroAbcPlaybackEngine.class.getName());

    private final LotroSequencerWrapper sequencer;
    private final VolumeTransceiver volumeTransceiver;
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    private PlaybackState state = PlaybackState.IDLE;
    private LoadedSong loadedSong;
    private AbcInfo abcInfo;
    private double volume = 1.0;
    private boolean closed;

    public LotroAbcPlaybackEngine() throws MidiUnavailableException {
        // Mirror AbcPlayer: resolve shared SF2, then point SynthesizerFactory at it
        // before LotroSequencerWrapper's static synth init runs.
        File sf2 = SoundFontDownloader.ensureSoundFontExists();
        if (sf2 != null) {
            SynthesizerFactory.setSoundbank(sf2);
            LOG.info("Using LOTRO soundfont: " + sf2.getAbsolutePath());
        } else {
            LOG.warning("No shared LOTRO soundfont; synthesizer may fall back to jar/app folder lookup");
        }

        sequencer = new LotroSequencerWrapper();
        sequencer.createReceiver();
        sequencer.setUseSequenceTempoFactor(true);

        volumeTransceiver = new VolumeTransceiver();
        volumeTransceiver.setVolume(MidiConstants.MAX_VOLUME);
        sequencer.addTransceiver(volumeTransceiver);

        sequencer.addChangeListener(evt -> {
            SequencerProperty property = evt.getProperty();
            if (property == SequencerProperty.SONG_ENDED) {
                state = PlaybackState.STOPPED;
                fire(PlaybackEventType.SONG_ENDED);
                fire(PlaybackEventType.STATE_CHANGED);
            } else if (property == SequencerProperty.IS_RUNNING) {
                if (sequencer.isRunning()) {
                    state = PlaybackState.PLAYING;
                } else if (state == PlaybackState.PLAYING) {
                    state = PlaybackState.PAUSED;
                }
                fire(PlaybackEventType.STATE_CHANGED);
            } else if (property == SequencerProperty.POSITION
                    || property == SequencerProperty.LENGTH
                    || property == SequencerProperty.DRAG_POSITION) {
                fire(PlaybackEventType.POSITION_CHANGED);
            } else if (property == SequencerProperty.TEMPO) {
                fire(PlaybackEventType.TEMPO_CHANGED);
            }
        });
    }

    @Override
    public LoadedSong load(Path abcFile) throws PlaybackException {
        ensureOpen();
        Objects.requireNonNull(abcFile, "abcFile");
        if (!Files.isRegularFile(abcFile)) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("ABC file not found: " + abcFile);
        }

        sequencer.stop();

        File file = abcFile.toFile();
        AbcInfo info = new AbcInfo();
        Sequence song;
        try {
            AbcToMidi.Params params = new AbcToMidi.Params(file);
            params.useLotroInstruments = true;
            params.abcInfo = info;
            params.enableLotroErrors = false;
            params.stereo = 100;
            params.generateRegions = false;
            song = AbcToMidi.convert(params);
        } catch (IOException | FileParseException ex) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("Failed to convert ABC to MIDI: " + ex.getMessage(), ex);
        }

        try {
            sequencer.setSequence(song);
            sequencer.setCurrentTrackInfos(info.abcTrackInfos);
        } catch (InvalidMidiDataException ex) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("Invalid MIDI data: " + ex.getMessage(), ex);
        }

        sequencer.setTempoFactor(1.0f);
        sequencer.clearAllSoloMute();
        sequencer.setPosition(0L);

        this.abcInfo = info;
        this.loadedSong = toLoadedSong(info, song);
        this.state = PlaybackState.LOADED;
        fire(PlaybackEventType.SONG_LOADED);
        fire(PlaybackEventType.STATE_CHANGED);
        fire(PlaybackEventType.POSITION_CHANGED);
        return loadedSong;
    }

    @Override
    public void play() throws PlaybackException {
        ensureOpen();
        requireLoaded("play");
        sequencer.start();
        state = PlaybackState.PLAYING;
        fire(PlaybackEventType.STATE_CHANGED);
    }

    @Override
    public void pause() throws PlaybackException {
        ensureOpen();
        requireLoaded("pause");
        if (sequencer.isRunning()) {
            sequencer.stop();
            state = PlaybackState.PAUSED;
            fire(PlaybackEventType.STATE_CHANGED);
        }
    }

    @Override
    public void stop() throws PlaybackException {
        ensureOpen();
        requireLoaded("stop");
        sequencer.stop();
        sequencer.setPosition(0L);
        state = PlaybackState.STOPPED;
        fire(PlaybackEventType.STATE_CHANGED);
        fire(PlaybackEventType.POSITION_CHANGED);
    }

    @Override
    public void seek(Duration position) throws PlaybackException {
        ensureOpen();
        requireLoaded("seek");
        Objects.requireNonNull(position, "position");
        if (position.isNegative()) {
            throw new PlaybackException("Seek position must be non-negative");
        }
        long micros = position.toNanos() / 1_000L;
        long length = sequencer.getLength();
        if (length > 0) {
            micros = Math.min(micros, length);
        }
        sequencer.setPosition(micros);
        fire(PlaybackEventType.POSITION_CHANGED);
    }

    @Override
    public void setPartMuted(int partIndex, boolean muted) throws PlaybackException {
        ensureOpen();
        requireLoaded("setPartMuted");
        validateTrackIndex(partIndex);
        sequencer.setTrackMute(partIndex, muted);
    }

    @Override
    public void setPartSolo(int partIndex, boolean solo) throws PlaybackException {
        ensureOpen();
        requireLoaded("setPartSolo");
        validateTrackIndex(partIndex);
        sequencer.setTrackSolo(partIndex, solo);
    }

    @Override
    public boolean isPartMuted(int partIndex) {
        if (loadedSong == null || sequencer.getSequence() == null) {
            return false;
        }
        try {
            return sequencer.getTrackMute(partIndex);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public boolean isPartSolo(int partIndex) {
        if (loadedSong == null || sequencer.getSequence() == null) {
            return false;
        }
        try {
            return sequencer.getTrackSolo(partIndex);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void setVolume(double newVolume) throws PlaybackException {
        if (newVolume < 0.0 || newVolume > 1.0) {
            throw new PlaybackException("Volume must be between 0.0 and 1.0");
        }
        this.volume = newVolume;
        int midiVolume = (int) Math.round(newVolume * MidiConstants.MAX_VOLUME);
        volumeTransceiver.setVolume(Math.max(0, Math.min(MidiConstants.MAX_VOLUME, midiVolume)));
    }

    @Override
    public double getVolume() {
        return volume;
    }

    @Override
    public void setTempoFactor(float tempoFactor) throws PlaybackException {
        ensureOpen();
        if (tempoFactor < 0.5f || tempoFactor > 2.0f) {
            throw new PlaybackException("Tempo factor must be between 0.5 and 2.0");
        }
        sequencer.setTempoFactor(tempoFactor);
        fire(PlaybackEventType.TEMPO_CHANGED);
    }

    @Override
    public float getTempoFactor() {
        return sequencer.getTempoFactor();
    }

    @Override
    public PlaybackState getState() {
        return state;
    }

    @Override
    public PlaybackPosition getPosition() {
        if (loadedSong == null || sequencer.getSequence() == null) {
            return PlaybackPosition.zero();
        }
        long posMicros = sequencer.getDelayedPosition();
        long lengthMicros = sequencer.getLength();
        return new PlaybackPosition(
                Duration.ofNanos(posMicros * 1_000L),
                Duration.ofNanos(lengthMicros * 1_000L));
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            sequencer.stop();
            sequencer.clearSequence();
            sequencer.close();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Error closing Lotro sequencer", ex);
        }
        loadedSong = null;
        abcInfo = null;
        state = PlaybackState.IDLE;
    }

    private LoadedSong toLoadedSong(AbcInfo info, Sequence song) {
        List<PartInfo> parts = new ArrayList<>();
        Track[] tracks = song.getTracks();
        for (int i = 0; i < tracks.length; i++) {
            if (!trackHasNotes(tracks[i])) {
                continue;
            }
            String name = info.getPartName(i);
            if (name == null || name.isBlank()) {
                name = "Part " + i;
            }
            String instrument = info.getPartInstrument(i).friendlyName;
            parts.add(new PartInfo(i, name, instrument));
        }
        long micros = song.getMicrosecondLength();
        return new LoadedSong(
                info.getTitle(),
                info.getComposer(),
                Duration.ofNanos(micros * 1_000L),
                parts);
    }

    private static boolean trackHasNotes(Track track) {
        for (int j = 0; j < track.size(); j++) {
            MidiEvent evt = track.get(j);
            if (evt.getMessage() instanceof ShortMessage shortMessage
                    && shortMessage.getCommand() == ShortMessage.NOTE_ON) {
                return true;
            }
        }
        return false;
    }

    private void fire(PlaybackEventType type) {
        PlaybackEvent event = new PlaybackEvent(type, state);
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackEvent(event);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Playback listener failed", ex);
            }
        }
    }

    private void requireLoaded(String action) throws PlaybackException {
        if (loadedSong == null || sequencer.getSequence() == null) {
            state = PlaybackState.ERROR;
            fire(PlaybackEventType.STATE_CHANGED);
            throw new PlaybackException("Cannot " + action + " before load()");
        }
    }

    private void validateTrackIndex(int partIndex) throws PlaybackException {
        Sequence sequence = sequencer.getSequence();
        if (sequence == null || partIndex < 0 || partIndex >= sequence.getTracks().length) {
            throw new PlaybackException("Invalid part index: " + partIndex);
        }
    }

    private void ensureOpen() throws PlaybackException {
        if (closed) {
            throw new PlaybackException("Playback engine is closed");
        }
    }
}
