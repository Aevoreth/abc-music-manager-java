package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.export.AbcpException;
import com.aevoreth.abcmm.domain.export.AbcpReader;
import com.aevoreth.abcmm.domain.export.SetExportException;
import com.aevoreth.abcmm.domain.export.SetExportItemInfo;
import com.aevoreth.abcmm.domain.export.SetExportService;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.playback.PlayQueueItem;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackSession;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.prefs.PreferencesStore;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Setlist folders, metadata editor, song order, and per-item part assignments.
 */
public final class SetlistsPanel extends JPanel {

    static final String COLUMN_WIDTHS_PREF_KEY = "java_setlist_song_column_widths";
    /** Shared with Python edition preferences key. */
    static final String META_SPLIT_PREF_KEY = "setlists_top_split_state";
    /** #, Play, Warning stay fixed; Title / Parts / Duration / Composer are flexible. */
    private static final int FIXED_COLUMN_COUNT = 3;
    private static final int COL_INDEX = 0;
    private static final int COL_PLAY = 1;
    private static final int COL_WARNING = 2;
    private static final int COL_TITLE = 3;
    private static final int COL_PARTS = 4;
    private static final int COL_DURATION = 5;
    private static final int COL_COMPOSER = 6;
    private static final int DEFAULT_TITLE_WIDTH = 220;
    private static final int DEFAULT_COMPOSER_WIDTH = 160;
    private static final int HEADER_PAD = 16;
    private static final Color WARNING_RED = new Color(0xFF4444);
    private static final int MAIN_SPLIT_INITIAL = 200;
    private static final int MAIN_SPLIT_MIN_LEFT = 100;
    private static final int META_SPLIT_DEFAULT = 360;
    private static final int META_SPLIT_MIN = 240;
    private static final String DURATION_VALUE_MIN_SAMPLE = "00:00:00 (With Delays)";

    private PlayerRepository playerRepository;
    private BandRepository bandRepository;
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private SongLayoutRepository songLayoutRepository;
    private Preferences preferences;
    private PreferencesStore preferencesStore;
    private PlaybackSession playbackSession;
    private Consumer<String> playbackErrorReporter = msg -> {
    };

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Setlists");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);
    private final JButton exportButton = new JButton("Export");

    private final JLabel nameValue = new JLabel("\u2014");
    private final JButton editDetailsButton = new JButton("Edit");
    private final JButton exportDetailsButton = new JButton("Export");
    private final JButton deleteSetlistButton = new JButton(PlaybackIcons.trash(14));
    private final JLabel layoutValue = new JLabel("\u2014");
    private final JLabel dateTimeValue = new JLabel("\u2014");
    private final JLabel targetDurationValue = new JLabel("\u2014");
    private final JLabel switchDelayValue = new JLabel("\u2014");
    private final DefaultTableModel durationSummaryModel = new DefaultTableModel(
            new Object[][] {
                {"Raw Duration", "\u2014"},
                {"Actual Duration", "\u2014"},
                {"Time remaining", "\u2014"}
            },
            new Object[] {"Metric", "Value"}) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable durationSummaryTable = new JTable(durationSummaryModel);
    private final JTextArea notesArea = new JTextArea(4, 20);
    private final JPanel notesPanel = new JPanel();
    private final JLabel lockedValue = new JLabel("Locked");

    private final ItemTableModel itemModel = new ItemTableModel();
    private final JTable itemTable = new JTable(itemModel);

    private final SetlistBandAssignmentPanel assignmentPanel = new SetlistBandAssignmentPanel();

    private final JPanel editorPanel = new JPanel(new BorderLayout(8, 8));
    private final JPanel metaPanel = new JPanel();
    private final JPanel songsPanel = new JPanel(new BorderLayout(4, 4));
    private final JButton addSongButton = new JButton("Add song");
    private final JButton removeSongButton = new JButton("Remove");
    private final JButton moveUpButton = new JButton("Move up");
    private final JButton moveDownButton = new JButton("Move down");

    private JSplitPane mainSplit;
    private JSplitPane topSplit;
    private boolean suppressSelection;
    private boolean columnWidthsRestored;
    private boolean mainSplitInitialized;
    private boolean metaSplitRestored;

