package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import javax.swing.JCheckBoxMenuItem;
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
            try {
                playLogRepository.logPlay(song.id(), null, null);
                onChanged.run();
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(
                        owner, ex.getMessage(), "Playback History", JOptionPane.ERROR_MESSAGE);
            }
        } else if (xInCell < third * 2) {
            PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                    SwingUtilities.getWindowAncestor(owner),
                    "Set last played",
                    java.time.Instant.now(),
                    null,
                    false);
            dialog.showDialog().ifPresent(result -> {
                try {
                    playLogRepository.logPlayAt(song.id(), result.playedAtIso(), null, null);
                    onChanged.run();
                } catch (LibraryException ex) {
                    JOptionPane.showMessageDialog(
                            owner, ex.getMessage(), "Playback History", JOptionPane.ERROR_MESSAGE);
                }
            });
        } else {
            PlayHistoryDialog dialog = new PlayHistoryDialog(
                    SwingUtilities.getWindowAncestor(owner),
                    playLogRepository,
                    song.id(),
                    song.title());
            if (dialog.showDialog()) {
                onChanged.run();
            }
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
        try {
            songRepository.updateSongAppMetadata(song.id(), SongAppMetadataUpdate.ratingOnly(newRating));
            onChanged.run();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Rating", JOptionPane.ERROR_MESSAGE);
        }
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
        for (StatusInfo status : statuses) {
            JCheckBoxMenuItem item =
                    new JCheckBoxMenuItem(LibraryDisplayFormats.statusFilterLabel(status));
            item.setSelected(song.statusId() != null && song.statusId() == status.id());
            long statusId = status.id();
            item.addActionListener(ev -> {
                try {
                    songRepository.updateSongAppMetadata(
                            song.id(), SongAppMetadataUpdate.statusOnly(statusId));
                    onChanged.run();
                } catch (LibraryException ex) {
                    JOptionPane.showMessageDialog(
                            owner, ex.getMessage(), "Status", JOptionPane.ERROR_MESSAGE);
                }
            });
            menu.add(item);
        }
        Point tableLoc = table.getLocationOnScreen();
        menu.show(table, screenX - tableLoc.x, screenY - tableLoc.y);
    }
}
