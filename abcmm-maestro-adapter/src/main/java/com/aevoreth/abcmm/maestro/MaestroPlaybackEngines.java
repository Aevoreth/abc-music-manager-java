package com.aevoreth.abcmm.maestro;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;

/**
 * Factory for Maestro-backed playback engines.
 * Callers outside this module must depend only on {@link AbcPlaybackEngine}.
 */
public final class MaestroPlaybackEngines {

    private MaestroPlaybackEngines() {
    }

    /**
     * Returns the current engine implementation.
     * Full audio integration is not complete in this bootstrap; see
     * {@link StubAbcPlaybackEngine} and {@code docs/MAESTRO_INTEGRATION.md}.
     */
    public static AbcPlaybackEngine create() {
        return new StubAbcPlaybackEngine();
    }
}
