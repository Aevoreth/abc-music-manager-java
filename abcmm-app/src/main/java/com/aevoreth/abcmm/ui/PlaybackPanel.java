package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PartInfo;
import com.aevoreth.abcmm.domain.playback.PlayQueueItem;
import com.aevoreth.abcmm.domain.playback.PlaybackEventType;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackPosition;
import com.aevoreth.abcmm.domain.playback.PlaybackSession;
import com.aevoreth.abcmm.domain.playback.PlaybackState;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Bottom playback bar mimicking ABC Player: scrubber, tempo, transport, parts/playlist, stereo, volume.
 */
public final class PlaybackPanel extends JPanel {

    /** Transport glyph size; ABC Player's play/stop PNGs are 24×24. */
    private static final int ICON_SIZE = 24;
    private static final int SCRUB_MAX = 1000;
    private static final int TEMPO_CENTER = 100;
    /** Magnetic zone around 100% so the slider lightly snaps to normal tempo. */
    private static final int TEMPO_SNAP_THRESHOLD = 3;
    /** Defer single-click stop so a double-click can panic instead (Python parity). */
    private static final int STOP_DOUBLE_CLICK_MS = 250;
    private static final int DEFAULT_STEREO = 50;
    /** Java-only extras key for the parts/playlist dialog size. */
    static final String LIST_SIZE_PREF_KEY = "java_playback_parts_playlist_size";
    private static final int DEFAULT_LIST_WIDTH = 640;
    private static final int DEFAULT_LIST_HEIGHT = 320;
    private static final int DEFAULT_PARTS_DIVIDER = 260;
    private static final int MIN_LIST_WIDTH = 320;
    private static final int MIN_LIST_HEIGHT = 160;
    private static final int COL_NOW = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_COMPOSER = 2;
    private static final int COL_DURATION = 3;
    private static final int COL_PARTS = 4;
    /** Maestro {@code ColorTable.PARTS_LIST_MUTE}. */
    private static final Color PARTS_MUTE_COLOR = Color.decode("#ff7777");
    /** Maestro {@code ColorTable.PARTS_LIST_SOLO}. */
    private static final Color PARTS_SOLO_COLOR = Color.decode("#7e7eff");

    private PlaybackSession session;
    private Preferences preferences;
    private Consumer<String> errorReporter = msg -> {
    };
    private Runnable prefsPersister = () -> {
    };
    private SetlistRepository setlistRepository;
    private BandRepository bandRepository;
    private Consumer<Long> setlistCreatedListener = id -> {
    };

    private final JLabel nowPlayingLabel = new JLabel("No song loaded");
    private final JSlider scrubber = new JSlider(0, SCRUB_MAX, 0);
    private final JLabel timeLabel = new JLabel("0:00 / 0:00");
    private final JSlider tempoSlider = new JSlider(50, 200, 100);
    private final JLabel tempoLabel = new JLabel("Tempo: 100%", SwingConstants.CENTER);
    private final JButton prevButton = new JButton(
            PlaybackIcons.previous(ICON_SIZE, PlaybackIcons.SKIP_COLOR));
    private final JButton playPauseButton = new JButton(PlaybackIcons.play(ICON_SIZE, PlaybackIcons.PLAY_COLOR));
    private final JButton stopButton = new JButton(PlaybackIcons.stop(ICON_SIZE, PlaybackIcons.STOP_COLOR));
    private final JButton nextButton = new JButton(PlaybackIcons.next(ICON_SIZE, PlaybackIcons.SKIP_COLOR));
    private final JToggleButton listButton = new JToggleButton(PlaybackIcons.list(ICON_SIZE));
    private final JSlider stereoSlider = new JSlider(0, 100, DEFAULT_STEREO);
    private final JLabel stereoLabel = new JLabel("Stereo: 50", SwingConstants.CENTER);
    private final JSlider volumeSlider = new JSlider(0, 100, 100);
    private final JLabel volumeLabel = new JLabel("Volume: 100", SwingConstants.CENTER);

    private final JPanel partsPanel = new JPanel();
    private final PlaylistTableModel playlistModel = new PlaylistTableModel();
    private final JTable playlistTable = new JTable(playlistModel);
    private final JButton moveUpButton = new JButton("Move up");
    private final JButton moveDownButton = new JButton("Move down");
    private final JButton saveAsSetlistButton = new JButton("Save as setlist…");
    private JDialog listDialog;
    private JSplitPane listSplit;
    private final Timer listSizePersistTimer;
    private int lastPlaylistCurrentIndex = Integer.MIN_VALUE;

