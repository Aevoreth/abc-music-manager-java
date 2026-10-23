package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SetlistRef;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;

/**
 * Library-column clicks that read and write the original song: status, rating,
 * playback history, set navigation, and edit.
 */
final class SongFieldActions {

    private final Component owner;
    private SongRepository songRepository;
    private PlayLogRepository playLogRepository;
    private List<StatusInfo> statuses = List.of();
    private Runnable onChanged = () -> {
    };
    private LongConsumer navigateToSetlist = id -> {
    };
    private Consumer<LibrarySong> editSong = song -> {
    };

    SongFieldActions(Component owner) {
        this.owner = owner;
    }

    void setSongRepository(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    void setPlayLogRepository(PlayLogRepository playLogRepository) {
        this.playLogRepository = playLogRepository;
    }

    void setStatuses(List<StatusInfo> statuses) {
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

    void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged == null ? () -> {
        } : onChanged;
    }

    void setNavigateToSetlist(LongConsumer navigateToSetlist) {
        this.navigateToSetlist = navigateToSetlist == null ? id -> {
        } : navigateToSetlist;
    }

    void setEditSong(Consumer<LibrarySong> editSong) {
        this.editSong = editSong == null ? song -> {
        } : editSong;
    }

    /** Tell listeners the original song changed (library, setlist, and Set Play). */
    void notifyChanged() {
        onChanged.run();
    }

    void handle(
            JTable table,
            SongLinkedColumns.Kind kind,
            LibrarySong song,
            int xInCell,
            int cellWidth,
            int screenX,
            int screenY) {
        if (song == null || kind == null) {
            return;
        }
        switch (kind) {
            case HISTORY -> handleHistory(table, song, xInCell, cellWidth);
            case RATING -> handleRating(song, xInCell, cellWidth);
            case SET -> handleSet(table, song, screenX, screenY);
            case STATUS -> handleStatus(table, song, screenX, screenY);
            case EDIT -> editSong.accept(song);
            case LAST_PLAYED, TRANSCRIBER -> {
            }
        }
    }

    void handleHistory(JTable table, LibrarySong song, int xInCell, int cellWidth) {
        if (playLogRepository == null || song == null) {
            return;
        }
        int third = Math.max(1, cellWidth / 3);
        if (xInCell < third) {
            logPlayedNow(List.of(song));
        } else if (xInCell < third * 2) {
            promptLastPlayed(List.of(song));
        } else {
            showHistory(song);
        }
    }

    void handleRating(LibrarySong song, int xInCell, int cellWidth) {
        if (songRepository == null || song == null) {
            return;
        }
        int starWidth = Math.max(1, cellWidth / 5);
        int starIdx = Math.min(5, Math.max(1, (xInCell / starWidth) + 1));
        int current = song.rating() == null ? 0 : song.rating();
        int newRating = current == starIdx ? 0 : starIdx;
        applyRating(List.of(song), newRating);
    }

    void handleSet(JTable table, LibrarySong song, int screenX, int screenY) {
        if (song == null || !song.inUpcomingSet() || songRepository == null) {
            return;
        }
        try {
            List<SetlistRef> setlists = songRepository.listUnlockedSetlistsContainingSong(song.id());
            if (setlists.isEmpty()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            for (SetlistRef setlist : setlists) {
                JMenuItem item = new JMenuItem("Go to: " + setlist.name());
                long setlistId = setlist.id();
                item.addActionListener(ev -> navigateToSetlist.accept(setlistId));
                menu.add(item);
            }
            Point tableLoc = table.getLocationOnScreen();
            menu.show(table, screenX - tableLoc.x, screenY - tableLoc.y);
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Set", JOptionPane.ERROR_MESSAGE);
        }
    }

    void handleStatus(JTable table, LibrarySong song, int screenX, int screenY) {
        if (song == null || songRepository == null || statuses.isEmpty()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (Component component : statusMenu(List.of(song)).getMenuComponents()) {
            menu.add(component);
        }
        Point tableLoc = table.getLocationOnScreen();
        menu.show(table, screenX - tableLoc.x, screenY - tableLoc.y);
    }

    JMenu ratingMenu(List<LibrarySong> songs) {
        JMenu menu = new JMenu("Rating");
        if (songRepository == null || songs == null || songs.isEmpty()) {
            menu.add(disabledItem("Unavailable"));
            return menu;
        }
        Integer shared = sharedRating(songs);
        for (int stars = 1; stars <= 5; stars++) {
            int rating = stars;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(ratingLabel(stars));
            item.setSelected(shared != null && shared == stars);
            item.addActionListener(ev -> {
                int next = shared != null && shared == rating ? 0 : rating;
                applyRating(songs, next);
            });
            menu.add(item);
        }
        menu.addSeparator();
        JCheckBoxMenuItem clear = new JCheckBoxMenuItem("Clear");
        clear.setSelected(shared != null && shared == 0);
        clear.addActionListener(ev -> applyRating(songs, 0));
        menu.add(clear);
        return menu;
    }

    JMenu statusMenu(List<LibrarySong> songs) {
        JMenu menu = new JMenu("Status");
        if (songRepository == null || songs == null || songs.isEmpty()) {
            menu.add(disabledItem("Unavailable"));
            return menu;
        }
        if (statuses.isEmpty()) {
            menu.add(disabledItem("No statuses"));
            return menu;
        }
        Long shared = sharedStatusId(songs);
        for (StatusInfo status : statuses) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(LibraryDisplayFormats.statusFilterLabel(status));
            item.setSelected(shared != null && shared == status.id());
            long statusId = status.id();
            item.addActionListener(ev -> applyStatus(songs, statusId));
            menu.add(item);
        }
        return menu;
    }

    JMenu historyMenu(List<LibrarySong> songs) {
        JMenu menu = new JMenu("Playback History");
        if (playLogRepository == null || songs == null || songs.isEmpty()) {
            menu.add(disabledItem("Unavailable"));
            return menu;
        }
        JMenuItem playedNow = new JMenuItem("Played Now");
        playedNow.addActionListener(ev -> logPlayedNow(songs));
        menu.add(playedNow);
        JMenuItem setTime = new JMenuItem("Set Last Played Time...");
        setTime.addActionListener(ev -> promptLastPlayed(songs));
        menu.add(setTime);
        JMenuItem history = new JMenuItem("History");
        if (songs.size() == 1) {
            LibrarySong song = songs.get(0);
            history.addActionListener(ev -> showHistory(song));
        } else {
            history.setEnabled(false);
            history.setToolTipText("Select a single song to open its history");
        }
        menu.add(history);
        return menu;
    }

    private void applyRating(List<LibrarySong> songs, int rating) {
        if (songRepository == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(5, rating));
        runEach(songs, "Rating", song -> {
            int current = song.rating() == null ? 0 : song.rating();
            if (current == clamped) {
                return false;
            }
            songRepository.updateSongAppMetadata(song.id(), SongAppMetadataUpdate.ratingOnly(clamped));
            return true;
        });
    }

    private void applyStatus(List<LibrarySong> songs, long statusId) {
        if (songRepository == null) {
            return;
        }
        runEach(songs, "Status", song -> {
            if (song.statusId() != null && song.statusId() == statusId) {
                return false;
            }
            songRepository.updateSongAppMetadata(song.id(), SongAppMetadataUpdate.statusOnly(statusId));
            return true;
        });
    }

    private void logPlayedNow(List<LibrarySong> songs) {
        if (playLogRepository == null) {
            return;
        }
        runEach(songs, "Playback History", song -> {
            playLogRepository.logPlay(song.id(), null, null);
            return true;
        });
    }

    private void promptLastPlayed(List<LibrarySong> songs) {
        if (playLogRepository == null || songs == null || songs.isEmpty()) {
            return;
        }
        PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                SwingUtilities.getWindowAncestor(owner),
                "Set last played",
                java.time.Instant.now(),
                null,
                false);
        dialog.showDialog().ifPresent(result -> logPlayedAt(songs, result.playedAtIso()));
    }

    private void logPlayedAt(List<LibrarySong> songs, String playedAtIso) {
        if (playLogRepository == null) {
            return;
        }
        runEach(songs, "Playback History", song -> {
            playLogRepository.logPlayAt(song.id(), playedAtIso, null, null);
            return true;
        });
    }

    private void showHistory(LibrarySong song) {
        if (playLogRepository == null || song == null) {
            return;
        }
        PlayHistoryDialog dialog = new PlayHistoryDialog(
                SwingUtilities.getWindowAncestor(owner),
                playLogRepository,
                song.id(),
                song.title());
        if (dialog.showDialog()) {
            onChanged.run();
        }
    }

    private void runEach(List<LibrarySong> songs, String title, SongWrite write) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        StringBuilder errors = new StringBuilder();
        int failures = 0;
        boolean changed = false;
        for (LibrarySong song : songs) {
            if (song == null) {
                continue;
            }
            try {
                if (write.apply(song)) {
                    changed = true;
                }
            } catch (LibraryException ex) {
                failures++;
                if (failures <= 8) {
                    if (errors.length() > 0) {
                        errors.append('\n');
                    }
                    String message = ex.getMessage() == null ? "Failed" : ex.getMessage();
                    errors.append(song.title()).append(": ").append(message);
                }
            }
        }
        if (changed) {
            onChanged.run();
        }
        if (failures > 8) {
            errors.append('\n').append(failures - 8).append(" more failed.");
        }
        if (failures > 0) {
            JOptionPane.showMessageDialog(owner, errors.toString(), title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Integer sharedRating(List<LibrarySong> songs) {
        boolean any = false;
        int shared = 0;
        for (LibrarySong song : songs) {
            if (song == null) {
                continue;
            }
            int rating = song.rating() == null ? 0 : Math.max(0, Math.min(5, song.rating()));
            if (!any) {
                shared = rating;
                any = true;
            } else if (shared != rating) {
                return null;
            }
        }
        return any ? shared : null;
    }

    private static Long sharedStatusId(List<LibrarySong> songs) {
        boolean any = false;
        Long shared = null;
        for (LibrarySong song : songs) {
            if (song == null) {
                continue;
            }
            if (!any) {
                shared = song.statusId();
                any = true;
            } else if (shared == null ? song.statusId() != null : !shared.equals(song.statusId())) {
                return null;
            }
        }
        return any ? shared : null;
    }

    private static String ratingLabel(int stars) {
        return String.valueOf(LibraryDisplayFormats.STAR_FILLED).repeat(Math.max(0, stars));
    }

    private static JMenuItem disabledItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        return item;
    }

    @FunctionalInterface
    private interface SongWrite {
        /** @return true when a value was written */
        boolean apply(LibrarySong song) throws LibraryException;
    }
}