    public SetlistsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new SetlistTreeCellRenderer());
        tree.addTreeSelectionListener(e -> {
            if (!suppressSelection) {
                onTreeSelection();
            }
        });
        enableTreeReorder();
        enableTreeContextMenu();

        itemTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        itemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        itemTable.setFillsViewportHeight(true);
        itemTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadAssignments();
            }
        });
        installItemTableColumnModel();
        applyDefaultColumnWidths();
        enableItemTableReorder();
        enableItemTablePlaybackActions();
        enableItemTableWarningColumn();
        TableColumn indexColumn = columnByModelIndex(COL_INDEX);
        if (indexColumn != null) {
            DefaultTableCellRenderer indexRenderer = new DefaultTableCellRenderer();
            indexRenderer.setHorizontalAlignment(JLabel.CENTER);
            indexColumn.setCellRenderer(indexRenderer);
        }

        assignmentPanel.setAssignmentChangedHandler(this::reloadAssignments);

        JPanel left = buildLeftPane();
        buildEditorPane();

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorPanel);
        mainSplit.setResizeWeight(0.22);
        mainSplit.setContinuousLayout(true);
        mainSplit.setDividerLocation(MAIN_SPLIT_INITIAL);
        add(mainSplit, BorderLayout.CENTER);

        clearEditor();
        setEditorEnabled(false);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!mainSplitInitialized && mainSplit.getWidth() > 0) {
                    mainSplit.setDividerLocation(MAIN_SPLIT_INITIAL);
                    mainSplitInitialized = true;
                }
                if (!metaSplitRestored && topSplit != null && topSplit.getWidth() > 0) {
                    restoreMetaSplit();
                    metaSplitRestored = true;
                }
                if (!columnWidthsRestored && preferences != null) {
                    restoreColumnWidths();
                    columnWidthsRestored = true;
                }
            }
        });
    }

    public void bind(
            PlayerRepository players,
            BandRepository bands,
            SetlistRepository setlists,
            SongRepository songs,
            SongLayoutRepository songLayouts) {
        this.playerRepository = players;
        this.bandRepository = bands;
        this.setlistRepository = setlists;
        this.songRepository = songs;
        this.songLayoutRepository = songLayouts;
        assignmentPanel.bind(bands, players, setlists, songLayouts);
        reload();
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
        if (isShowing()) {
            restoreColumnWidths();
            columnWidthsRestored = true;
            restoreMetaSplit();
            metaSplitRestored = true;
        }
    }

    public void setPreferencesStore(PreferencesStore preferencesStore) {
        this.preferencesStore = preferencesStore;
    }

    public void persistUiState(Preferences preferences) {
        if (preferences == null) {
            return;
        }
        preferences.extras().put(COLUMN_WIDTHS_PREF_KEY, captureColumnState());
        if (topSplit != null) {
            int left = topSplit.getDividerLocation();
            int right = Math.max(0, topSplit.getWidth() - left - topSplit.getDividerSize());
            if (left >= META_SPLIT_MIN && right >= 80) {
                preferences.extras().put(META_SPLIT_PREF_KEY, List.of(left, right));
            }
        }
    }

    public void setPlayerRepository(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void setBandRepository(BandRepository bandRepository) {
        this.bandRepository = bandRepository;
    }

    public void setSetlistRepository(SetlistRepository setlistRepository) {
        this.setlistRepository = setlistRepository;
    }

    public void setSongRepository(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public void setSongLayoutRepository(SongLayoutRepository songLayoutRepository) {
        this.songLayoutRepository = songLayoutRepository;
    }

    public void setPlaybackSession(PlaybackSession playbackSession, Consumer<String> errorReporter) {
        this.playbackSession = playbackSession;
        this.playbackErrorReporter = errorReporter == null ? msg -> {
        } : errorReporter;
    }

    public void reload() {
        Long selectedSetlistId = selectedSetlistId();
        Long selectedFolderId = selectedFolderId();
        boolean unfiledSelected = isUnfiledFolderSelected();
        rebuildTree();
        if (selectedSetlistId != null) {
            selectSetlistInTree(selectedSetlistId);
        } else if (unfiledSelected) {
            selectUnfiledInTree();
        } else if (selectedFolderId != null) {
            selectFolderInTree(selectedFolderId);
        }
        onTreeSelection();
    }

    private JPanel buildLeftPane() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        JButton addFolder = new JButton("Add folder");
        JButton addSetlist = new JButton("Add setlist");
        JButton importAbcp = new JButton("Import ABCP");
        JButton delete = new JButton("Delete");
        addFolder.addActionListener(e -> addFolder());
        addSetlist.addActionListener(e -> addSetlist());
        importAbcp.addActionListener(e -> importAbcp());
        delete.addActionListener(e -> deleteSelected());
        exportButton.addActionListener(e -> exportSelectedSet());
        exportButton.setEnabled(false);
        toolbar.add(addFolder);
        toolbar.add(addSetlist);
        toolbar.add(importAbcp);
        toolbar.add(delete);
        toolbar.add(exportButton);
        // Preferred height changes when width wraps; revalidate so BorderLayout.NORTH grows.
        toolbar.addComponentListener(new ComponentAdapter() {
            private int lastPreferredHeight = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                int preferredHeight = toolbar.getPreferredSize().height;
                if (preferredHeight != lastPreferredHeight) {
                    lastPreferredHeight = preferredHeight;
                    toolbar.revalidate();
                }
            }
        });
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(MAIN_SPLIT_INITIAL, 400));
        panel.setMinimumSize(new Dimension(MAIN_SPLIT_MIN_LEFT, 0));
        return panel;
    }

    private void buildEditorPane() {
        metaPanel.setLayout(new BoxLayout(metaPanel, BoxLayout.Y_AXIS));
        metaPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setEditable(false);
        notesArea.setOpaque(false);
        notesArea.setFocusable(false);
        editDetailsButton.addActionListener(e -> editDetails());
        exportDetailsButton.addActionListener(e -> exportSelectedSet());
        deleteSetlistButton.setToolTipText("Delete setlist");
        deleteSetlistButton.addActionListener(e -> deleteCurrentSetlist());

        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.add(new JLabel("Set Name"), BorderLayout.WEST);
        JPanel nameValueRow = new JPanel(new BorderLayout(8, 0));
        nameValueRow.setOpaque(false);
        nameValue.setFont(nameValue.getFont().deriveFont(Font.BOLD));
        nameValueRow.add(nameValue, BorderLayout.CENTER);
        JPanel nameActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        nameActions.setOpaque(false);
        nameActions.add(editDetailsButton);
        nameActions.add(exportDetailsButton);
        nameActions.add(deleteSetlistButton);
        nameValueRow.add(nameActions, BorderLayout.EAST);
        nameRow.add(nameValueRow, BorderLayout.CENTER);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(editDetailsButton.getPreferredSize().height, 24) + 12));
        nameRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        metaPanel.add(nameRow);

        metaPanel.add(inlineField("Band Layout", layoutValue));
        metaPanel.add(inlineField("Set Date / Time", dateTimeValue));

        JPanel targetSwitchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        targetSwitchRow.setOpaque(false);
        targetSwitchRow.add(new JLabel("Target Duration"));
        targetSwitchRow.add(targetDurationValue);
        targetSwitchRow.add(new JLabel("Switch delay (s)"));
        targetSwitchRow.add(switchDelayValue);
        metaPanel.add(flowRow(targetSwitchRow));

        durationSummaryTable.setTableHeader(null);
        durationSummaryTable.setFocusable(false);
        durationSummaryTable.setRowSelectionAllowed(false);
        durationSummaryTable.setColumnSelectionAllowed(false);
        durationSummaryTable.setCellSelectionEnabled(false);
        durationSummaryTable.setShowGrid(true);
        durationSummaryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        durationSummaryTable.setFont(durationSummaryTable.getFont().deriveFont(Font.PLAIN));
        DefaultTableCellRenderer labelRenderer = new DefaultTableCellRenderer();
        labelRenderer.setHorizontalAlignment(JLabel.LEFT);
        durationSummaryTable.getColumnModel().getColumn(0).setCellRenderer(labelRenderer);
        DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer();
        valueRenderer.setHorizontalAlignment(JLabel.LEFT);
        durationSummaryTable.getColumnModel().getColumn(1).setCellRenderer(valueRenderer);
        packDurationSummaryTable();
        JPanel summaryWrap = new JPanel(new BorderLayout());
        summaryWrap.setOpaque(false);
        summaryWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryWrap.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        summaryWrap.add(durationSummaryTable, BorderLayout.WEST);
        summaryWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                durationSummaryTable.getPreferredSize().height + 14));
        metaPanel.add(summaryWrap);

        notesPanel.setLayout(new BoxLayout(notesPanel, BoxLayout.Y_AXIS));
        notesPanel.setOpaque(false);
        notesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel notesCaption = new JLabel("Set Notes");
        notesCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        notesPanel.add(notesCaption);
        notesPanel.add(Box.createVerticalStrut(3));
        notesArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        notesScroll.setBorder(BorderFactory.createEmptyBorder());
        notesScroll.setPreferredSize(new Dimension(200, 80));
        notesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        notesPanel.add(notesScroll);
        notesPanel.add(Box.createVerticalStrut(12));
        notesPanel.setVisible(false);
        metaPanel.add(notesPanel);

        lockedValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockedValue.setVisible(false);
        lockedValue.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        metaPanel.add(lockedValue);
        metaPanel.add(Box.createVerticalGlue());

        JScrollPane metaScroll = new JScrollPane(metaPanel);
        metaScroll.setBorder(BorderFactory.createTitledBorder("Setlist"));
        metaScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        metaScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        metaScroll.getVerticalScrollBar().setUnitIncrement(16);
        metaScroll.setPreferredSize(new Dimension(META_SPLIT_DEFAULT, 280));
        metaScroll.setMinimumSize(new Dimension(META_SPLIT_MIN, 120));

        songsPanel.setBorder(BorderFactory.createTitledBorder("Songs"));
        JPanel songToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addSongButton.addActionListener(e -> addSong());
        removeSongButton.addActionListener(e -> removeSong());
        moveUpButton.addActionListener(e -> moveSongs(-1));
        moveDownButton.addActionListener(e -> moveSongs(1));
        songToolbar.add(addSongButton);
        songToolbar.add(removeSongButton);
        songToolbar.add(moveUpButton);
        songToolbar.add(moveDownButton);
        songsPanel.add(songToolbar, BorderLayout.NORTH);
        songsPanel.add(new JScrollPane(itemTable), BorderLayout.CENTER);
        songsPanel.setMinimumSize(new Dimension(200, 120));

        assignmentPanel.setPreferredSize(new Dimension(400, 220));
        assignmentPanel.setMinimumSize(new Dimension(120, 120));

        topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, metaScroll, songsPanel);
        topSplit.setResizeWeight(0.0);
        topSplit.setContinuousLayout(true);
        topSplit.setDividerLocation(META_SPLIT_DEFAULT);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, assignmentPanel);
        editorSplit.setResizeWeight(0.55);
        editorSplit.setContinuousLayout(true);
        editorSplit.setDividerLocation(280);

        editorPanel.setMinimumSize(new Dimension(280, 0));
        editorPanel.add(editorSplit, BorderLayout.CENTER);
    }

    private static JPanel inlineField(String label, Component field) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        int height = Math.max(field.getPreferredSize().height, 24);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height + 12));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return panel;
    }

    private static JPanel flowRow(JPanel row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 12));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        wrap.add(row, BorderLayout.WEST);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 12));
        return wrap;
    }

    private void installItemTableColumnModel() {
        DefaultTableColumnModel columnModel = new DefaultTableColumnModel() {
            @Override
            public void moveColumn(int columnIndex, int newIndex) {
                if (columnIndex < FIXED_COLUMN_COUNT || newIndex < FIXED_COLUMN_COUNT) {
                    return;
                }
                super.moveColumn(columnIndex, newIndex);
            }
        };
        itemTable.setColumnModel(columnModel);
        itemTable.createDefaultColumnsFromModel();
        itemTable.getTableHeader().setReorderingAllowed(true);
    }

    private void applyDefaultColumnWidths() {
        int fixedWidth = fixedLeadingColumnWidth();
        for (int modelIndex = 0; modelIndex < FIXED_COLUMN_COUNT; modelIndex++) {
            lockFixedColumn(columnByModelIndex(modelIndex), fixedWidth);
        }

        FontMetrics headerMetrics = itemTable.getTableHeader().getFontMetrics(itemTable.getTableHeader().getFont());
        setFlexiblePreferredWidth(
                columnByModelIndex(COL_PARTS),
                headerMetrics.stringWidth("Parts") + HEADER_PAD);
        setFlexiblePreferredWidth(
                columnByModelIndex(COL_DURATION),
                headerMetrics.stringWidth("Duration") + HEADER_PAD);
        setFlexiblePreferredWidth(columnByModelIndex(COL_TITLE), DEFAULT_TITLE_WIDTH);
        setFlexiblePreferredWidth(columnByModelIndex(COL_COMPOSER), DEFAULT_COMPOSER_WIDTH);
    }

    private int fixedLeadingColumnWidth() {
        FontMetrics bodyMetrics = itemTable.getFontMetrics(itemTable.getFont());
        FontMetrics headerMetrics = itemTable.getTableHeader().getFontMetrics(itemTable.getTableHeader().getFont());
        int digits = bodyMetrics.stringWidth("000");
        int hash = headerMetrics.stringWidth("#");
        return Math.max(digits, hash) + HEADER_PAD;
    }

    private static void lockFixedColumn(TableColumn column, int width) {
        if (column == null) {
            return;
        }
        int safe = Math.max(width, 24);
        column.setMinWidth(safe);
        column.setPreferredWidth(safe);
        column.setMaxWidth(safe);
        column.setResizable(false);
    }

    private static void setFlexiblePreferredWidth(TableColumn column, int width) {
        if (column == null) {
            return;
        }
        int safe = Math.max(width, 28);
        column.setResizable(true);
        column.setMinWidth(28);
        column.setMaxWidth(Integer.MAX_VALUE);
        column.setPreferredWidth(safe);
        column.setWidth(safe);
    }

    private TableColumn columnByModelIndex(int modelIndex) {
        TableColumnModel columns = itemTable.getColumnModel();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            TableColumn column = columns.getColumn(i);
            if (column.getModelIndex() == modelIndex) {
                return column;
            }
        }
        return null;
    }

    private void restoreColumnWidths() {
        if (preferences == null) {
            applyDefaultColumnWidths();
            return;
        }
        Object raw = preferences.extras().get(COLUMN_WIDTHS_PREF_KEY);
        List<Integer> widthsByModel = null;
        List<Integer> order = null;
        if (raw instanceof Map<?, ?> map) {
            widthsByModel = asIntegerList(map.get("widths"));
            order = asIntegerList(map.get("order"));
        } else {
            // Legacy: plain width list in default model order / view order.
            widthsByModel = asIntegerList(raw);
            if (widthsByModel != null && widthsByModel.size() == 6 && itemModel.getColumnCount() == 7) {
                widthsByModel = new ArrayList<>(widthsByModel);
                widthsByModel.add(COL_WARNING, fixedLeadingColumnWidth());
            }
        }
        if (widthsByModel == null || widthsByModel.isEmpty()) {
            applyDefaultColumnWidths();
            return;
        }

        restoreColumnOrder(order);
        applyDefaultColumnWidths();
        for (int modelIndex = FIXED_COLUMN_COUNT;
                modelIndex < widthsByModel.size() && modelIndex < itemModel.getColumnCount();
                modelIndex++) {
            Integer width = widthsByModel.get(modelIndex);
            if (width != null && width > 0) {
                setFlexiblePreferredWidth(columnByModelIndex(modelIndex), width);
            }
        }
    }

    private void restoreColumnOrder(List<Integer> order) {
        if (order == null || order.size() != itemModel.getColumnCount()) {
            return;
        }
        // Fixed leading columns must stay at view indices 0..FIXED-1 in model order.
        for (int i = 0; i < FIXED_COLUMN_COUNT; i++) {
            if (order.get(i) == null || order.get(i) != i) {
                return;
            }
        }
        for (int viewIndex = FIXED_COLUMN_COUNT; viewIndex < order.size(); viewIndex++) {
            Integer modelIndex = order.get(viewIndex);
            if (modelIndex == null
                    || modelIndex < FIXED_COLUMN_COUNT
                    || modelIndex >= itemModel.getColumnCount()) {
                continue;
            }
            int currentView = itemTable.convertColumnIndexToView(modelIndex);
            if (currentView >= FIXED_COLUMN_COUNT && currentView != viewIndex) {
                itemTable.moveColumn(currentView, viewIndex);
            }
        }
    }

    private Map<String, Object> captureColumnState() {
        TableColumnModel columns = itemTable.getColumnModel();
        int modelCount = itemModel.getColumnCount();
        Integer[] widthsByModel = new Integer[modelCount];
        List<Integer> order = new ArrayList<>(columns.getColumnCount());
        for (int viewIndex = 0; viewIndex < columns.getColumnCount(); viewIndex++) {
            TableColumn column = columns.getColumn(viewIndex);
            int modelIndex = column.getModelIndex();
            order.add(modelIndex);
            if (modelIndex >= 0 && modelIndex < modelCount) {
                widthsByModel[modelIndex] = column.getWidth();
            }
        }
        List<Integer> widths = new ArrayList<>(modelCount);
        for (int i = 0; i < modelCount; i++) {
            widths.add(widthsByModel[i] != null ? widthsByModel[i] : 0);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("widths", widths);
        state.put("order", order);
        return state;
    }

    private static List<Integer> asIntegerList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Integer> widths = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof Number number) {
                widths.add(number.intValue());
            } else if (entry instanceof String text) {
                try {
                    widths.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return widths;
    }

    private void enableTreeReorder() {
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new TransferHandler() {
            private TreePath dragPath;

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                dragPath = tree.getSelectionPath();
                if (dragPath == null) {
                    return null;
                }
                Object last = dragPath.getLastPathComponent();
                if (!(last instanceof DefaultMutableTreeNode node)) {
                    return null;
                }
                Object user = node.getUserObject();
                if (user instanceof SetlistNode setlistNode) {
                    return new StringSelection("setlist:" + setlistNode.setlist().id());
                }
                if (user instanceof FolderNode folderNode && folderNode.folder() != null) {
                    return new StringSelection("folder:" + folderNode.folder().id());
                }
                dragPath = null;
                return null;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()
                        || !support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        || setlistRepository == null
                        || dragPath == null) {
                    return false;
                }
                JTree.DropLocation drop = (JTree.DropLocation) support.getDropLocation();
                TreePath dropPath = drop.getPath();
                if (dropPath == null) {
                    return false;
                }
                DefaultMutableTreeNode dragNode = (DefaultMutableTreeNode) dragPath.getLastPathComponent();
                Object dragUser = dragNode.getUserObject();
                DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
                Object dropUser = dropNode.getUserObject();
                int childIndex = drop.getChildIndex();

                if (dragUser instanceof SetlistNode) {
                    if (dropUser instanceof FolderNode) {
                        return true;
                    }
                    if (dropUser instanceof SetlistNode && childIndex < 0) {
                        return dropNode.getParent() instanceof DefaultMutableTreeNode parent
                                && parent.getUserObject() instanceof FolderNode;
                    }
                    return false;
                }
                if (dragUser instanceof FolderNode folderNode && folderNode.folder() != null) {
                    if (dropNode == treeRoot && childIndex >= 0) {
                        return true;
                    }
                    if (dropUser instanceof FolderNode && childIndex < 0) {
                        return true;
                    }
                    return false;
                }
                return false;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support) || setlistRepository == null || dragPath == null) {
                    return false;
                }
                String payload;
                try {
                    payload = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                } catch (Exception ex) {
                    return false;
                }
                JTree.DropLocation drop = (JTree.DropLocation) support.getDropLocation();
                DefaultMutableTreeNode dragNode = (DefaultMutableTreeNode) dragPath.getLastPathComponent();
                try {
                    if (payload.startsWith("setlist:")) {
                        long setlistId = Long.parseLong(payload.substring("setlist:".length()));
                        return dropSetlist(dragNode, drop, setlistId);
                    }
                    if (payload.startsWith("folder:")) {
                        long folderId = Long.parseLong(payload.substring("folder:".length()));
                        return dropFolder(dragNode, drop, folderId);
                    }
                } catch (NumberFormatException | LibraryException ex) {
                    showError(ex instanceof LibraryException libraryEx
                            ? libraryEx.getMessage()
                            : "Failed to rearrange setlists");
                    reload();
                }
                return false;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                dragPath = null;
            }
        });
    }

    private boolean dropSetlist(
            DefaultMutableTreeNode dragNode,
            JTree.DropLocation drop,
            long setlistId) throws LibraryException {
        DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) drop.getPath().getLastPathComponent();
        Object dropUser = dropNode.getUserObject();
        int childIndex = drop.getChildIndex();

        DefaultMutableTreeNode targetFolderNode;
        int sortOrder;
        if (dropUser instanceof FolderNode) {
            targetFolderNode = dropNode;
            if (childIndex < 0) {
                sortOrder = targetFolderNode.getChildCount();
                if (dragNode.getParent() == targetFolderNode) {
                    sortOrder--;
                }
            } else {
                sortOrder = childIndex;
                if (dragNode.getParent() == targetFolderNode) {
                    int fromIndex = targetFolderNode.getIndex(dragNode);
                    if (fromIndex >= 0 && childIndex > fromIndex) {
                        sortOrder--;
                    }
                }
            }
        } else if (dropUser instanceof SetlistNode) {
            targetFolderNode = (DefaultMutableTreeNode) dropNode.getParent();
            if (targetFolderNode == null || !(targetFolderNode.getUserObject() instanceof FolderNode)) {
                return false;
            }
            sortOrder = targetFolderNode.getIndex(dropNode) + 1;
            if (dragNode.getParent() == targetFolderNode) {
                int fromIndex = targetFolderNode.getIndex(dragNode);
                if (fromIndex >= 0 && sortOrder > fromIndex) {
                    sortOrder--;
                }
            }
        } else {
            return false;
        }

        FolderNode folderNode = (FolderNode) targetFolderNode.getUserObject();
        Long folderId = folderNode.folder() == null ? null : folderNode.folder().id();
        sortOrder = Math.max(0, sortOrder);
        setlistRepository.moveSetlistToFolder(setlistId, folderId, sortOrder);
        reload();
        selectSetlistInTree(setlistId);
        return true;
    }

    private boolean dropFolder(
            DefaultMutableTreeNode dragNode,
            JTree.DropLocation drop,
            long folderId) throws LibraryException {
        DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) drop.getPath().getLastPathComponent();
        int childIndex = drop.getChildIndex();

        int insertAmongRoot;
        if (dropNode == treeRoot && childIndex >= 0) {
            insertAmongRoot = childIndex;
        } else if (dropNode.getUserObject() instanceof FolderNode) {
            insertAmongRoot = treeRoot.getIndex(dropNode);
            if (insertAmongRoot < 0) {
                return false;
            }
        } else {
            return false;
        }

        int fromRootIndex = treeRoot.getIndex(dragNode);
        if (fromRootIndex < 0) {
            return false;
        }

        List<Long> order = new ArrayList<>();
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            if (child.getUserObject() instanceof FolderNode folderNode
                    && folderNode.folder() != null
                    && folderNode.folder().id() != folderId) {
                order.add(folderNode.folder().id());
            }
        }

        int insertAt = 0;
        for (int i = 0; i < insertAmongRoot && i < treeRoot.getChildCount(); i++) {
            if (i == fromRootIndex) {
                continue;
            }
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            if (child.getUserObject() instanceof FolderNode folderNode && folderNode.folder() != null) {
                insertAt++;
            }
        }
        insertAt = Math.max(0, Math.min(insertAt, order.size()));
        order.add(insertAt, folderId);

        setlistRepository.reorderFolders(order);
        reload();
        selectFolderInTree(folderId);
        return true;
    }

    private void enableItemTableReorder() {
        itemTable.setDragEnabled(true);
        itemTable.setDropMode(DropMode.INSERT_ROWS);
        itemTable.setTransferHandler(new TransferHandler() {
            private int[] dragRows = new int[0];

            @Override
            public int getSourceActions(JComponent c) {
                return isSelectedSetlistLocked() ? NONE : MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                if (isSelectedSetlistLocked()) {
                    return null;
                }
                dragRows = itemTable.getSelectedRows();
                Arrays.sort(dragRows);
                return new StringSelection(Arrays.toString(dragRows));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        && dragRows.length > 0
                        && selectedSetlist() != null
                        && !isSelectedSetlistLocked();
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support) || !(support.getDropLocation() instanceof JTable.DropLocation drop)) {
                    return false;
                }
                int dropIndex = drop.getRow();
                if (dropIndex < 0) {
                    dropIndex = itemModel.getRowCount();
                }
                boolean moved = reorderRows(dragRows, dropIndex);
                dragRows = new int[0];
                return moved;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                dragRows = new int[0];
            }
        });
    }

    private boolean reorderRows(int[] selectedRows, int dropIndex) {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || setlistRepository == null || selectedRows.length == 0 || setlist.locked()) {
            return false;
        }
        List<SetlistItemInfo> current = new ArrayList<>(itemModel.items());
        if (dropIndex > current.size()) {
            dropIndex = current.size();
        }

        Set<Integer> selected = new HashSet<>();
        for (int row : selectedRows) {
            if (row >= 0 && row < current.size()) {
                selected.add(row);
            }
        }
        if (selected.isEmpty()) {
            return false;
        }

        // Dropping inside the selected block is a no-op.
        int firstSelected = selected.stream().mapToInt(Integer::intValue).min().orElse(0);
        int lastSelected = selected.stream().mapToInt(Integer::intValue).max().orElse(0);
        if (dropIndex >= firstSelected && dropIndex <= lastSelected + 1
                && selected.size() == (lastSelected - firstSelected + 1)) {
            boolean contiguous = true;
            for (int i = firstSelected; i <= lastSelected; i++) {
                if (!selected.contains(i)) {
                    contiguous = false;
                    break;
                }
            }
            if (contiguous) {
                return false;
            }
        }

        List<SetlistItemInfo> moved = new ArrayList<>();
        List<SetlistItemInfo> remaining = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            if (selected.contains(i)) {
                moved.add(current.get(i));
            } else {
                remaining.add(current.get(i));
            }
        }

        int insertAt = dropIndex;
        for (int row : selectedRows) {
            if (row < dropIndex) {
                insertAt--;
            }
        }
        insertAt = Math.max(0, Math.min(insertAt, remaining.size()));
        remaining.addAll(insertAt, moved);

        List<Long> order = new ArrayList<>(remaining.size());
        for (SetlistItemInfo item : remaining) {
            order.add(item.id());
        }
        try {
            setlistRepository.reorderItems(setlist.id(), order);
            reloadItems(setlist.id());
            itemTable.clearSelection();
            for (int i = 0; i < moved.size(); i++) {
                int row = insertAt + i;
                itemTable.addRowSelectionInterval(row, row);
            }
            return true;
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return false;
        }
    }

    private void rebuildTree() {
        suppressSelection = true;
        treeRoot.removeAllChildren();
        if (setlistRepository == null) {
            treeModel.reload();
            suppressSelection = false;
            return;
        }
        try {
            List<SetlistFolderInfo> folders = setlistRepository.listFolders();
            List<SetlistInfo> setlists = setlistRepository.listSetlists();
            Map<Long, DefaultMutableTreeNode> folderNodes = new HashMap<>();
            for (SetlistFolderInfo folder : folders) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FolderNode(folder));
                folderNodes.put(folder.id(), node);
                treeRoot.add(node);
            }
            // Always show Unfiled so setlists can be dragged out of folders (Python shows it
            // only when non-empty; empty Unfiled is still a valid drop target here).
            DefaultMutableTreeNode unfiled = new DefaultMutableTreeNode(new FolderNode(null));
            for (SetlistInfo setlist : setlists) {
                DefaultMutableTreeNode setlistNode = new DefaultMutableTreeNode(new SetlistNode(setlist));
                if (setlist.folderId() == null) {
                    unfiled.add(setlistNode);
                } else {
                    DefaultMutableTreeNode parent = folderNodes.get(setlist.folderId());
                    if (parent == null) {
                        unfiled.add(setlistNode);
                    } else {
                        parent.add(setlistNode);
                    }
                }
            }
            treeRoot.add(unfiled);
            treeModel.reload();
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        } finally {
            suppressSelection = false;
        }
    }

    private void onTreeSelection() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null) {
            clearEditor();
            setEditorEnabled(false);
            return;
        }
        setEditorEnabled(true);
        loadSetlistEditor(setlist);
    }

    private void clearEditor() {
        nameValue.setText("\u2014");
        layoutValue.setText("\u2014");
        dateTimeValue.setText("\u2014");
        targetDurationValue.setText("\u2014");
        switchDelayValue.setText("\u2014");
        notesArea.setText("");
        notesPanel.setVisible(false);
        lockedValue.setVisible(false);
        setDurationSummaryValues("\u2014", "\u2014", "\u2014");
        itemModel.setItems(List.of());
        assignmentPanel.clear();
        revalidate();
        repaint();
    }

    private void setEditorEnabled(boolean enabled) {
        editDetailsButton.setEnabled(enabled);
        deleteSetlistButton.setEnabled(enabled);
        exportButton.setEnabled(enabled);
        exportDetailsButton.setEnabled(enabled);
        boolean songsEditable = enabled && !isSelectedSetlistLocked();
        addSongButton.setEnabled(songsEditable);
        removeSongButton.setEnabled(songsEditable);
        moveUpButton.setEnabled(songsEditable);
        moveDownButton.setEnabled(songsEditable);
        itemTable.setEnabled(enabled);
        itemTable.setDragEnabled(songsEditable);
        assignmentPanel.setEnabled(enabled);
    }

    private boolean isSelectedSetlistLocked() {
        SetlistInfo setlist = selectedSetlist();
        return setlist != null && setlist.locked();
    }

    private void loadSetlistEditor(SetlistInfo setlist) {
        nameValue.setText(displayOrDash(setlist.name()));
        layoutValue.setText(resolveLayoutLabel(setlist.bandLayoutId()));
        dateTimeValue.setText(formatDateTime(setlist.setDate(), setlist.setTime()));
        targetDurationValue.setText(
                setlist.targetDurationSeconds() == null || setlist.targetDurationSeconds() <= 0
                        ? "\u2014"
                        : LibraryDisplayFormats.formatHoursMinutes(setlist.targetDurationSeconds()));
        switchDelayValue.setText(
                setlist.defaultChangeDurationSeconds() == null
                        ? "\u2014"
                        : String.valueOf(setlist.defaultChangeDurationSeconds()));
        String notes = setlist.notes();
        if (notes == null || notes.isBlank()) {
            notesArea.setText("");
            notesPanel.setVisible(false);
        } else {
            notesArea.setText(notes);
            notesPanel.setVisible(true);
        }
        lockedValue.setVisible(setlist.locked());
        setEditorEnabled(true);
        reloadItems(setlist.id());
        revalidate();
        repaint();
    }

    private String resolveLayoutLabel(Long bandLayoutId) {
        if (bandLayoutId == null || bandRepository == null) {
            return "(none)";
        }
        try {
            for (BandInfo band : bandRepository.listBands()) {
                BandLayoutInfo layout = bandRepository.getOrCreatePrimaryLayout(band.id());
                if (Objects.equals(layout.id(), bandLayoutId)) {
                    return band.name();
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
        return "(none)";
    }

    private static String formatDateTime(String setDate, String setTime) {
        boolean hasDate = setDate != null && !setDate.isBlank();
        boolean hasTime = setTime != null && !setTime.isBlank();
        if (!hasDate && !hasTime) {
            return "\u2014";
        }
        if (hasDate && hasTime) {
            return setDate + " at " + setTime;
        }
        return hasDate ? setDate : setTime;
    }

    private static String displayOrDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private void reloadItems(long setlistId) {
        if (setlistRepository == null) {
            itemModel.setItems(List.of());
            updateDurationSummary();
            return;
        }
        try {
            itemModel.setItems(setlistRepository.listItems(setlistId));
            syncPlaybackQueueFromSetlist(setlistId);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            itemModel.setItems(List.of());
        }
        updateDurationSummary();
        reloadAssignments();
    }

    private void updateDurationSummary() {
        List<SetlistItemInfo> items = itemModel.items();
        if (items.isEmpty()) {
            setDurationSummaryValues("\u2014", "\u2014", "\u2014");
            return;
        }
        int songSeconds = 0;
        for (SetlistItemInfo item : items) {
            Integer duration = item.songDurationSeconds();
            if (duration != null) {
                songSeconds += Math.max(0, duration);
            }
        }
        SetlistInfo setlist = selectedSetlist();
        int delay = setlist == null || setlist.defaultChangeDurationSeconds() == null
                ? 0
                : Math.max(0, setlist.defaultChangeDurationSeconds());
        int target = setlist == null || setlist.targetDurationSeconds() == null
                ? 0
                : Math.max(0, setlist.targetDurationSeconds());
        int switchSeconds = items.size() > 1 ? delay * (items.size() - 1) : 0;
        int actualWithSwitches = songSeconds + switchSeconds;
        String raw = LibraryDisplayFormats.formatSignedDuration(songSeconds) + " (No Delays)";
        String actual = LibraryDisplayFormats.formatSignedDuration(actualWithSwitches) + " (With Delays)";
        String remaining = target <= 0
                ? "\u2014"
                : LibraryDisplayFormats.formatSignedDuration(target - actualWithSwitches);
        setDurationSummaryValues(raw, actual, remaining);
    }

    private void setDurationSummaryValues(String raw, String actual, String remaining) {
        durationSummaryModel.setValueAt(raw, 0, 1);
        durationSummaryModel.setValueAt(actual, 1, 1);
        durationSummaryModel.setValueAt(remaining, 2, 1);
        packDurationSummaryTable();
    }

    private void packDurationSummaryTable() {
        TableColumnModel columns = durationSummaryTable.getColumnModel();
        FontMetrics metrics = durationSummaryTable.getFontMetrics(durationSummaryTable.getFont());
        int valueMinWidth = metrics.stringWidth(DURATION_VALUE_MIN_SAMPLE) + 16;
        for (int col = 0; col < columns.getColumnCount(); col++) {
            int width = col == 1 ? valueMinWidth : 24;
            for (int row = 0; row < durationSummaryTable.getRowCount(); row++) {
                Component renderer = durationSummaryTable.prepareRenderer(
                        durationSummaryTable.getCellRenderer(row, col), row, col);
                width = Math.max(width, renderer.getPreferredSize().width + 12);
            }
            columns.getColumn(col).setPreferredWidth(width);
            columns.getColumn(col).setMinWidth(width);
            columns.getColumn(col).setMaxWidth(width);
        }
        int tableWidth = columns.getTotalColumnWidth();
        int tableHeight = durationSummaryTable.getRowHeight() * durationSummaryTable.getRowCount();
        Dimension size = new Dimension(tableWidth, tableHeight);
        durationSummaryTable.setPreferredSize(size);
        durationSummaryTable.setMinimumSize(size);
        durationSummaryTable.setMaximumSize(size);
        Container parent = durationSummaryTable.getParent();
        if (parent instanceof JComponent wrap) {
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableHeight + 14));
            wrap.revalidate();
        }
    }

    private void restoreMetaSplit() {
        int divider = META_SPLIT_DEFAULT;
        if (preferences != null) {
            List<Integer> saved = asIntegerList(preferences.extras().get(META_SPLIT_PREF_KEY));
            if (saved != null && !saved.isEmpty() && saved.get(0) != null && saved.get(0) >= META_SPLIT_MIN) {
                divider = saved.get(0);
            }
        }
        if (topSplit != null) {
            topSplit.setDividerLocation(divider);
        }
    }

    private void syncPlaybackQueueFromSetlist(long setlistId) {
        if (playbackSession == null) {
            return;
        }
        List<PlayQueueItem> items = new ArrayList<>();
        for (SetlistItemInfo item : itemModel.items()) {
            items.add(PlayQueueItem.ofSetlistItem(
                    item.songId(),
                    item.songTitle(),
                    item.songComposers(),
                    item.songDurationSeconds(),
                    item.partCount(),
                    setlistId,
                    item.id()));
        }
        playbackSession.syncFromSetlistIfActive(setlistId, items);
    }

    private void enableItemTablePlaybackActions() {
        TableColumn playColumn = columnByModelIndex(COL_PLAY);
        if (playColumn != null) {
            playColumn.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(
                            table, value, isSelected, hasFocus, row, column);
                    label.setIcon(PlaybackIcons.play(14));
                    label.setText("");
                    label.setHorizontalAlignment(JLabel.CENTER);
                    label.setToolTipText("Play setlist from this song");
                    return label;
                }
            });
        }
        itemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) {
                    return;
                }
                int viewRow = itemTable.rowAtPoint(e.getPoint());
                int viewCol = itemTable.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                int modelCol = itemTable.convertColumnIndexToModel(viewCol);
                if (modelCol != COL_PLAY) {
                    return;
                }
                playSetlistFromRow(viewRow);
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowItemPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowItemPopup(e);
            }
        });
    }

    private void enableItemTableWarningColumn() {
        TableColumn warningColumn = columnByModelIndex(COL_WARNING);
        if (warningColumn != null) {
            warningColumn.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(
                            table, value, isSelected, hasFocus, row, column);
                    int modelRow = table.convertRowIndexToModel(row);
                    String warning = itemModel.warningAt(modelRow);
                    if (warning != null && !warning.isBlank()) {
                        label.setText("\u26A0");
                        label.setForeground(WARNING_RED);
                        Font base = label.getFont();
                        label.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 4f));
                        label.setToolTipText(warning);
                    } else {
                        label.setText("");
                        label.setToolTipText(null);
                    }
                    label.setHorizontalAlignment(JLabel.CENTER);
                    label.setIcon(null);
                    return label;
                }
            });
        }
    }

    /**
     * Recompute the warning-flag column for every song (Python {@code _refresh_error_column_only}).
     */
    private void refreshWarningColumn() {
        List<SetlistItemInfo> items = itemModel.items();
        if (items.isEmpty()) {
            itemModel.setWarnings(List.of());
            return;
        }
        SetlistInfo setlist = selectedSetlist();
        Long bandLayoutId = setlist == null ? null : setlist.bandLayoutId();
        if (bandLayoutId == null
                || bandRepository == null
                || setlistRepository == null
                || songLayoutRepository == null
                || playerRepository == null) {
            itemModel.setWarnings(blankWarnings(items.size()));
            return;
        }
        try {
            List<BandLayoutSlotInfo> slots = bandRepository.listSlots(bandLayoutId);
            Map<Long, Set<Long>> ownedByPlayer = loadOwnedInstruments(slots);
            Map<Long, String> instrumentNames = new HashMap<>();
            for (InstrumentInfo info : playerRepository.listInstruments()) {
                instrumentNames.put(info.id(), info.name());
            }
            Map<Long, Set<Long>> equivByInstrument =
                    SetlistSongWarningChecker.buildEquivalentInstrumentIds(instrumentNames);

            List<String> warnings = new ArrayList<>(items.size());
            for (SetlistItemInfo item : items) {
                Map<Long, Integer> layoutAssigns = new HashMap<>();
                if (item.songLayoutId() != null) {
                    for (SongLayoutAssignmentInfo a
                            : songLayoutRepository.listAssignments(item.songLayoutId())) {
                        layoutAssigns.put(a.playerId(), a.partNumber());
                    }
                }
                Map<Long, Integer> overrides = new HashMap<>();
                for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(item.id())) {
                    overrides.put(a.playerId(), a.partNumber());
                }
                warnings.add(SetlistSongWarningChecker.warningMessage(
                        bandLayoutId,
                        item.partsJson(),
                        slots,
                        layoutAssigns,
                        overrides,
                        ownedByPlayer,
                        equivByInstrument));
            }
            itemModel.setWarnings(warnings);
        } catch (LibraryException ex) {
            itemModel.setWarnings(blankWarnings(items.size()));
        }
    }

    private Map<Long, Set<Long>> loadOwnedInstruments(List<BandLayoutSlotInfo> slots)
            throws LibraryException {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (BandLayoutSlotInfo slot : slots) {
            Set<Long> owned = new HashSet<>();
            for (PlayerInstrumentInfo info : playerRepository.listPlayerInstruments(slot.playerId())) {
                if (info.hasInstrument()) {
                    owned.add(info.instrumentId());
                }
            }
            result.put(slot.playerId(), owned);
        }
        return result;
    }

    private static List<String> blankWarnings(int size) {
        List<String> warnings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            warnings.add(null);
        }
        return warnings;
    }

    private void maybeShowItemPopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int viewRow = itemTable.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        if (!itemTable.isRowSelected(viewRow)) {
            itemTable.setRowSelectionInterval(viewRow, viewRow);
        }
        SetlistItemInfo item = itemModel.itemAt(viewRow);
        SetlistInfo currentSetlist = selectedSetlist();
        Long currentSetlistId = currentSetlist == null ? null : currentSetlist.id();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem play = new JMenuItem("Play setlist from here");
        play.addActionListener(ev -> playSetlistFromRow(viewRow));
        JMenuItem enqueue = new JMenuItem("Add to queue");
        enqueue.addActionListener(ev -> enqueueItem(item));
        menu.add(play);
        menu.add(enqueue);
        menu.addSeparator();
        menu.add(AddToSetlistMenu.build(
                setlistRepository,
                currentSetlistId,
                target -> addSongToSetlist(item, target)));
        menu.show(itemTable, e.getX(), e.getY());
    }

    private void addSongToSetlist(SetlistItemInfo item, SetlistInfo target) {
        if (item == null || target == null || setlistRepository == null || target.locked()) {
            return;
        }
        try {
            int position = setlistRepository.listItems(target.id()).size();
            setlistRepository.addItem(target.id(), item.songId(), position, null, null);
            SetlistInfo current = selectedSetlist();
            if (current != null && current.id() == target.id()) {
                reloadItems(target.id());
            }
            playbackErrorReporter.accept("Added \"" + item.songTitle() + "\" to " + target.name());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void playSetlistFromRow(int row) {
        if (playbackSession == null) {
            return;
        }
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || row < 0 || row >= itemModel.getRowCount()) {
            return;
        }
        List<PlayQueueItem> items = new ArrayList<>();
        for (SetlistItemInfo item : itemModel.items()) {
            items.add(PlayQueueItem.ofSetlistItem(
                    item.songId(),
                    item.songTitle(),
                    item.songComposers(),
                    item.songDurationSeconds(),
                    item.partCount(),
                    setlist.id(),
                    item.id()));
        }
        try {
            playbackSession.playSetlist(setlist.id(), items, row);
        } catch (PlaybackException ex) {
            playbackErrorReporter.accept(ex.getMessage());
        }
    }

    private void enqueueItem(SetlistItemInfo item) {
        if (playbackSession == null || item == null) {
            return;
        }
        try {
            playbackSession.enqueue(PlayQueueItem.ofSong(
                    item.songId(),
                    item.songTitle(),
                    item.songComposers(),
                    item.songDurationSeconds(),
                    item.partCount()));
        } catch (PlaybackException ex) {
            playbackErrorReporter.accept(ex.getMessage());
        }
    }

    private void reloadAssignments() {
        SetlistInfo setlist = selectedSetlist();
        SetlistItemInfo item = selectedItem();
        if (setlist == null) {
            assignmentPanel.clear();
            refreshWarningColumn();
            return;
        }
        Long songLayoutId = item == null ? null : item.songLayoutId();
        if (item != null
                && setlist.bandLayoutId() != null
                && songLayoutRepository != null
                && setlistRepository != null) {
            try {
                Long ensured = ensureSongLayout(item, setlist.bandLayoutId());
                if (ensured != null && !Objects.equals(ensured, item.songLayoutId())) {
                    int selected = itemTable.getSelectedRow();
                    itemModel.setItems(setlistRepository.listItems(setlist.id()));
                    if (selected >= 0 && selected < itemModel.getRowCount()) {
                        itemTable.setRowSelectionInterval(selected, selected);
                        item = itemModel.itemAt(selected);
                    } else {
                        item = selectedItem();
                    }
                }
                songLayoutId = ensured;
            } catch (LibraryException ex) {
                showError(ex.getMessage());
            }
        }
        SetlistItemInfo current = item;
        assignmentPanel.refresh(
                setlist.bandLayoutId(),
                current == null ? null : current.id(),
                songLayoutId,
                current == null ? null : current.partsJson(),
                itemModel.items());
        refreshWarningColumn();
    }

    /**
     * Link a matching library song layout when one exists, and snapshot its assignments onto
     * the setlist item if that item has no overrides yet.
     */
    private Long ensureSongLayout(SetlistItemInfo item, long bandLayoutId) throws LibraryException {
        if (songLayoutRepository == null || setlistRepository == null) {
            return item.songLayoutId();
        }
        setlistRepository.snapshotSongLayoutToItem(item.id(), item.songId(), bandLayoutId);
        return songLayoutRepository.findSongLayout(item.songId(), bandLayoutId)
                .map(SongLayoutInfo::id)
                .orElse(item.songLayoutId());
    }

    private void editDetails() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || setlistRepository == null) {
            return;
        }
        SetlistDetailsDialog.showEdit(SwingUtilities.getWindowAncestor(this), bandRepository, setlist)
                .ifPresent(details -> {
                    try {
                        setlistRepository.updateSetlist(
                                setlist.id(),
                                details.name(),
                                details.bandLayoutId(),
                                setlist.folderId(),
                                setlist.sortOrder(),
                                details.locked(),
                                details.switchDelaySeconds(),
                                details.notes(),
                                details.setDate(),
                                details.setTime(),
                                details.targetDurationSeconds());
                        if (!Objects.equals(setlist.bandLayoutId(), details.bandLayoutId())) {
                            setlistRepository.remapItemsToBandLayout(
                                    setlist.id(), details.bandLayoutId());
                        }
                        reload();
                        selectSetlistInTree(setlist.id());
                    } catch (LibraryException ex) {
                        showError(ex.getMessage());
                    }
                });
    }

    private void enableTreeContextMenu() {
        java.awt.event.MouseAdapter adapter = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowTreePopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowTreePopup(e);
            }
        };
        tree.addMouseListener(adapter);
    }

    private void maybeShowTreePopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger() || setlistRepository == null) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof SetlistNode setlistNode)) {
            return;
        }
        tree.setSelectionPath(path);
        SetlistInfo setlist = setlistNode.setlist();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem duplicate = new JMenuItem("Duplicate setlist...");
        duplicate.addActionListener(ev -> duplicateSetlist(setlist));
        menu.add(duplicate);

        menu.addSeparator();
        JMenuItem prependTo = new JMenuItem("Prepend to...");
        prependTo.addActionListener(ev -> prependCurrentToOther(setlist));
        menu.add(prependTo);
        JMenuItem appendTo = new JMenuItem("Append to...");
        appendTo.addActionListener(ev -> appendCurrentToOther(setlist));
        menu.add(appendTo);
        JMenuItem prependFrom = new JMenuItem("Prepend from...");
        prependFrom.addActionListener(ev -> prependOtherIntoCurrent(setlist));
        menu.add(prependFrom);
        JMenuItem appendFrom = new JMenuItem("Append from...");
        appendFrom.addActionListener(ev -> appendOtherIntoCurrent(setlist));
        menu.add(appendFrom);

        menu.addSeparator();
        JMenuItem exportSet = new JMenuItem("Export set...");
        exportSet.addActionListener(ev -> openExportDialog(setlist));
        menu.add(exportSet);
        JMenuItem exportAbcp = new JMenuItem("Export to ABCP...");
        exportAbcp.addActionListener(ev -> exportToAbcp(setlist));
        menu.add(exportAbcp);

        menu.addSeparator();
        JMenuItem lockToggle = new JMenuItem(setlist.locked() ? "Unlock setlist" : "Lock setlist");
        lockToggle.addActionListener(ev -> toggleSetlistLocked(setlist));
        menu.add(lockToggle);

        menu.addSeparator();
        JMenuItem delete = new JMenuItem("Delete setlist...");
        delete.addActionListener(ev -> deleteSetlist(setlist));
        menu.add(delete);

        menu.show(tree, e.getX(), e.getY());
    }

    private void exportSelectedSet() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist != null) {
            openExportDialog(setlist);
        }
    }

    private void openExportDialog(SetlistInfo setlist) {
        if (setlist == null
                || preferences == null
                || preferencesStore == null
                || setlistRepository == null
                || songRepository == null
                || bandRepository == null
                || playerRepository == null) {
            return;
        }
        SetExportDialog.show(
                SwingUtilities.getWindowAncestor(this),
                setlist,
                preferences,
                preferencesStore,
                setlistRepository,
                songRepository,
                bandRepository,
                playerRepository);
    }

    private void exportToAbcp(SetlistInfo setlist) {
        if (setlist == null || setlistRepository == null || songRepository == null) {
            return;
        }
        String startDir = preferences == null
                ? System.getProperty("user.home")
                : SetExportDialog.resolveDefaultOutputDir(preferences);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export to ABCP");
        chooser.setFileFilter(new FileNameExtensionFilter("ABCP Playlist (*.abcp)", "abcp"));
        Path start = Paths.get(startDir);
        if (Files.isDirectory(start)) {
            chooser.setCurrentDirectory(start.toFile());
        }
        String baseName = SetExportService.sanitizeForPath(
                setlist.name() == null || setlist.name().isBlank() ? "setlist" : setlist.name());
        chooser.setSelectedFile(start.resolve(baseName + ".abcp").toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path output = chooser.getSelectedFile().toPath();
        if (!output.getFileName().toString().toLowerCase().endsWith(".abcp")) {
            output = output.resolveSibling(output.getFileName().toString() + ".abcp");
        }
        try {
            List<SetExportItemInfo> items = setlistRepository.listItemsForExport(setlist.id());
            SetExportService service = new SetExportService(
                    setlistRepository, songRepository, bandRepository, playerRepository);
            service.exportStandaloneAbcp(items, output);
            JOptionPane.showMessageDialog(
                    this,
                    "ABCP exported to:\n" + output,
                    "Export to ABCP",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (LibraryException | SetExportException ex) {
            showError(ex.getMessage());
        }
    }

    private void importAbcp() {
        if (setlistRepository == null || songRepository == null) {
            return;
        }
        int notice = JOptionPane.showConfirmDialog(
                this,
                "ABCP import is intended for playlists you created locally in ABC Player"
                        + " and want to bring into ABC Music Manager.\n\n"
                        + "It is not meant for importing a shared setlist from someone else —"
                        + " track paths must already exist in this library and match exactly.\n\n"
                        + "Continue?",
                "Import ABCP",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (notice != JOptionPane.OK_OPTION) {
            return;
        }

        String startDir = preferences == null
                ? System.getProperty("user.home")
                : SetExportDialog.resolveDefaultOutputDir(preferences);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import ABCP");
        chooser.setFileFilter(new FileNameExtensionFilter("ABCP Playlist (*.abcp)", "abcp"));
        Path start = Paths.get(startDir);
        if (Files.isDirectory(start)) {
            chooser.setCurrentDirectory(start.toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();

        List<String> trackPaths;
        try {
            trackPaths = AbcpReader.read(path);
        } catch (AbcpException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not read ABCP file:\n" + ex.getMessage(),
                    "Import ABCP",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (trackPaths.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "The file contains no tracks.",
                    "Import ABCP",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            List<Long> matchedSongIds = new ArrayList<>();
            List<String> unmatched = new ArrayList<>();
            for (String filePath : trackPaths) {
                Optional<Long> songId = songRepository.findSongIdByFilePath(filePath);
                if (songId.isPresent()) {
                    matchedSongIds.add(songId.get());
                } else {
                    unmatched.add(filePath);
                }
            }
            if (matchedSongIds.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "None of the tracks in the file were found in your library. "
                                + "Paths must match exactly.",
                        "Import ABCP",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!unmatched.isEmpty()) {
                int reply = JOptionPane.showConfirmDialog(
                        this,
                        matchedSongIds.size()
                                + " of "
                                + trackPaths.size()
                                + " tracks matched. "
                                + unmatched.size()
                                + " path(s) not found in library.\n\n"
                                + "Import the matched tracks only?",
                        "Import ABCP",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (reply != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            String setlistName = abcpStem(path);
            Long folderId = selectedFolderId();
            long setlistId = setlistRepository.addSetlist(setlistName, folderId);
            for (int i = 0; i < matchedSongIds.size(); i++) {
                setlistRepository.addItem(setlistId, matchedSongIds.get(i), i, null, null);
            }
            reload();
            selectSetlistInTree(setlistId);
            JOptionPane.showMessageDialog(
                    this,
                    "Imported " + matchedSongIds.size() + " tracks into setlist '" + setlistName + "'.",
                    "Import ABCP",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private static String abcpStem(Path path) {
        String name = path.getFileName() == null ? "Imported" : path.getFileName().toString();
        if (name.toLowerCase().endsWith(".abcp") && name.length() > 5) {
            return name.substring(0, name.length() - 5);
        }
        return name.isBlank() ? "Imported" : name;
    }

    private void duplicateSetlist(SetlistInfo source) {
        if (source == null || setlistRepository == null) {
            return;
        }
        String suggested = suggestedDuplicateName(source.name());
        SetlistDetailsDialog.showDuplicate(
                        SwingUtilities.getWindowAncestor(this), bandRepository, source, suggested)
                .ifPresent(details -> {
                    try {
                        long newId = setlistRepository.duplicateSetlist(
                                source.id(),
                                details.name(),
                                details.bandLayoutId(),
                                details.locked(),
                                details.switchDelaySeconds(),
                                details.notes(),
                                details.setDate(),
                                details.setTime(),
                                details.targetDurationSeconds());
                        reload();
                        selectSetlistInTree(newId);
                        onTreeSelection();
                    } catch (LibraryException ex) {
                        reload();
                        showError(ex.getMessage());
                    }
                });
    }

    private String suggestedDuplicateName(String sourceName) {
        String base = "Copy of " + (sourceName == null || sourceName.isBlank() ? "setlist" : sourceName.trim());
        if (setlistRepository == null) {
            return base;
        }
        try {
            HashSet<String> existing = new HashSet<>();
            for (SetlistInfo setlist : setlistRepository.listSetlists()) {
                if (setlist.name() != null) {
                    existing.add(setlist.name());
                }
            }
            if (!existing.contains(base)) {
                return base;
            }
            int n = 2;
            while (existing.contains(base + " (" + n + ")")) {
                n++;
            }
            return base + " (" + n + ")";
        } catch (LibraryException ex) {
            return base;
        }
    }

    private void prependCurrentToOther(SetlistInfo current) {
        pickOtherSetlist(
                        current.id(),
                        "Prepend to setlist",
                        "Select setlist to prepend to (current setlist will be copied to its beginning):",
                        true)
                .ifPresent(other -> mergeSetlists(other.id(), current.id(), true));
    }

    private void appendCurrentToOther(SetlistInfo current) {
        pickOtherSetlist(
                        current.id(),
                        "Append to setlist",
                        "Select setlist to append to (current setlist will be copied to its end):",
                        true)
                .ifPresent(other -> mergeSetlists(other.id(), current.id(), false));
    }

    private void prependOtherIntoCurrent(SetlistInfo current) {
        pickOtherSetlist(
                        current.id(),
                        "Prepend from setlist",
                        "Select setlist to copy from (songs will be inserted at the beginning of the current setlist):",
                        false)
                .ifPresent(other -> mergeSetlists(current.id(), other.id(), true));
    }

    private void appendOtherIntoCurrent(SetlistInfo current) {
        pickOtherSetlist(
                        current.id(),
                        "Append from setlist",
                        "Select setlist to copy from (songs will be added to the end of the current setlist):",
                        false)
                .ifPresent(other -> mergeSetlists(current.id(), other.id(), false));
    }

    private Optional<SetlistInfo> pickOtherSetlist(
            long excludeId, String title, String label, boolean unlockedTargetsOnly) {
        if (setlistRepository == null) {
            return Optional.empty();
        }
        try {
            List<SetlistInfo> others = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (SetlistInfo setlist : setlistRepository.listSetlists()) {
                if (setlist.id() == excludeId) {
                    continue;
                }
                if (unlockedTargetsOnly && setlist.locked()) {
                    continue;
                }
                int songCount = setlistRepository.listItems(setlist.id()).size();
                others.add(setlist);
                String lockSuffix = setlist.locked() ? " [locked]" : "";
                labels.add(setlist.name() + " (" + songCount + " songs)" + lockSuffix);
            }
            if (others.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        unlockedTargetsOnly
                                ? "No other unlocked setlists available."
                                : "No other setlists available. Create another setlist first.",
                        title,
                        JOptionPane.INFORMATION_MESSAGE);
                return Optional.empty();
            }
            JComboBox<String> combo = new JComboBox<>(labels.toArray(String[]::new));
            combo.setSelectedIndex(0);
            int result = JOptionPane.showConfirmDialog(
                    this,
                    new Object[] {label, combo},
                    title,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION || combo.getSelectedIndex() < 0) {
                return Optional.empty();
            }
            return Optional.of(others.get(combo.getSelectedIndex()));
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return Optional.empty();
        }
    }

    private void mergeSetlists(long targetId, long sourceId, boolean prepend) {
        if (setlistRepository == null || targetId == sourceId) {
            return;
        }
        try {
            SetlistInfo target = null;
            SetlistInfo source = null;
            for (SetlistInfo setlist : setlistRepository.listSetlists()) {
                if (setlist.id() == targetId) {
                    target = setlist;
                }
                if (setlist.id() == sourceId) {
                    source = setlist;
                }
            }
            if (target == null || source == null) {
                return;
            }
            if (setlistRepository.listItems(sourceId).isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "The selected setlist has no songs.",
                        "Copy setlist",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int added = setlistRepository.mergeSetlistSongs(targetId, sourceId, prepend);
            reload();
            selectSetlistInTree(targetId);
            String action = prepend ? "Prepended" : "Appended";
            JOptionPane.showMessageDialog(
                    this,
                    action + " " + added + " song(s) from \"" + source.name()
                            + "\" into \"" + target.name() + "\".",
                    "Copy setlist",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void toggleSetlistLocked(SetlistInfo setlist) {
        if (setlist == null || setlistRepository == null) {
            return;
        }
        try {
            setlistRepository.updateSetlist(
                    setlist.id(),
                    setlist.name(),
                    setlist.bandLayoutId(),
                    setlist.folderId(),
                    setlist.sortOrder(),
                    !setlist.locked(),
                    setlist.defaultChangeDurationSeconds(),
                    setlist.notes(),
                    setlist.setDate(),
                    setlist.setTime(),
                    setlist.targetDurationSeconds());
            reload();
            selectSetlistInTree(setlist.id());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteSetlist(SetlistInfo setlist) {
        if (setlist == null || setlistRepository == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete setlist \"" + setlist.name() + "\"?",
                "Delete setlist",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            setlistRepository.deleteSetlist(setlist.id());
            reload();
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void addFolder() {
        if (setlistRepository == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Folder name:", "Add folder", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            long id = setlistRepository.addFolder(name.trim());
            reload();
            selectFolderInTree(id);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void addSetlist() {
        if (setlistRepository == null) {
            return;
        }
        Long folderId = selectedFolderId();
        SetlistDetailsDialog.showCreate(SwingUtilities.getWindowAncestor(this), bandRepository)
                .ifPresent(details -> {
                    try {
                        long id = setlistRepository.addSetlist(details.name(), folderId);
                        setlistRepository.updateSetlist(
                                id,
                                details.name(),
                                details.bandLayoutId(),
                                folderId,
                                0,
                                details.locked(),
                                details.switchDelaySeconds(),
                                details.notes(),
                                details.setDate(),
                                details.setTime(),
                                details.targetDurationSeconds());
                        reload();
                        selectSetlistInTree(id);
                    } catch (LibraryException ex) {
                        showError(ex.getMessage());
                    }
                });
    }

    private void deleteCurrentSetlist() {
        deleteSetlist(selectedSetlist());
    }

    private void deleteSelected() {
        if (setlistRepository == null) {
            return;
        }
        SetlistInfo setlist = selectedSetlist();
        if (setlist != null) {
            deleteCurrentSetlist();
            return;
        }
        SetlistFolderInfo folder = selectedFolder();
        if (folder != null) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Delete folder \"" + folder.name() + "\"?",
                    "Delete",
                    JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                setlistRepository.deleteFolder(folder.id());
                reload();
            } catch (LibraryException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void addSong() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || setlistRepository == null || songRepository == null || setlist.locked()) {
            return;
        }
        SongPickerDialog.showPicker(SwingUtilities.getWindowAncestor(this), songRepository, song -> {
            try {
                int position = itemModel.getRowCount();
                setlistRepository.addItem(setlist.id(), song.id(), position, null, null);
                reloadItems(setlist.id());
                if (itemModel.getRowCount() > 0) {
                    int last = itemModel.getRowCount() - 1;
                    itemTable.setRowSelectionInterval(last, last);
                }
            } catch (LibraryException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void removeSong() {
        SetlistInfo setlist = selectedSetlist();
        int[] rows = itemTable.getSelectedRows();
        if (setlist == null || rows.length == 0 || setlistRepository == null || setlist.locked()) {
            return;
        }
        Arrays.sort(rows);
        try {
            for (int i = rows.length - 1; i >= 0; i--) {
                SetlistItemInfo item = itemModel.itemAt(rows[i]);
                setlistRepository.removeItem(item.id());
            }
            reloadItems(setlist.id());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void moveSongs(int delta) {
        SetlistInfo setlist = selectedSetlist();
        int[] selected = itemTable.getSelectedRows();
        if (setlist == null || setlistRepository == null || selected.length == 0 || setlist.locked()) {
            return;
        }
        Arrays.sort(selected);
        if (delta < 0 && selected[0] == 0) {
            return;
        }
        if (delta > 0 && selected[selected.length - 1] >= itemModel.getRowCount() - 1) {
            return;
        }

        List<SetlistItemInfo> current = new ArrayList<>(itemModel.items());
        Set<Integer> selectedSet = new HashSet<>();
        for (int row : selected) {
            selectedSet.add(row);
        }

        if (delta < 0) {
            for (int i = 0; i < current.size(); i++) {
                if (selectedSet.contains(i) && i > 0 && !selectedSet.contains(i - 1)) {
                    SetlistItemInfo item = current.remove(i);
                    current.add(i - 1, item);
                    selectedSet.remove(i);
                    selectedSet.add(i - 1);
                }
            }
        } else {
            for (int i = current.size() - 1; i >= 0; i--) {
                if (selectedSet.contains(i) && i < current.size() - 1 && !selectedSet.contains(i + 1)) {
                    SetlistItemInfo item = current.remove(i);
                    current.add(i + 1, item);
                    selectedSet.remove(i);
                    selectedSet.add(i + 1);
                }
            }
        }

        List<Long> order = new ArrayList<>(current.size());
        for (SetlistItemInfo item : current) {
            order.add(item.id());
        }
        try {
            setlistRepository.reorderItems(setlist.id(), order);
            reloadItems(setlist.id());
            itemTable.clearSelection();
            List<Integer> newSelection = new ArrayList<>(selectedSet);
            newSelection.sort(Integer::compareTo);
            for (int row : newSelection) {
                itemTable.addRowSelectionInterval(row, row);
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private SetlistItemInfo selectedItem() {
        int row = itemTable.getSelectionModel().getLeadSelectionIndex();
        if (row < 0 || row >= itemModel.getRowCount()) {
            row = itemTable.getSelectedRow();
        }
        if (row < 0 || row >= itemModel.getRowCount()) {
            return null;
        }
        return itemModel.itemAt(row);
    }

    private SetlistInfo selectedSetlist() {
        DefaultMutableTreeNode node = selectedTreeNode();
        if (node == null) {
            return null;
        }
        Object user = node.getUserObject();
        if (user instanceof SetlistNode setlistNode) {
            return setlistNode.setlist();
        }
        return null;
    }

    private Long selectedSetlistId() {
        SetlistInfo setlist = selectedSetlist();
        return setlist == null ? null : setlist.id();
    }

    private SetlistFolderInfo selectedFolder() {
        DefaultMutableTreeNode node = selectedTreeNode();
        if (node == null) {
            return null;
        }
        Object user = node.getUserObject();
        if (user instanceof FolderNode folderNode) {
            return folderNode.folder();
        }
        return null;
    }

    private Long selectedFolderId() {
        DefaultMutableTreeNode node = selectedTreeNode();
        if (node != null && node.getUserObject() instanceof FolderNode folderNode) {
            return folderNode.folder() == null ? null : folderNode.folder().id();
        }
        SetlistInfo setlist = selectedSetlist();
        return setlist == null ? null : setlist.folderId();
    }

    private boolean isUnfiledFolderSelected() {
        DefaultMutableTreeNode node = selectedTreeNode();
        return node != null
                && node.getUserObject() instanceof FolderNode folderNode
                && folderNode.folder() == null;
    }

    private DefaultMutableTreeNode selectedTreeNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object last = path.getLastPathComponent();
        return last instanceof DefaultMutableTreeNode node ? node : null;
    }

    /**
     * Select a setlist in the tree (Library Set-column navigation).
     */
    public void navigateToSetlist(long setlistId) {
        selectSetlistInTree(setlistId);
    }

    private void selectSetlistInTree(long setlistId) {
        DefaultMutableTreeNode found = findSetlistNode(treeRoot, setlistId);
        if (found != null) {
            TreePath path = new TreePath(found.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private void selectFolderInTree(long folderId) {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            if (child.getUserObject() instanceof FolderNode folderNode
                    && folderNode.folder() != null
                    && folderNode.folder().id() == folderId) {
                TreePath path = new TreePath(child.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private void selectUnfiledInTree() {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            if (child.getUserObject() instanceof FolderNode folderNode && folderNode.folder() == null) {
                TreePath path = new TreePath(child.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private static DefaultMutableTreeNode findSetlistNode(DefaultMutableTreeNode node, long setlistId) {
        Object user = node.getUserObject();
        if (user instanceof SetlistNode setlistNode && setlistNode.setlist().id() == setlistId) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findSetlistNode((DefaultMutableTreeNode) node.getChildAt(i), setlistId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message == null || message.isBlank() ? "Operation failed." : message,
                "Setlists",
                JOptionPane.ERROR_MESSAGE);
    }

    private record FolderNode(SetlistFolderInfo folder) {
        @Override
        public String toString() {
            return folder == null ? "Unfiled" : folder.name();
        }
    }

    private record SetlistNode(SetlistInfo setlist) {
        @Override
        public String toString() {
            return setlist.name();
        }
    }

    private static final class SetlistTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon lockIcon = PlaybackIcons.lock(14);
        private Icon folderOpenIcon;
        private Icon folderClosedIcon;
        private Icon leafIcon;

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {
            if (folderOpenIcon == null) {
                folderOpenIcon = getOpenIcon();
                folderClosedIcon = getClosedIcon();
                leafIcon = getLeafIcon();
            }
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof SetlistNode setlistNode) {
                setIcon(setlistNode.setlist().locked() ? lockIcon : leafIcon);
            } else if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof FolderNode) {
                setIcon(expanded ? folderOpenIcon : folderClosedIcon);
            }
            return this;
        }
    }

    private static final class ItemTableModel extends AbstractTableModel {
        private final List<SetlistItemInfo> items = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final String[] columns = {"#", "", "", "Title", "Parts", "Duration", "Composer"};

        void setItems(List<SetlistItemInfo> next) {
            items.clear();
            warnings.clear();
            if (next != null) {
                items.addAll(next);
            }
            for (int i = 0; i < items.size(); i++) {
                warnings.add(null);
            }
            fireTableDataChanged();
        }

        void setWarnings(List<String> next) {
            warnings.clear();
            int size = items.size();
            for (int i = 0; i < size; i++) {
                warnings.add(next != null && i < next.size() ? next.get(i) : null);
            }
            if (size > 0) {
                fireTableRowsUpdated(0, size - 1);
            }
        }

        String warningAt(int row) {
            if (row < 0 || row >= warnings.size()) {
                return null;
            }
            return warnings.get(row);
        }

        List<SetlistItemInfo> items() {
            return List.copyOf(items);
        }

        SetlistItemInfo itemAt(int row) {
            return items.get(row);
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0, 4 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SetlistItemInfo item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> "";
                case 2 -> warningAt(rowIndex) == null ? "" : "\u26A0";
                case 3 -> item.songTitle();
                case 4 -> item.partCount();
                case 5 -> LibraryDisplayFormats.formatDuration(item.songDurationSeconds());
                case 6 -> item.songComposers();
                default -> "";
            };
        }
    }
}
