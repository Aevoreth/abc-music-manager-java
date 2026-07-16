package com.aevoreth.abcmm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.SettingsRepository;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackSession;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.prefs.PreferencesException;
import com.aevoreth.abcmm.domain.prefs.PreferencesStore;
import com.aevoreth.abcmm.domain.scan.LibraryScanService;
import com.aevoreth.abcmm.domain.scan.ScanRequest;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.aevoreth.abcmm.maestro.MaestroPlaybackEngines;
import com.aevoreth.abcmm.storage.DataPaths;
import com.aevoreth.abcmm.storage.JsonPreferencesStore;
import com.aevoreth.abcmm.storage.SqliteBandRepository;
import com.aevoreth.abcmm.storage.SqliteDatabase;
import com.aevoreth.abcmm.storage.SqliteLibraryScanService;
import com.aevoreth.abcmm.storage.SqlitePlayerRepository;
import com.aevoreth.abcmm.storage.SqliteSetlistRepository;
import com.aevoreth.abcmm.storage.SqliteSettingsRepository;
import com.aevoreth.abcmm.storage.SqliteSongLayoutRepository;
import com.aevoreth.abcmm.storage.SqliteSongRepository;
import com.aevoreth.abcmm.ui.AbcmmThemer;
import com.aevoreth.abcmm.ui.BandsPanel;
import com.aevoreth.abcmm.ui.LibraryPanel;
import com.aevoreth.abcmm.ui.PlaybackPanel;
import com.aevoreth.abcmm.ui.ScanLibraryDialog;
import com.aevoreth.abcmm.ui.SetlistsPanel;
import com.aevoreth.abcmm.ui.SettingsDialog;
import com.aevoreth.abcmm.ui.StatusBar;

/**
 * Main application window: Library, Setlists, Bands, Playback placeholder, Settings, status bar.
 */
public final class MainFrame extends JFrame {

    public static final String APP_TITLE = "ABC Music Manager";

    private static final String[] NAV_SECTIONS = {"Library", "Setlists", "Bands"};

    private final AbcPlaybackEngine playbackEngine;
    private final PlaybackSession playbackSession;
    private final PreferencesStore preferencesStore;
    private Preferences preferences;
    private SqliteDatabase database;
    private SongRepository songRepository;
    private SettingsRepository settingsRepository;
    private PlayerRepository playerRepository;
    private BandRepository bandRepository;
    private SetlistRepository setlistRepository;
    private SongLayoutRepository songLayoutRepository;
    private LibraryScanService scanService;
    private List<StatusInfo> statuses = List.of();
    private List<FolderRuleInfo> folderRules = List.of();
    private List<AccountTargetInfo> accountTargets = List.of();

    private final LibraryPanel libraryPanel;
    private final SetlistsPanel setlistsPanel;
    private final BandsPanel bandsPanel;
    private final PlaybackPanel playbackPanel;
    private final StatusBar statusBar;
    private final JSplitPane verticalSplit;
    private final JTabbedPane navTabs;
    private int currentNavIndex;
    private boolean suppressNavChange;

    public MainFrame() {
        this(MaestroPlaybackEngines.create(), JsonPreferencesStore.atDefaultLocation());
    }

    MainFrame(PreferencesStore preferencesStore) {
        this(MaestroPlaybackEngines.create(), preferencesStore);
    }

    MainFrame(AbcPlaybackEngine playbackEngine) {
        this(playbackEngine, JsonPreferencesStore.atDefaultLocation());
    }