    private boolean scrubbing;
    private boolean suppressTempo;
    private boolean suppressStereo;
    private boolean suppressVolume;
    private boolean suppressListSizePersist;
    private final Timer positionTimer;
    private final Timer stopClickTimer;

    public PlaybackPanel() {
        super(new BorderLayout(6, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(120, 120, 120, 80)),
                new EmptyBorder(6, 10, 8, 10)));

        nowPlayingLabel.setFont(nowPlayingLabel.getFont().deriveFont(Font.BOLD));

        scrubber.setPaintTicks(false);
        scrubber.setFocusable(false);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeLabel.setPreferredSize(new Dimension(110, timeLabel.getPreferredSize().height));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(scrubber, BorderLayout.CENTER);
        top.add(timeLabel, BorderLayout.EAST);

        styleTransportButton(prevButton, "Previous");
        styleTransportButton(playPauseButton, "Play / Pause");
        styleTransportButton(stopButton, "Stop (double-click for MIDI panic)");
        styleTransportButton(nextButton, "Next");
        styleTransportButton(listButton, "Show / hide parts and playlist");

        tempoSlider.setPreferredSize(new Dimension(110, tempoSlider.getPreferredSize().height));
        tempoSlider.setToolTipText("Tempo");
        JPanel tempoPanel = new JPanel(new BorderLayout());
        tempoPanel.add(tempoLabel, BorderLayout.NORTH);
        tempoPanel.add(tempoSlider, BorderLayout.CENTER);
        tempoPanel.setPreferredSize(new Dimension(130, 42));

