package com.aevoreth.abcmm.domain.playback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Owns the runtime play queue and drives {@link AbcPlaybackEngine} for next/prev/auto-advance.
 */
public final class PlaybackSession implements AutoCloseable {

    private final AbcPlaybackEngine engine;
    private final Function<Long, Optional<Path>> pathResolver;
    private final List<PlayQueueItem> queue = new ArrayList<>();
    private final List<PlaybackSessionListener> listeners = new CopyOnWriteArrayList<>();
    private final PlaybackListener engineListener = this::onEngineEvent;

    private QueueSource source = QueueSource.NONE;
    private Long setlistId;
    private int currentIndex = -1;
    private boolean advancing;

    public PlaybackSession(AbcPlaybackEngine engine, Function<Long, Optional<Path>> pathResolver) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.engine.addPlaybackListener(engineListener);
    }

    public AbcPlaybackEngine engine() {
        return engine;
    }

    public QueueSource source() {
        return source;
    }

    public Long setlistId() {
        return setlistId;
    }

    public List<PlayQueueItem> queue() {
        return List.copyOf(queue);
    }

    public int currentIndex() {
        return currentIndex;
    }

    public PlayQueueItem currentItem() {
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return null;
        }
        return queue.get(currentIndex);
    }

    public void addSessionListener(PlaybackSessionListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeSessionListener(PlaybackSessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Replaces the playlist with a single song and starts playback.
     */
    public void playSong(long songId, String title) throws PlaybackException {
        queue.clear();
        queue.add(PlayQueueItem.ofSong(songId, title));
        source = QueueSource.SINGLE;
        setlistId = null;
        currentIndex = 0;
        fireSessionChanged();
        loadAndPlayCurrent();
    }

    /**
     * Replaces the playlist with a setlist and starts at {@code startIndex}.
     */
    public void playSetlist(long setlistId, List<PlayQueueItem> items, int startIndex)
            throws PlaybackException {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new PlaybackException("Setlist has no songs");
        }
        int index = Math.max(0, Math.min(startIndex, items.size() - 1));
        queue.clear();
        queue.addAll(items);
        source = QueueSource.SETLIST;
        this.setlistId = setlistId;
        currentIndex = index;
        fireSessionChanged();
        loadAndPlayCurrent();
    }

    /**
     * Appends a song without replacing the playlist. Starts playback if nothing is loaded.
     */
    public void enqueue(long songId, String title) throws PlaybackException {
        boolean wasEmpty = queue.isEmpty();
        if (source == QueueSource.SETLIST || source == QueueSource.SINGLE) {
            source = QueueSource.CUSTOM;
            setlistId = null;
        } else if (source == QueueSource.NONE) {
            source = QueueSource.CUSTOM;
        }
        queue.add(PlayQueueItem.ofSong(songId, title));
        fireSessionChanged();
        if (wasEmpty || engine.getLoadedSong() == null) {
            currentIndex = queue.size() - 1;
            fireSessionChanged();
            loadAndPlayCurrent();
        }
    }

    public void next() throws PlaybackException {
        if (currentIndex < 0 || currentIndex >= queue.size() - 1) {
            return;
        }
        currentIndex++;
        fireSessionChanged();
        loadAndPlayCurrent();
    }

    public void previous() throws PlaybackException {
        if (currentIndex <= 0) {
            return;
        }
        currentIndex--;
        fireSessionChanged();
        loadAndPlayCurrent();
    }

    public void playAt(int index) throws PlaybackException {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        currentIndex = index;
        fireSessionChanged();
        loadAndPlayCurrent();
    }

    /**
     * When the active playlist is backed by {@code setlistId}, rebuild order from the setlist
     * while keeping the current song selected when possible.
     */
    public void syncFromSetlistIfActive(long setlistId, List<PlayQueueItem> orderedItems) {
        if (source != QueueSource.SETLIST || this.setlistId == null || this.setlistId != setlistId) {
            return;
        }
        Objects.requireNonNull(orderedItems, "orderedItems");
        PlayQueueItem current = currentItem();
        Long currentItemId = current == null ? null : current.setlistItemId();
        long currentSongId = current == null ? -1L : current.songId();

        queue.clear();
        queue.addAll(orderedItems);

        int newIndex = -1;
        if (currentItemId != null) {
            for (int i = 0; i < queue.size(); i++) {
                if (currentItemId.equals(queue.get(i).setlistItemId())) {
                    newIndex = i;
                    break;
                }
            }
        }
        if (newIndex < 0 && currentSongId > 0) {
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).songId() == currentSongId) {
                    newIndex = i;
                    break;
                }
            }
        }
        if (queue.isEmpty()) {
            currentIndex = -1;
        } else if (newIndex >= 0) {
            currentIndex = newIndex;
        } else {
            currentIndex = Math.min(Math.max(currentIndex, 0), queue.size() - 1);
        }
        fireSessionChanged();
    }

    public boolean hasNext() {
        return currentIndex >= 0 && currentIndex < queue.size() - 1;
    }

    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    @Override
    public void close() {
        engine.removePlaybackListener(engineListener);
    }

    private void onEngineEvent(PlaybackEvent event) {
        if (event.type() != PlaybackEventType.SONG_ENDED || advancing) {
            return;
        }
        if (!hasNext()) {
            return;
        }
        advancing = true;
        try {
            next();
        } catch (PlaybackException ignored) {
            // UI may surface via state ERROR
        } finally {
            advancing = false;
        }
    }

    private void loadAndPlayCurrent() throws PlaybackException {
        PlayQueueItem item = currentItem();
        if (item == null) {
            throw new PlaybackException("No song selected in playlist");
        }
        Path path = pathResolver.apply(item.songId())
                .orElseThrow(() -> new PlaybackException(
                        "ABC file not found for song: " + item.title()));
        engine.load(path);
        engine.play();
    }

    private void fireSessionChanged() {
        for (PlaybackSessionListener listener : listeners) {
            listener.onSessionChanged();
        }
    }
}