    MainFrame(AbcPlaybackEngine playbackEngine, PreferencesStore preferencesStore) {
        super(APP_TITLE);
        this.playbackEngine = playbackEngine;
        this.preferencesStore = preferencesStore;
        this.preferences = preferencesStore.load();
        ensureDefaultLotroRootPersisted();

        this.playbackSession = new PlaybackSession(playbackEngine, songId -> {
            if (songRepository == null) {
                return java.util.Optional.empty();
            }
            try {
                return songRepository.resolvePrimaryAbcPath(songId);
            } catch (LibraryException ex) {
                return java.util.Optional.empty();
            }
        });

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setPreferredSize(new Dimension(
                Preferences.DEFAULT_WINDOW_WIDTH, Preferences.DEFAULT_WINDOW_HEIGHT));

        libraryPanel = new LibraryPanel();
        setlistsPanel = new SetlistsPanel();
        setlistsPanel.setPreferences(preferences);
        bandsPanel = new BandsPanel();
        playbackPanel = new PlaybackPanel();
        statusBar = new StatusBar();

        navTabs = new JTabbedPane(JTabbedPane.TOP);
        navTabs.addTab(NAV_SECTIONS[0], libraryPanel);
        navTabs.addTab(NAV_SECTIONS[1], setlistsPanel);
        navTabs.addTab(NAV_SECTIONS[2], bandsPanel);
        currentNavIndex = 0;
        navTabs.addChangeListener(e -> {
            if (suppressNavChange) {
                return;
            }
            int selected = navTabs.getSelectedIndex();
            if (selected == currentNavIndex) {
                preferences.extras().put("java_nav_section", selected);
                return;
            }
            if (!confirmLeavePageWithUnsaved(selected)) {
                suppressNavChange = true;
                try {
                    navTabs.setSelectedIndex(currentNavIndex);
                } finally {
                    suppressNavChange = false;
                }
                return;
            }
            currentNavIndex = selected;
            preferences.extras().put("java_nav_section", selected);
        });

        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, navTabs, playbackPanel);
        verticalSplit.setResizeWeight(0.72);
        verticalSplit.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout());
        content.add(verticalSplit, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setJMenuBar(buildMenuBar());

        libraryPanel.setFilterListener(this::reloadLibrary);
        libraryPanel.setDefaultFilters(preferences.defaultFilters());
        libraryPanel.setPlayListener(song -> {
            try {
                playbackSession.playSong(song.id(), song.title());
                statusBar.setMessage("Playing: " + song.title());
            } catch (PlaybackException ex) {
                statusBar.setMessage(ex.getMessage());
            }
        });
        libraryPanel.setEnqueueListener(song -> {
            try {
                playbackSession.enqueue(song.id(), song.title());
                statusBar.setMessage("Queued: " + song.title());
            } catch (PlaybackException ex) {
                statusBar.setMessage(ex.getMessage());
            }
        });
        libraryPanel.setAddToSetlistListener(this::addLibrarySongToSetlist);
        setlistsPanel.setPlaybackSession(playbackSession, msg -> statusBar.setMessage(msg));
        playbackPanel.bind(
                playbackSession,
                preferences,
                msg -> statusBar.setMessage(msg),
                this::persistPreferencesQuietly);

        applyPreferencesToUi(false);
        openDatabaseAndLoad();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!confirmLeavePageWithUnsaved(-1)) {
                    return;
                }
                persistWindowState();
                dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                shutdown();
                System.exit(0);
            }
        });

        pack();
        if (!restoreWindowGeometry()) {
            setLocationRelativeTo(null);
        }
        restoreNavSelection();
    }

    private boolean pageHasUnsavedChanges(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= navTabs.getTabCount()) {
            return false;
        }
        if (navTabs.getComponentAt(pageIndex) instanceof BandsPanel bands) {
            return bands.hasUnsavedChanges();
        }
        return false;
    }

    /**
     * @param targetIndex destination tab, or {@code -1} when closing the window
     * @return true if navigation/close may proceed
     */
    private boolean confirmLeavePageWithUnsaved(int targetIndex) {
        if (currentNavIndex == targetIndex) {
            return true;
        }
        if (!pageHasUnsavedChanges(currentNavIndex)) {
            return true;
        }
        int reply = JOptionPane.showConfirmDialog(
                this,
                "You have unsaved changes. Are you sure you want to leave?",
                "Unsaved changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return reply == JOptionPane.YES_OPTION;
    }

    void shutdown() {
        if (playbackPanel != null) {
            playbackPanel.stopTimers();
        }
        playbackSession.close();
        if (songRepository != null) {
            songRepository.close();
            songRepository = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        playbackEngine.close();
    }

    private void persistPreferencesQuietly() {
        try {
            preferencesStore.save(preferences);
        } catch (PreferencesException ignored) {
            // volume/tempo prefs are best-effort
        }
    }

    AbcPlaybackEngine playbackEngine() {
        return playbackEngine;
    }

    LibraryPanel libraryPanel() {
        return libraryPanel;
    }

    StatusBar statusBar() {
        return statusBar;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem scan = new JMenuItem("Scan Library…");
        scan.addActionListener(e -> runLibraryScan());
        JMenuItem settings = new JMenuItem("Settings…");
        settings.addActionListener(e -> openSettings());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            if (!confirmLeavePageWithUnsaved(-1)) {
                return;
            }
            persistWindowState();
            dispose();
        });
        file.add(scan);
        file.addSeparator();
        file.add(settings);
        file.addSeparator();
        file.add(exit);
        menuBar.add(file);
        return menuBar;
    }

    private void restoreNavSelection() {
        Object stored = preferences.extras().get("java_nav_section");
        Integer index = asInt(stored);
        if (index != null && index >= 0 && index < NAV_SECTIONS.length) {
            suppressNavChange = true;
            try {
                navTabs.setSelectedIndex(index);
                currentNavIndex = index;
            } finally {
                suppressNavChange = false;
            }
        }
    }

    private void ensureDefaultLotroRootPersisted() {
        if (LotroPaths.ensureDefaultLotroRoot(preferences)) {
            try {
                preferencesStore.save(preferences);
            } catch (PreferencesException ignored) {
                // leave in-memory default; user can fix via Settings
            }
        }
    }

    private void openDatabaseAndLoad() {
        try {
            database = SqliteDatabase.openOrCreate(DataPaths.databasePath());
            songRepository = new SqliteSongRepository(database, false);
            settingsRepository = new SqliteSettingsRepository(database);
            playerRepository = new SqlitePlayerRepository(database);
            bandRepository = new SqliteBandRepository(database);
            setlistRepository = new SqliteSetlistRepository(database);
            songLayoutRepository = new SqliteSongLayoutRepository(database);
            scanService = new SqliteLibraryScanService(database);

            bandsPanel.bind(playerRepository, bandRepository);
            setlistsPanel.bind(
                    playerRepository,
                    bandRepository,
                    setlistRepository,
                    songRepository,
                    songLayoutRepository);
            libraryPanel.setSetlistRepository(setlistRepository);

            refreshEntityCaches();
            libraryPanel.setStatuses(statuses);
            libraryPanel.setTranscribers(songRepository.listUniqueTranscribers());
            libraryPanel.applyDefaultFilters();
            bandsPanel.reload();
            setlistsPanel.reload();
        } catch (LibraryException ex) {
            if (database != null) {
                database.close();
                database = null;
            }
            songRepository = null;
            settingsRepository = null;
            playerRepository = null;
            bandRepository = null;
            setlistRepository = null;
            songLayoutRepository = null;
            scanService = null;
            statuses = List.of();
            folderRules = List.of();
            accountTargets = List.of();
            libraryPanel.setSetlistRepository(null);
            libraryPanel.setTranscribers(List.of());
            libraryPanel.setSongs(List.of());
            statusBar.setMessage(ex.getMessage());
        }
    }

    private void refreshEntityCaches() throws LibraryException {
        if (songRepository == null) {
            return;
        }
        statuses = songRepository.listStatuses();
        folderRules = songRepository.listFolderRules();
        accountTargets = songRepository.listAccountTargets();
    }

    private void reloadLibrary(LibraryFilter filter) {
        if (songRepository == null) {
            return;
        }
        try {
            List<LibrarySong> songs = songRepository.listLibrarySongs(filter);
            libraryPanel.setSongs(songs);
            statusBar.setMessage(
                    songs.size() + " songs (filtered) — " + DataPaths.databasePath());
        } catch (LibraryException ex) {
            libraryPanel.setSongs(List.of());
            statusBar.setMessage(ex.getMessage());
        }
    }

    private void addLibrarySongToSetlist(LibrarySong song, SetlistInfo setlist) {
        if (song == null || setlist == null || setlistRepository == null) {
            return;
        }
        try {
            int position = setlistRepository.listItems(setlist.id()).size();
            setlistRepository.addItem(setlist.id(), song.id(), position, null, null);
            setlistsPanel.reload();
            reloadLibrary(libraryPanel.currentFilter());
            statusBar.setMessage("Added \"" + song.title() + "\" to " + setlist.name());
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(
                    this, ex.getMessage(), "Add to setlist", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runLibraryScan() {
        if (scanService == null || database == null) {
            JOptionPane.showMessageDialog(
                    this, "Database is not available.", "Scan library", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String lotroRoot = preferences.lotroRoot();
        if (lotroRoot == null || lotroRoot.isBlank()) {
            LotroPaths.ensureDefaultLotroRoot(preferences);
            lotroRoot = preferences.lotroRoot();
        }
        if (lotroRoot == null || lotroRoot.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Set LOTRO root in Settings before scanning.",
                    "Scan library",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path lotro = Paths.get(lotroRoot);
        Path setExport = preferences.setExportDir() == null || preferences.setExportDir().isBlank()
                ? null
                : Paths.get(preferences.setExportDir());
        ScanRequest request = new ScanRequest(lotro, setExport, preferences.defaultStatusId());
        ScanLibraryDialog dialog = new ScanLibraryDialog(
                this,
                scanService,
                request,
                () -> {
                    try {
                        if (songRepository != null) {
                            libraryPanel.setTranscribers(songRepository.listUniqueTranscribers());
                        }
                        libraryPanel.applyDefaultFilters();
                        statusBar.setMessage("Library scan finished.");
                    } catch (LibraryException ex) {
                        statusBar.setMessage(ex.getMessage());
                    }
                });
        dialog.setVisible(true);
    }

    private void openSettings() {
        LotroPaths.ensureDefaultLotroRoot(preferences);
        SettingsDialog dialog = new SettingsDialog(
                this,
                preferences,
                settingsRepository,
                statuses,
                folderRules,
                accountTargets,
                () -> {
                    preferences.clearWindowGeometry();
                    setSize(Preferences.DEFAULT_WINDOW_WIDTH, Preferences.DEFAULT_WINDOW_HEIGHT);
                    setLocationRelativeTo(null);
                },
                this::applySavedPreferences,
                this::onSettingsEntitiesChanged);
        dialog.setVisible(true);
    }

    private void onSettingsEntitiesChanged() {
        try {
            refreshEntityCaches();
            libraryPanel.setStatuses(statuses);
            libraryPanel.applyDefaultFilters();
            if (bandsPanel != null) {
                bandsPanel.reload();
            }
            if (setlistsPanel != null) {
                setlistsPanel.reload();
            }
            statusBar.setMessage("Settings entities updated.");
        } catch (LibraryException ex) {
            statusBar.setMessage(ex.getMessage());
        }
    }

    private void applySavedPreferences(Preferences updated) {
        preferences = updated;
        setlistsPanel.setPreferences(preferences);
        playbackPanel.updatePreferences(preferences);
        try {
            preferencesStore.save(preferences);
        } catch (PreferencesException ex) {
            statusBar.setMessage(ex.getMessage());
            return;
        }
        applyPreferencesToUi(true);
        libraryPanel.setDefaultFilters(preferences.defaultFilters());
        libraryPanel.applyDefaultFilters();
        statusBar.setMessage("Preferences saved.");
    }

    private void applyPreferencesToUi(boolean revalidateTree) {
        AbcmmThemer.setLookAndFeelQuietly(preferences.theme(), preferences.baseFontSize());
        if (preferences.splitterState() != null && preferences.splitterState().size() >= 2) {
            verticalSplit.setDividerLocation(preferences.splitterState().get(0));
        }
        if (revalidateTree) {
            SwingUtilities.updateComponentTreeUI(this);
        }
    }

    private boolean restoreWindowGeometry() {
        Map<String, Object> geometry = preferences.windowGeometry();
        if (geometry == null) {
            return false;
        }
        Integer width = asInt(geometry.get("width"));
        Integer height = asInt(geometry.get("height"));
        Integer x = asInt(geometry.get("x"));
        Integer y = asInt(geometry.get("y"));
        boolean restored = false;
        if (width != null && height != null) {
            setSize(width, height);
            restored = true;
        }
        if (x != null && y != null) {
            setLocation(x, y);
            restored = true;
        }
        Object maximized = geometry.get("maximized");
        if (Boolean.TRUE.equals(maximized)) {
            setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
            restored = true;
        }
        return restored;
    }

    private void persistWindowState() {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("x", getX());
        geometry.put("y", getY());
        geometry.put("width", getWidth());
        geometry.put("height", getHeight());
        geometry.put("maximized", (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH);
        preferences.setWindowGeometry(geometry);
        preferences.setSplitterState(List.of(
                verticalSplit.getDividerLocation(),
                Math.max(0, verticalSplit.getHeight() - verticalSplit.getDividerLocation())));
        preferences.extras().put("java_nav_section", navTabs.getSelectedIndex());
        preferences.extras().remove("java_nav_splitter");
        setlistsPanel.persistUiState(preferences);
        try {
            preferencesStore.save(preferences);
        } catch (PreferencesException ex) {
            statusBar.setMessage(ex.getMessage());
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
