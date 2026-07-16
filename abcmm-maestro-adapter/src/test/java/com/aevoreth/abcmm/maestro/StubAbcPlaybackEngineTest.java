package com.aevoreth.abcmm.maestro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackState;

class StubAbcPlaybackEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void loadPlayPauseStopAndClose() throws Exception {
        Path abc = tempDir.resolve("Demo Song.abc");
        Files.writeString(abc, "X:1\nT:Demo\nK:C\nC\n");

        try (StubAbcPlaybackEngine engine = new StubAbcPlaybackEngine()) {
            LoadedSong song = engine.load(abc);
            assertEquals("Demo Song", song.title());
            assertEquals(PlaybackState.LOADED, engine.getState());

            engine.play();
            assertEquals(PlaybackState.PLAYING, engine.getState());
            engine.pause();
            assertEquals(PlaybackState.PAUSED, engine.getState());
            engine.seek(Duration.ofSeconds(2));
            assertEquals(Duration.ofSeconds(2), engine.getPosition().position());
            engine.setVolume(0.5);
            assertEquals(0.5, engine.getVolume());
            engine.stop();
            assertEquals(PlaybackState.STOPPED, engine.getState());
        }
    }

    @Test
    void playBeforeLoadFails() {
        try (StubAbcPlaybackEngine engine = new StubAbcPlaybackEngine()) {
            PlaybackException ex = assertThrows(PlaybackException.class, engine::play);
            assertTrue(ex.getMessage().contains("load"));
            assertEquals(PlaybackState.ERROR, engine.getState());
        }
    }
}
