package com.aevoreth.abcmm.domain.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class DomainPlaybackTypesTest {

    @Test
    void loadedSongCopiesPartsAndDefaultsNullStrings() {
        LoadedSong song = new LoadedSong(null, null, null, List.of(new PartInfo(0, "Lead", "lute")));
        assertEquals("", song.title());
        assertEquals("", song.composer());
        assertEquals(Duration.ZERO, song.duration());
        assertEquals(1, song.partCount());
        assertEquals("Lead", song.parts().get(0).name());
        assertEquals("lute", song.parts().get(0).instrument());
    }

    @Test
    void partInfoRejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> new PartInfo(-1, "x", "y"));
    }

    @Test
    void playbackPositionRejectsNegatives() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackPosition(Duration.ofSeconds(-1), Duration.ZERO));
    }

    @Test
    void playbackStatesIncludeTransportValues() {
        assertTrue(PlaybackState.IDLE.name().length() > 0);
        assertEquals(PlaybackState.PLAYING, PlaybackState.valueOf("PLAYING"));
    }
}
