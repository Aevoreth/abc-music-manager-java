package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;

class LibraryContextMenuTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void playStartsWithFirstSelectedAndQueuesTheRest() {
        LibraryPanel panel = new LibraryPanel();
        List<String> log = new ArrayList<>();
        panel.setPlayListener(song -> log.add("play:" + song.id()));
        panel.setEnqueueListener(song -> log.add("queue:" + song.id()));
        LibrarySong first = song(1, "First", 0, null);
        LibrarySong second = song(2, "Second", 0, null);
        LibrarySong third = song(3, "Third", 0, null);

        menuItem(panel.rowPopup(List.of(first, second, third), second), "Play").doClick();

        assertEquals(List.of("play:1", "queue:2", "queue:3"), log);
    }

    @Test
    void addToQueueAppendsEverySelectedSong() {
        LibraryPanel panel = new LibraryPanel();
        List<Long> queued = new ArrayList<>();
        panel.setEnqueueListener(song -> queued.add(song.id()));

        menuItem(panel.rowPopup(List.of(song(4, "A", 0, null), song(5, "B", 0, null)), null), "Add to queue")
                .doClick();

        assertEquals(List.of(4L, 5L), queued);
    }

    @Test
    void editUsesTheSongUnderThePointer() {
        LibraryPanel panel = new LibraryPanel();
        List<Long> edited = new ArrayList<>();
        panel.setEditSongListener(song -> edited.add(song.id()));
        LibrarySong clicked = song(9, "Clicked", 0, null);

        menuItem(panel.rowPopup(List.of(song(1, "Other", 0, null), clicked), clicked), "Edit").doClick();

        assertEquals(List.of(9L), edited);
    }

    @Test
    void newSetSitsBelowADividerAndReceivesTheSelection() {
        LibraryPanel panel = new LibraryPanel();
        List<LibrarySong> created = new ArrayList<>();
        panel.setNewSetlistListener(created::addAll);
        LibrarySong first = song(1, "First", 0, null);
        LibrarySong second = song(2, "Second", 0, null);
        JMenu addToSetlist = (JMenu) menuItem(panel.rowPopup(List.of(first, second), first), "Add to setlist");
        Component[] items = addToSetlist.getMenuComponents();

        assertInstanceOf(JPopupMenu.Separator.class, items[items.length - 2]);
        assertEquals("New Set...", ((JMenuItem) items[items.length - 1]).getText());
        ((JMenuItem) items[items.length - 1]).doClick();
        assertEquals(List.of(first, second), created);
    }

    @Test
    void addToSetlistWithoutNewSetOmitsTheDivider() {
        JMenu menu = AddToSetlistMenu.build(null, null, setlist -> {
        });
        for (Component component : menu.getMenuComponents()) {
            assertFalse(component instanceof JPopupMenu.Separator);
            if (component instanceof JMenuItem item) {
                assertFalse("New Set...".equals(item.getText()));
            }
        }
    }

    @Test
    void ratingAndStatusApplyToEverySelectedSong() {
        List<String> writes = new ArrayList<>();
        AtomicInteger changed = new AtomicInteger();
        SongFieldActions actions = new SongFieldActions(new JPanel());
        actions.setSongRepository(songRepository(writes));
        actions.setStatuses(List.of(new StatusInfo(7, "Ready", "#00aa00", 0)));
        actions.setOnChanged(changed::incrementAndGet);
        LibrarySong low = song(1, "Low", 1, null);
        LibrarySong high = song(2, "High", 2, 3L);

        JCheckBoxMenuItem four = (JCheckBoxMenuItem) menuItem(
                actions.ratingMenu(List.of(low, high)), starLabel(4));
        assertFalse(four.isSelected());
        four.doClick();

        JCheckBoxMenuItem status = onlyCheckBox(actions.statusMenu(List.of(low, high)));
        assertFalse(status.isSelected());
        status.doClick();

        assertEquals(List.of("rating:1:4", "rating:2:4", "status:1:7", "status:2:7"), writes);
        assertEquals(2, changed.get());
    }

    @Test
    void choosingTheSharedRatingClearsIt() {
        List<String> writes = new ArrayList<>();
        SongFieldActions actions = new SongFieldActions(new JPanel());
        actions.setSongRepository(songRepository(writes));
        LibrarySong left = song(1, "Left", 3, null);
        LibrarySong right = song(2, "Right", 3, null);

        JCheckBoxMenuItem three = (JCheckBoxMenuItem) menuItem(
                actions.ratingMenu(List.of(left, right)), starLabel(3));
        assertTrue(three.isSelected());
        three.doClick();

        assertEquals(List.of("rating:1:0", "rating:2:0"), writes);
    }

    @Test
    void playedNowLogsEverySongAndHistoryNeedsASingleSong() {
        List<Long> played = new ArrayList<>();
        AtomicInteger changed = new AtomicInteger();
        SongFieldActions actions = new SongFieldActions(new JPanel());
        actions.setPlayLogRepository(playLogRepository(played));
        actions.setOnChanged(changed::incrementAndGet);
        LibrarySong one = song(1, "One", 0, null);
        LibrarySong two = song(2, "Two", 0, null);

        JMenu bulk = actions.historyMenu(List.of(one, two));
        assertFalse(menuItem(bulk, "History").isEnabled());
        menuItem(bulk, "Played Now").doClick();
        assertTrue(menuItem(actions.historyMenu(List.of(one)), "History").isEnabled());

        assertEquals(List.of(1L, 2L), played);
        assertEquals(1, changed.get());
        assertEquals("Set Last Played Time...", menuItem(bulk, "Set Last Played Time...").getText());
    }

    @Test
    void selectedSongsStayInViewOrderAndRightClickKeepsTheSelection() {
        LibraryPanel panel = new LibraryPanel();
        LibrarySong top = song(1, "Top", 0, null);
        LibrarySong middle = song(2, "Middle", 0, null);
        LibrarySong bottom = song(3, "Bottom", 0, null);
        panel.setSongs(List.of(top, middle, bottom));
        JTable table = panel.libraryTable();
        table.setSize(800, 400);
        table.doLayout();
        table.getSelectionModel().addSelectionInterval(2, 2);
        table.getSelectionModel().addSelectionInterval(0, 0);

        assertEquals(List.of(top, bottom), panel.selectedSongsInViewOrder());

        AtomicInteger presses = new AtomicInteger();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                presses.incrementAndGet();
            }
        });

        table.dispatchEvent(rightClick(table, MouseEvent.MOUSE_PRESSED, 4, false));
        assertEquals(0, presses.get());
        assertEquals(List.of(top, bottom), panel.selectedSongsInViewOrder());

        int middleY = table.getRowHeight() + 4;
        table.dispatchEvent(rightClick(table, MouseEvent.MOUSE_PRESSED, middleY, false));
        assertEquals(1, presses.get());
    }

    private static MouseEvent rightClick(Component source, int id, int y, boolean popupTrigger) {
        return new MouseEvent(
                source,
                id,
                System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK,
                8,
                y,
                1,
                popupTrigger,
                MouseEvent.BUTTON3);
    }

    private static String starLabel(int stars) {
        return String.valueOf(LibraryDisplayFormats.STAR_FILLED).repeat(stars);
    }

    private static JCheckBoxMenuItem onlyCheckBox(JMenu menu) {
        JCheckBoxMenuItem found = null;
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JCheckBoxMenuItem item) {
                found = item;
            }
        }
        if (found == null) {
            throw new AssertionError("no checkbox item");
        }
        return found;
    }

    private static JMenuItem menuItem(MenuHolder menu, String text) {
        Component[] components = menu.components();
        for (Component component : components) {
            if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("missing menu item: " + text);
    }

    private static JMenuItem menuItem(JPopupMenu menu, String text) {
        return menuItem(menu::getComponents, text);
    }

    private static JMenuItem menuItem(JMenu menu, String text) {
        return menuItem(menu::getMenuComponents, text);
    }

    private interface MenuHolder {
        Component[] components();
    }

    private static SongRepository songRepository(List<String> writes) {
        return (SongRepository) Proxy.newProxyInstance(
                SongRepository.class.getClassLoader(),
                new Class<?>[] {SongRepository.class},
                (proxy, method, args) -> {
                    if ("updateSongAppMetadata".equals(method.getName())) {
                        SongAppMetadataUpdate update = (SongAppMetadataUpdate) args[1];
                        if (update.updateRating()) {
                            writes.add("rating:" + args[0] + ":" + update.rating());
                        }
                        if (update.updateStatusId()) {
                            writes.add("status:" + args[0] + ":" + update.statusId());
                        }
                        return null;
                    }
                    return defaultReturn(method.getReturnType());
                });
    }

    private static PlayLogRepository playLogRepository(List<Long> played) {
        return (PlayLogRepository) Proxy.newProxyInstance(
                PlayLogRepository.class.getClassLoader(),
                new Class<?>[] {PlayLogRepository.class},
                (proxy, method, args) -> {
                    if ("logPlay".equals(method.getName())) {
                        played.add((Long) args[0]);
                        return null;
                    }
                    return defaultReturn(method.getReturnType());
                });
    }

    private static Object defaultReturn(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        if (type.equals(Optional.class)) {
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }

    private static LibrarySong song(long id, String title, Integer rating, Long statusId) {
        return new LibrarySong(
                id, title, "", "", null, 0, null, null, 0, rating, statusId, null, null, null, null, false);
    }
}
