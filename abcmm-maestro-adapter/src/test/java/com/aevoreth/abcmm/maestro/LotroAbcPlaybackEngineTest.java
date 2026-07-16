package com.aevoreth.abcmm.maestro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.sound.midi.MidiUnavailableException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PlaybackState;

/**
 * Smoke test for real Lotro engine. Skips if MIDI/soundfont cannot be initialized.
 */
class LotroAbcPlaybackEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void loadPlayPauseStopWhenMidiAvailable() throws Exception {
        LotroAbcPlaybackEngine engine;
        try {
            engine = new LotroAbcPlaybackEngine();
        } catch (MidiUnavailableException | RuntimeException ex) {
            assumeTrue(false, "MIDI unavailable: " + ex.getMessage());
            return;
        }

        Path abc = tempDir.resolve("smoke.abc");
        Files.writeString(abc, """
                X:1
                T:Smoke Test
                C:Tester
                M:4/4
                L:1/4
                Q:1/4=120
                K:C
                CDEF|
                """);

        try (engine) {
            LoadedSong song = engine.load(abc);
            assertNotNull(song);
            assertFalse(song.title().isBlank());
            assertTrue(song.partCount() >= 1);
            assertEquals(PlaybackState.LOADED, engine.getState());

            engine.setVolume(0.4);
            engine.setTempoFactor(1.1f);
            assertEquals(0.4, engine.getVolume(), 0.001);
            assertEquals(1.1f, engine.getTempoFactor(), 0.001f);

            engine.play();
            assertEquals(PlaybackState.PLAYING, engine.getState());
            engine.pause();
            assertEquals(PlaybackState.PAUSED, engine.getState());
            engine.seek(Duration.ofMillis(100));
            assertTrue(engine.getPosition().position().toMillis() >= 0);
            engine.stop();
            assertEquals(PlaybackState.STOPPED, engine.getState());
        }
    }
}