        volumeSlider.setPreferredSize(new Dimension(110, volumeSlider.getPreferredSize().height));
        volumeSlider.setToolTipText("Volume");
        JPanel volumePanel = new JPanel(new BorderLayout());
        volumePanel.add(volumeLabel, BorderLayout.NORTH);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);
        volumePanel.setPreferredSize(new Dimension(120, 42));

        stereoSlider.setPreferredSize(new Dimension(110, stereoSlider.getPreferredSize().height));
        stereoSlider.setToolTipText("Stereo width (left = mono, right = full stereo)");
        JPanel stereoPanel = new JPanel(new BorderLayout());
        stereoPanel.add(stereoLabel, BorderLayout.NORTH);
        stereoPanel.add(stereoSlider, BorderLayout.CENTER);
        stereoPanel.setPreferredSize(new Dimension(120, 42));

        JPanel rightSliders = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightSliders.add(stereoPanel);
        rightSliders.add(volumePanel);

        JPanel transport = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        transport.add(prevButton);
        transport.add(playPauseButton);
        transport.add(stopButton);
        transport.add(nextButton);
        transport.add(Box.createHorizontalStrut(12));
        transport.add(listButton);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(tempoPanel, BorderLayout.WEST);
        bottom.add(transport, BorderLayout.CENTER);
        bottom.add(rightSliders, BorderLayout.EAST);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        nowPlayingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(nowPlayingLabel);
        center.add(Box.createVerticalStrut(2));
        center.add(top);
        center.add(Box.createVerticalStrut(4));
        center.add(bottom);
        add(center, BorderLayout.CENTER);

        stopClickTimer = new Timer(STOP_DOUBLE_CLICK_MS, e -> runSafe(() -> session.engine().stop()));
        stopClickTimer.setRepeats(false);

        buildListDialogContent();
        wireControls();

        listSizePersistTimer = new Timer(400, e -> persistListSize());
        listSizePersistTimer.setRepeats(false);

        positionTimer = new Timer(100, e -> refreshPosition());
        positionTimer.setRepeats(true);
        setTransportEnabled(false);
    }

    public void bind(PlaybackSession session, Preferences preferences,
            Consumer<String> errorReporter, Runnable prefsPersister) {
        this.session = Objects.requireNonNull(session, "session");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.errorReporter = errorReporter == null ? msg -> {
        } : errorReporter;
        this.prefsPersister = prefsPersister == null ? () -> {
        } : prefsPersister;

        AbcPlaybackEngine engine = session.engine();
        applyPrefsToControls();
        try {
            engine.setVolume(volumeSlider.getValue() / 100.0);
            engine.setStereo(stereoSlider.getValue());
            engine.setTempoFactor(tempoSlider.getValue() / 100.0f);
        } catch (PlaybackException ignored) {
            // engine may not be ready
        }

        engine.addPlaybackListener(event -> SwingUtilities.invokeLater(() -> {
            if (event.type() == PlaybackEventType.SONG_LOADED
                    || event.type() == PlaybackEventType.STATE_CHANGED) {
                refreshFromEngine();
            } else if (event.type() == PlaybackEventType.POSITION_CHANGED) {
                refreshPosition();
            } else if (event.type() == PlaybackEventType.TEMPO_CHANGED) {
                syncTempoFromEngine();
            }
        }));
        session.addSessionListener(() -> SwingUtilities.invokeLater(this::refreshPlaylistUi));
        refreshFromEngine();
        refreshPlaylistUi();
        positionTimer.start();
    }

    /**
     * Enables saving the current queue as a setlist. Pass nulls when the database is unavailable.
     */
    public void setSetlistSaveSupport(
            SetlistRepository setlists,
            BandRepository bands,
            Consumer<Long> onCreated) {
        this.setlistRepository = setlists;
        this.bandRepository = bands;
        this.setlistCreatedListener = onCreated == null ? id -> {
        } : onCreated;
        updatePlaylistActionButtons();
    }

    /** Stop UI polling so AWT can shut down after the frame is disposed. */
    public void stopTimers() {
        positionTimer.stop();
        listSizePersistTimer.stop();
        hideListDialog();
        if (listDialog != null) {
            listDialog.dispose();
            listDialog = null;
        }
    }

    public void updatePreferences(Preferences preferences) {
        this.preferences = preferences;
        applyPrefsToControls();
        applyListSizeFromPrefs();
        if (session != null) {
            try {
                session.engine().setVolume(volumeSlider.getValue() / 100.0);
                session.engine().setStereo(stereoSlider.getValue());
                session.engine().setTempoFactor(tempoSlider.getValue() / 100.0f);
            } catch (PlaybackException ignored) {
                // engine may not be ready
            }
        }
    }

    /** Persist parts/playlist dialog size into preferences extras. */
    public void persistUiState(Preferences preferences) {
        if (preferences == null) {
            return;
        }
        this.preferences = preferences;
        persistListSize();
    }

    private void buildListDialogContent() {
        partsPanel.setLayout(new BoxLayout(partsPanel, BoxLayout.Y_AXIS));
        partsPanel.setBorder(BorderFactory.createTitledBorder("Parts"));

        playlistTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistTable.setFillsViewportHeight(true);
        playlistTable.setRowHeight(Math.max(22, playlistTable.getRowHeight()));
        playlistTable.setAutoCreateRowSorter(false);
        playlistTable.getTableHeader().setReorderingAllowed(false);
        playlistTable.setToolTipText("Drag to rearrange. Double-click to play.");
        playlistTable.getColumnModel().getColumn(COL_NOW).setPreferredWidth(28);
        playlistTable.getColumnModel().getColumn(COL_NOW).setMaxWidth(36);
        playlistTable.getColumnModel().getColumn(COL_TITLE).setPreferredWidth(160);
        playlistTable.getColumnModel().getColumn(COL_COMPOSER).setPreferredWidth(120);
        playlistTable.getColumnModel().getColumn(COL_DURATION).setPreferredWidth(56);
        playlistTable.getColumnModel().getColumn(COL_DURATION).setMaxWidth(72);
        playlistTable.getColumnModel().getColumn(COL_PARTS).setPreferredWidth(44);
        playlistTable.getColumnModel().getColumn(COL_PARTS).setMaxWidth(56);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        playlistTable.getColumnModel().getColumn(COL_NOW).setCellRenderer(center);
        playlistTable.getColumnModel().getColumn(COL_DURATION).setCellRenderer(center);
        playlistTable.getColumnModel().getColumn(COL_PARTS).setCellRenderer(center);
        playlistTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && session != null) {
                    int row = playlistTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        runSafe(() -> session.playAt(row));
                    }
                }
            }
        });
        playlistTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updatePlaylistActionButtons();
            }
        });
        enablePlaylistReorder();
        JScrollPane playlistScroll = new JScrollPane(playlistTable);
        playlistScroll.setBorder(BorderFactory.createTitledBorder("Playlist"));
        playlistScroll.setPreferredSize(new Dimension(360, 220));

        moveUpButton.setToolTipText("Move the selected song earlier in the queue");
        moveDownButton.setToolTipText("Move the selected song later in the queue");
        saveAsSetlistButton.setToolTipText("Create a setlist from the current queue order");
        moveUpButton.addActionListener(e -> moveSelected(-1));
        moveDownButton.addActionListener(e -> moveSelected(1));
        saveAsSetlistButton.addActionListener(e -> saveQueueAsSetlist());
        JPanel playlistToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        playlistToolbar.add(moveUpButton);
        playlistToolbar.add(moveDownButton);
        playlistToolbar.add(saveAsSetlistButton);
        updatePlaylistActionButtons();

        JPanel playlistColumn = new JPanel(new BorderLayout());
        playlistColumn.add(playlistScroll, BorderLayout.CENTER);
        playlistColumn.add(playlistToolbar, BorderLayout.SOUTH);

        JScrollPane partsScroll = new JScrollPane(partsPanel);
        partsScroll.setPreferredSize(new Dimension(DEFAULT_PARTS_DIVIDER, 220));

        listSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, partsScroll, playlistColumn);
        listSplit.setResizeWeight(0.4);
        listSplit.setContinuousLayout(true);
        listSplit.setBorder(new EmptyBorder(8, 8, 8, 8));
        listSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!suppressListSizePersist) {
                listSizePersistTimer.restart();
            }
        });
    }

    private void ensureListDialog() {
        if (listDialog != null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        listDialog = owner instanceof Frame frame
                ? new JDialog(frame, "Parts / Playlist", false)
                : new JDialog((Frame) null, "Parts / Playlist", false);
        listDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        listDialog.setResizable(true);
        listDialog.setMinimumSize(new Dimension(MIN_LIST_WIDTH, MIN_LIST_HEIGHT));
        listDialog.setContentPane(listSplit);
        listDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hideListDialog();
            }
        });
        listDialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!suppressListSizePersist && listDialog.isVisible()) {
                    listSizePersistTimer.restart();
                }
            }
        });
        applyListSizeFromPrefs();
    }

    private void wireControls() {
        scrubber.addChangeListener(e -> {
            if (!scrubber.getValueIsAdjusting()) {
                if (scrubbing && session != null) {
                    PlaybackPosition pos = session.engine().getPosition();
                    long totalMs = pos.duration().toMillis();
                    if (totalMs > 0) {
                        long ms = scrubber.getValue() * totalMs / SCRUB_MAX;
                        runSafe(() -> session.engine().seek(Duration.ofMillis(ms)));
                    }
                }
                scrubbing = false;
            } else {
                scrubbing = true;
            }
        });

        prevButton.addActionListener(e -> runSafe(() -> session.previous()));
        nextButton.addActionListener(e -> runSafe(() -> session.next()));
        stopButton.addActionListener(e -> onStopClicked());
        stopButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    onStopDoubleClicked();
                }
            }
        });
        playPauseButton.addActionListener(e -> runSafe(this::togglePlayPause));
        listButton.addActionListener(e -> {
            if (listButton.isSelected()) {
                showListDialog();
            } else {
                hideListDialog();
            }
        });

        tempoSlider.addChangeListener(tempoListener());
        stereoSlider.addChangeListener(stereoListener());
        volumeSlider.addChangeListener(volumeListener());
    }

    /**
     * Single-click defers stop so a double-click can cancel it and panic instead.
     * Swing fires the button action on mouse-release, then {@code mouseClicked}.
     */
    private void onStopClicked() {
        stopClickTimer.restart();
    }

    private void onStopDoubleClicked() {
        stopClickTimer.stop();
        runSafe(() -> session.engine().panic());
    }

    private ChangeListener tempoListener() {
        return e -> {
            if (suppressTempo || session == null) {
                return;
            }
            int pct = tempoSlider.getValue();
            if (Math.abs(pct - TEMPO_CENTER) <= TEMPO_SNAP_THRESHOLD && pct != TEMPO_CENTER) {
                suppressTempo = true;
                try {
                    tempoSlider.setValue(TEMPO_CENTER);
                    pct = TEMPO_CENTER;
                } finally {
                    suppressTempo = false;
                }
            }
            tempoLabel.setText("Tempo: " + pct + "%");
            if (!tempoSlider.getValueIsAdjusting()) {
                int applied = pct;
                runSafe(() -> {
                    session.engine().setTempoFactor(applied / 100.0f);
                    if (preferences != null) {
                        preferences.setPlaybackTempo(applied / 100.0);
                        prefsPersister.run();
                    }
                });
            }
        };
    }

    private ChangeListener stereoListener() {
        return e -> {
            if (suppressStereo || session == null) {
                return;
            }
            int pct = stereoSlider.getValue();
            stereoLabel.setText("Stereo: " + pct);
            if (!stereoSlider.getValueIsAdjusting()) {
                runSafe(() -> {
                    session.engine().setStereo(pct);
                    if (preferences != null) {
                        preferences.setPlaybackStereoSlider(pct);
                        prefsPersister.run();
                    }
                });
            }
        };
    }

    private ChangeListener volumeListener() {
        return e -> {
            if (suppressVolume || session == null) {
                return;
            }
            int pct = volumeSlider.getValue();
            volumeLabel.setText("Volume: " + pct);
            if (!volumeSlider.getValueIsAdjusting()) {
                runSafe(() -> {
                    session.engine().setVolume(pct / 100.0);
                    if (preferences != null) {
                        preferences.setPlaybackVolume((double) pct);
                        prefsPersister.run();
                    }
                });
            }
        };
    }

    private void togglePlayPause() throws PlaybackException {
        if (session == null) {
            return;
        }
        AbcPlaybackEngine engine = session.engine();
        if (engine.getState() == PlaybackState.PLAYING) {
            engine.pause();
        } else if (engine.getLoadedSong() != null) {
            engine.play();
        }
    }

    private void showListDialog() {
        ensureListDialog();
        rebuildPartsPanel();
        refreshPlaylistUi();
        applyListSizeFromPrefs();
        positionListDialog();
        listDialog.setVisible(true);
        listButton.setSelected(true);
    }

    private void hideListDialog() {
        if (listDialog != null && listDialog.isVisible()) {
            persistListSize();
            listDialog.setVisible(false);
        }
        listButton.setSelected(false);
    }

    private void positionListDialog() {
        if (listDialog == null || !listButton.isShowing()) {
            return;
        }
        Point loc = listButton.getLocationOnScreen();
        int width = listDialog.getWidth();
        int height = listDialog.getHeight();
        if (width <= 0 || height <= 0) {
            Dimension preferred = listDialog.getPreferredSize();
            width = preferred.width;
            height = preferred.height;
        }
        int x = loc.x + listButton.getWidth() / 2 - width / 2;
        int y = loc.y - height - 8;
        if (y < 0) {
            y = loc.y + listButton.getHeight() + 8;
        }
        listDialog.setLocation(Math.max(0, x), Math.max(0, y));
    }

    private void applyListSizeFromPrefs() {
        if (listDialog == null) {
            return;
        }
        int width = DEFAULT_LIST_WIDTH;
        int height = DEFAULT_LIST_HEIGHT;
        int divider = DEFAULT_PARTS_DIVIDER;
        if (preferences != null) {
            Object raw = preferences.extras().get(LIST_SIZE_PREF_KEY);
            if (raw instanceof Map<?, ?> map) {
                Integer w = asInt(map.get("width"));
                Integer h = asInt(map.get("height"));
                Integer d = asInt(map.get("divider"));
                if (w != null) {
                    width = Math.max(MIN_LIST_WIDTH, w);
                }
                if (h != null) {
                    height = Math.max(MIN_LIST_HEIGHT, h);
                }
                if (d != null) {
                    divider = Math.max(80, d);
                }
            }
        }
        suppressListSizePersist = true;
        try {
            listDialog.setSize(width, height);
            listSplit.setDividerLocation(Math.min(divider, Math.max(80, width - 120)));
        } finally {
            suppressListSizePersist = false;
        }
    }

    private void persistListSize() {
        if (preferences == null || listDialog == null) {
            return;
        }
        int width = listDialog.getWidth();
        int height = listDialog.getHeight();
        if (width < MIN_LIST_WIDTH || height < MIN_LIST_HEIGHT) {
            return;
        }
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("width", width);
        size.put("height", height);
        size.put("divider", listSplit.getDividerLocation());
        preferences.extras().put(LIST_SIZE_PREF_KEY, size);
        prefsPersister.run();
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

    private void rebuildPartsPanel() {
        partsPanel.removeAll();
        if (session == null) {
            partsPanel.revalidate();
            return;
        }
        LoadedSong song = session.engine().getLoadedSong();
        if (song == null || song.parts().isEmpty()) {
            partsPanel.add(new JLabel("No parts"));
            partsPanel.revalidate();
            return;
        }
        for (PartInfo part : song.parts()) {
            JPanel row = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(1, 2, 1, 2);
            c.gridy = 0;

            String label = part.number() + ". " + part.name()
                    + (part.instrument().isBlank() ? "" : " (" + part.instrument() + ")");
            JLabel name = new JLabel(label);
            name.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

            boolean soloed = session.engine().isPartSolo(part.index());
            boolean muted = session.engine().isPartMuted(part.index());
            int buttonSide = name.getPreferredSize().height + 4;
            Dimension buttonSize = new Dimension(buttonSide, buttonSide);

            // Maestro PartsListItem order: title | S | M
            JButton solo = createMuteSoloButton(
                    soloed, "S", PARTS_SOLO_COLOR, buttonSize,
                    "Solo / unsolo part (Shift+click: unsolo all)");
            solo.addActionListener(e -> runSafe(() -> {
                boolean next = !session.engine().isPartSolo(part.index());
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
                    clearAllPartSolos(song);
                }
                session.engine().setPartSolo(part.index(), next);
            }));

            JButton mute = createMuteSoloButton(
                    muted, "M", PARTS_MUTE_COLOR, buttonSize,
                    "Mute / unmute part (Shift+click: unmute all)");
            mute.addActionListener(e -> runSafe(() -> {
                boolean next = !session.engine().isPartMuted(part.index());
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
                    clearAllPartMutes(song);
                }
                session.engine().setPartMuted(part.index(), next);
            }));

            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            row.add(name, c);
            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            row.add(solo, c);
            c.gridx = 2;
            row.add(mute, c);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            partsPanel.add(row);
        }
        partsPanel.revalidate();
        partsPanel.repaint();
    }

    /** Square M/S control matching Maestro's parts-list mute/solo buttons. */
    private static JButton createMuteSoloButton(
            boolean active, String letter, Color activeColor, Dimension size, String tip) {
        JButton button = new JButton(active
                ? "<html><b>" + letter + "</b></html>"
                : "<html>" + letter + "</html>");
        button.setToolTipText(tip);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        applyMuteSoloColor(button, active, activeColor);
        return button;
    }

    private static void applyMuteSoloColor(JButton button, boolean active, Color activeColor) {
        if (active) {
            String hex = String.format("#%02x%02x%02x",
                    activeColor.getRed(), activeColor.getGreen(), activeColor.getBlue());
            button.putClientProperty("FlatLaf.style",
                    "background: " + hex
                            + "; focusedBackground: " + hex
                            + "; hoverBackground: " + hex
                            + "; pressedBackground: " + hex);
            button.setBackground(activeColor);
        } else {
            button.putClientProperty("FlatLaf.style", null);
            Color fallback = UIManager.getColor("Button.background");
            button.setBackground(fallback != null ? fallback : new JButton().getBackground());
        }
    }

    private void clearAllPartSolos(LoadedSong song) throws PlaybackException {
        for (PartInfo part : song.parts()) {
            if (session.engine().isPartSolo(part.index())) {
                session.engine().setPartSolo(part.index(), false);
            }
        }
    }

    private void clearAllPartMutes(LoadedSong song) throws PlaybackException {
        for (PartInfo part : song.parts()) {
            if (session.engine().isPartMuted(part.index())) {
                session.engine().setPartMuted(part.index(), false);
            }
        }
    }

    private void refreshFromEngine() {
        if (session == null) {
            return;
        }
        AbcPlaybackEngine engine = session.engine();
        LoadedSong song = engine.getLoadedSong();
        if (song == null) {
            nowPlayingLabel.setText("No song loaded");
            setTransportEnabled(false);
            timeLabel.setText("0:00 / 0:00");
            scrubber.setValue(0);
            return;
        }
        nowPlayingLabel.setText(song.title() + (song.composer().isBlank() ? "" : " — " + song.composer()));
        setTransportEnabled(true);
        boolean playing = engine.getState() == PlaybackState.PLAYING;
        playPauseButton.setIcon(playing
                ? PlaybackIcons.pause(ICON_SIZE, PlaybackIcons.PAUSE_COLOR)
                : PlaybackIcons.play(ICON_SIZE, PlaybackIcons.PLAY_COLOR));
        playPauseButton.setToolTipText(playing ? "Pause" : "Play");
        prevButton.setEnabled(session.hasPrevious());
        nextButton.setEnabled(session.hasNext());
        refreshPosition();
        if (listDialog != null && listDialog.isVisible()) {
            rebuildPartsPanel();
        }
    }

    private void refreshPosition() {
        if (session == null || scrubbing) {
            return;
        }
        PlaybackPosition pos = session.engine().getPosition();
        long curMs = pos.position().toMillis();
        long totalMs = pos.duration().toMillis();
        timeLabel.setText(formatTime(curMs) + " / " + formatTime(totalMs));
        if (totalMs > 0) {
            int value = (int) Math.min(SCRUB_MAX, curMs * SCRUB_MAX / totalMs);
            if (Math.abs(scrubber.getValue() - value) > 1) {
                scrubber.setValue(value);
            }
        } else {
            scrubber.setValue(0);
        }
    }

    private void refreshPlaylistUi() {
        if (session == null) {
            return;
        }
        int previousCurrent = lastPlaylistCurrentIndex;
        int[] selected = playlistTable.getSelectedRows();
        playlistModel.setItems(session.queue(), session.currentIndex());
        int current = session.currentIndex();
        lastPlaylistCurrentIndex = current;
        if (current != previousCurrent) {
            if (current >= 0 && current < playlistModel.getRowCount()) {
                playlistTable.getSelectionModel().setSelectionInterval(current, current);
                playlistTable.scrollRectToVisible(
                        playlistTable.getCellRect(current, 0, true));
            } else {
                playlistTable.clearSelection();
            }
        } else if (selected.length > 0 && playlistModel.getRowCount() > 0) {
            int row = Math.min(selected[0], playlistModel.getRowCount() - 1);
            if (row >= 0) {
                playlistTable.getSelectionModel().setSelectionInterval(row, row);
            }
        }
        prevButton.setEnabled(session.hasPrevious());
        nextButton.setEnabled(session.hasNext());
        updatePlaylistActionButtons();
    }

    private void enablePlaylistReorder() {
        playlistTable.setDragEnabled(true);
        playlistTable.setDropMode(DropMode.INSERT_ROWS);
        playlistTable.setTransferHandler(new TransferHandler() {
            private int dragRow = -1;

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                dragRow = playlistTable.getSelectedRow();
                if (dragRow < 0) {
                    return null;
                }
                return new StringSelection(Integer.toString(dragRow));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        && dragRow >= 0
                        && session != null;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support) || !(support.getDropLocation() instanceof JTable.DropLocation drop)) {
                    return false;
                }
                int dropIndex = drop.getRow();
                if (dropIndex < 0) {
                    dropIndex = playlistModel.getRowCount();
                }
                int from = dragRow;
                dragRow = -1;
                int newIndex = session.moveItem(from, dropIndex);
                if (newIndex >= 0 && newIndex < playlistModel.getRowCount()) {
                    playlistTable.getSelectionModel().setSelectionInterval(newIndex, newIndex);
                    playlistTable.scrollRectToVisible(playlistTable.getCellRect(newIndex, 0, true));
                }
                updatePlaylistActionButtons();
                return true;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                dragRow = -1;
            }
        });
    }

    private void moveSelected(int direction) {
        if (session == null) {
            return;
        }
        int row = playlistTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int dropIndex = direction < 0 ? row - 1 : row + 2;
        if (dropIndex < 0 || dropIndex > playlistModel.getRowCount()) {
            return;
        }
        int newIndex = session.moveItem(row, dropIndex);
        if (newIndex >= 0 && newIndex < playlistModel.getRowCount()) {
            playlistTable.getSelectionModel().setSelectionInterval(newIndex, newIndex);
            playlistTable.scrollRectToVisible(playlistTable.getCellRect(newIndex, 0, true));
        }
        updatePlaylistActionButtons();
    }

    private void saveQueueAsSetlist() {
        if (session == null || setlistRepository == null) {
            return;
        }
        List<PlayQueueItem> queue = session.queue();
        if (queue.isEmpty()) {
            JOptionPane.showMessageDialog(
                    listDialog != null ? listDialog : this,
                    "The playlist is empty.",
                    "Save as setlist",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Window owner = listDialog != null ? listDialog : SwingUtilities.getWindowAncestor(this);
        SetlistDetailsDialog.showCreate(owner, bandRepository).ifPresent(details -> {
            try {
                long setlistId = setlistRepository.addSetlist(details.name(), null);
                setlistRepository.updateSetlist(
                        setlistId,
                        details.name(),
                        details.bandLayoutId(),
                        null,
                        0,
                        details.locked(),
                        details.switchDelaySeconds(),
                        details.notes(),
                        details.setDate(),
                        details.setTime(),
                        details.targetDurationSeconds());
                for (int i = 0; i < queue.size(); i++) {
                    setlistRepository.addItem(setlistId, queue.get(i).songId(), i, null, null);
                }
                errorReporter.accept("Saved queue as setlist \"" + details.name() + "\"");
                setlistCreatedListener.accept(setlistId);
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(
                        owner,
                        ex.getMessage(),
                        "Save as setlist",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void updatePlaylistActionButtons() {
        int rows = playlistModel.getRowCount();
        int selected = playlistTable.getSelectedRow();
        moveUpButton.setEnabled(selected > 0);
        moveDownButton.setEnabled(selected >= 0 && selected < rows - 1);
        saveAsSetlistButton.setEnabled(rows > 0 && setlistRepository != null);
    }

    private void syncTempoFromEngine() {
        if (session == null) {
            return;
        }
        int pct = Math.round(session.engine().getTempoFactor() * 100);
        suppressTempo = true;
        try {
            tempoSlider.setValue(pct);
            tempoLabel.setText("Tempo: " + pct + "%");
        } finally {
            suppressTempo = false;
        }
    }

    private void applyPrefsToControls() {
        if (preferences == null) {
            return;
        }
        suppressVolume = true;
        suppressStereo = true;
        suppressTempo = true;
        try {
            int vol = preferences.playbackVolume() == null
                    ? 100
                    : (int) Math.round(preferences.playbackVolume());
            volumeSlider.setValue(Math.max(0, Math.min(100, vol)));
            volumeLabel.setText("Volume: " + volumeSlider.getValue());
            int stereo = preferences.playbackStereoSlider() == null
                    ? DEFAULT_STEREO
                    : preferences.playbackStereoSlider();
            stereoSlider.setValue(Math.max(0, Math.min(100, stereo)));
            stereoLabel.setText("Stereo: " + stereoSlider.getValue());
            int tempo = preferences.playbackTempo() == null
                    ? 100
                    : (int) Math.round(preferences.playbackTempo() * 100);
            tempoSlider.setValue(Math.max(50, Math.min(200, tempo)));
            tempoLabel.setText("Tempo: " + tempoSlider.getValue() + "%");
        } finally {
            suppressVolume = false;
            suppressStereo = false;
            suppressTempo = false;
        }
    }

    private void setTransportEnabled(boolean enabled) {
        scrubber.setEnabled(enabled);
        playPauseButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        listButton.setEnabled(true);
        if (!enabled) {
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
        }
    }

    private void styleTransportButton(AbstractButton button, String tip) {
        button.setToolTipText(tip);
        button.setFocusable(false);
        // Match ABC Player play-control margins (Insets(5, 20, 5, 20)).
        button.setMargin(new Insets(5, 20, 5, 20));
    }

    private void runSafe(ThrowingAction action) {
        if (session == null) {
            return;
        }
        try {
            action.run();
            refreshFromEngine();
            refreshPlaylistUi();
        } catch (PlaybackException ex) {
            errorReporter.accept(ex.getMessage());
        }
    }

    private static String formatTime(long millis) {
        long totalSec = Math.max(0, millis / 1000);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private static String formatDurationSeconds(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "—";
        }
        return formatTime(seconds * 1000L);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws PlaybackException;
    }

    private static final class PlaylistTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"", "Title", "Composer", "Dur", "Parts"};

        private final List<PlayQueueItem> items = new ArrayList<>();
        private int currentIndex = -1;

        void setItems(List<PlayQueueItem> queue, int currentIndex) {
            items.clear();
            if (queue != null) {
                items.addAll(queue);
            }
            this.currentIndex = currentIndex;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PlayQueueItem item = items.get(rowIndex);
            return switch (columnIndex) {
                case COL_NOW -> rowIndex == currentIndex ? "▶" : "";
                case COL_TITLE -> item.title();
                case COL_COMPOSER -> item.composers();
                case COL_DURATION -> formatDurationSeconds(item.durationSeconds());
                case COL_PARTS -> item.partCount() > 0 ? Integer.toString(item.partCount()) : "—";
                default -> "";
            };
        }
    }
}
