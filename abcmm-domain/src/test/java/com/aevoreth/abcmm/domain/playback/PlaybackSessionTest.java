package com.aevoreth.abcmm.domain.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaybackSessionTest {

    @TempDir
    Path tempDir;

    @Test
    void playSongReplacesPlaylist() throws Exception {
        Path abc = writeAbc("one.abc");
        FakeEngine engine = new FakeEngine();
        PlaybackSession session = new PlaybackSession(engine, id -> Optional.of(abc));

        session.enqueue(2, "Two");
        session.playSong(1, "One");

        assertEquals(QueueSource.SINGLE, session.source());
        assertEquals(1, session.queue().size());
        assertEquals(1L, session.queue().get(0).songId());
        assertEquals(PlaybackState.PLAYING, engine.getState());
        session.close();
        engine.close();
    }

    @Test
    void enqueueAppendsWithoutStoppingWhenAlreadyPlaying() throws Exception {
        Path abc = writeAbc("song.abc");
        FakeEngine engine = new FakeEngine();
        PlaybackSession session = new PlaybackSession(engine, id -> Optional.of(abc));

        session.playSong(1, "One");
        session.enqueue(2, "Two");

        assertEquals(2, session.queue().size());
        assertEquals(QueueSource.CUSTOM, session.source());
        assertEquals(0, session.currentIndex());
        assertEquals(1L, session.currentItem().songId());
        session.close();
        engine.close();
    }

    @Test
    void nextPreviousAndSongEndedAdvance() throws Exception {
        Path abc = writeAbc("song.abc");
        FakeEngine engine = new FakeEngine();
        PlaybackSession session = new PlaybackSession(engine, id -> Optional.of(abc));

        session.playSetlist(9, List.of(
                PlayQueueItem.ofSetlistItem(1, "A", 9, 100),
                PlayQueueItem.ofSetlistItem(2, "B", 9, 101),
                PlayQueueItem.ofSetlistItem(3, "C", 9, 102)), 0);

        assertTrue(session.hasNext());
        assertFalse(session.hasPrevious());
        session.next();
        assertEquals(1, session.currentIndex());
        assertEquals(2L, session.currentItem().songId());

        engine.fireSongEnded();
        assertEquals(2, session.currentIndex());
        assertEquals(3L, session.currentItem().songId());

        session.previous();
        assertEquals(1, session.currentIndex());
        session.close();
        engine.close();
    }

    @Test
    void setlistLiveSyncPreservesCurrentItem() throws Exception {
        Path abc = writeAbc("song.abc");
        FakeEngine engine = new FakeEngine();
        PlaybackSession session = new PlaybackSession(engine, id -> Optional.of(abc));

        session.playSetlist(5, List.of(
                PlayQueueItem.ofSetlistItem(1, "A", 5, 10),
                PlayQueueItem.ofSetlistItem(2, "B", 5, 11),
                PlayQueueItem.ofSetlistItem(3, "C", 5, 12)), 1);

        session.syncFromSetlistIfActive(5, List.of(
                PlayQueueItem.ofSetlistItem(3, "C", 5, 12),
                PlayQueueItem.ofSetlistItem(2, "B", 5, 11),
                PlayQueueItem.ofSetlistItem(1, "A", 5, 10)));

        assertEquals(QueueSource.SETLIST, session.source());
        assertEquals(3, session.queue().size());
        assertEquals(2L, session.currentItem().songId());
        assertEquals(11L, session.currentItem().setlistItemId());
        session.close();
        engine.close();
    }

    private Path writeAbc(String name) throws Exception {
        Path abc = tempDir.resolve(name);
        Files.writeString(abc, "X:1\nT:Test\nK:C\nC\n");
        return abc;
    }

    private static final class FakeEngine implements AbcPlaybackEngine {
        private PlaybackState state = PlaybackState.IDLE;
        private LoadedSong loaded;
        private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

        @Override
        public LoadedSong load(Path abcFile) {
            loaded = new LoadedSong(abcFile.getFileName().toString(), "", Duration.ofSeconds(10),
                    List.of(new PartInfo(0, "Part 1", "")));
            state = PlaybackState.LOADED;
            return loaded;
        }

        @Override
        public void play() {
            state = PlaybackState.PLAYING;
        }

        @Override
        public void pause() {
            state = PlaybackState.PAUSED;
        }

        @Override
        public void stop() {
            state = PlaybackState.STOPPED;
        }

        @Override
        public void seek(Duration position) {
        }

        @Override
        public void setPartMuted(int partIndex, boolean muted) {
        }

        @Override
        public void setPartSolo(int partIndex, boolean solo) {
        }

        @Override
        public boolean isPartMuted(int partIndex) {
            return false;
        }

        @Override
        public boolean isPartSolo(int partIndex) {
            return false;
        }

        @Override
        public void setVolume(double volume) {
        }

        @Override
        public double getVolume() {
            return 1.0;
        }

        @Override
        public void setTempoFactor(float tempoFactor) {
        }

        @Override
        public float getTempoFactor() {
            return 1.0f;
        }

        @Override
        public PlaybackState getState() {
            return state;
        }

        @Override
        public PlaybackPosition getPosition() {
            return new PlaybackPosition(Duration.ZERO, Duration.ofSeconds(10));
        }

        @Override
        public LoadedSong getLoadedSong() {
            return loaded;
        }

        @Override
        public void addPlaybackListener(PlaybackListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removePlaybackListener(PlaybackListener listener) {
            listeners.remove(listener);
        }

        void fireSongEnded() {
            state = PlaybackState.STOPPED;
            PlaybackEvent event = new PlaybackEvent(PlaybackEventType.SONG_ENDED, state);
            for (PlaybackListener listener : listeners) {
                listener.onPlaybackEvent(event);
            }
        }

        @Override
        public void close() {
            loaded = null;
            state = PlaybackState.IDLE;
        }
    }
}
