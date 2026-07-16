package com.aevoreth.abcmm.maestro;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.midi.MidiUnavailableException;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;

/**
 * Factory for Maestro-backed playback engines.
 * Callers outside this module must depend only on {@link AbcPlaybackEngine}.
 */
public final class MaestroPlaybackEngines {

    private static final Logger LOG = Logger.getLogger(MaestroPlaybackEngines.class.getName());

    private MaestroPlaybackEngines() {
    }

    /**
     * Returns a Lotro/MIDI-backed engine, or a stub if MIDI / module access fails.
     */
    public static AbcPlaybackEngine create() {
        try {
            return new LotroAbcPlaybackEngine();
        } catch (MidiUnavailableException | RuntimeException | LinkageError ex) {
            // LinkageError covers IllegalAccessError when --add-exports are missing.
            LOG.log(Level.SEVERE, "Failed to create Lotro playback engine; using stub", ex);
            return new StubAbcPlaybackEngine();
        }
    }

    /** Test / fallback factory that always returns the non-audio stub. */
    public static AbcPlaybackEngine createStub() {
        return new StubAbcPlaybackEngine();
    }
}
