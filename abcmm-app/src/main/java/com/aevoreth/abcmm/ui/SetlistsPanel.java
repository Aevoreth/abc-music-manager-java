package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.playback.PlayQueueItem;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackSession;
import com.aevoreth.abcmm.domain.prefs.Preferences;
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
    private static final int[] DEFAULT_COLUMN_WIDTHS = {48, 36, 220, 48, 64, 160};
    private static final int MAIN_SPLIT_INITIAL = 200;
    private static final int MAIN_SPLIT_MIN_LEFT = 100;
    private static final int META_SPLIT_DEFAULT = 360;
    private static final int META_SPLIT_MIN = 240;

    private PlayerRepository playerRepository;
    private BandRepository bandRepository;
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private SongLayoutRepository songLayoutRepository;
    private Preferences preferences;
    private PlaybackSession playbackSession;
    private Consumer<String> playbackErrorReporter = msg -> {
    };

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Setlists");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);

    private final JTextField nameField = new JTextField(20);
    private final JComboBox<LayoutChoice> layoutCombo = new JComboBox<>();
    private final CalendarDatePicker datePicker = new CalendarDatePicker();
    private final TimeChooser timeChooser = new TimeChooser();
    private final JSpinner targetDurationSpinner = DurationSpinners.createHoursMinutes(0, 0, 24 * 3600);
    private final JSpinner switchDelaySpinner = DurationSpinners.createPaddedInt(
            SetlistDetailsDialog.DEFAULT_SWITCH_DELAY_SECONDS, 0, 300, 1);
    private final JLabel rawDurationValue = new JLabel("\u2014");
    private final JLabel actualDurationValue = new JLabel("\u2014");
    private final JLabel remainingValue = new JLabel("\u2014");
    private final JTextArea notesArea = new JTextArea(4, 20);
    private final JCheckBox lockedCheck = new JCheckBox("Locked");
    private final JButton saveMetaButton = new JButton("Save");

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
    private boolean suppressDurationUpdate;

    public SetlistsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> {
            if (!suppressSelection) {
                onTreeSelection();
            }
        });
        enableTreeReorder();

        itemTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        itemTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        itemTable.setFillsViewportHeight(true);
        itemTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadAssignments();
            }
        });
        applyDefaultColumnWidths();
        enableItemTableReorder();
        enableItemTablePlaybackActions();

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

    public void persistUiState(Preferences preferences) {
        if (preferences == null) {
            return;
        }
        preferences.extras().put(COLUMN_WIDTHS_PREF_KEY, captureColumnWidths());
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
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addFolder = new JButton("Add folder");
        JButton addSetlist = new JButton("Add setlist");
        JButton delete = new JButton("Delete");
        addFolder.addActionListener(e -> addFolder());
        addSetlist.addActionListener(e -> addSetlist());
        delete.addActionListener(e -> deleteSelected());
        toolbar.add(addFolder);
        toolbar.add(addSetlist);
        toolbar.add(delete);
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
        saveMetaButton.addActionListener(e -> saveMetadata());
        ChangeListener durationListener = e -> {
            if (!suppressDurationUpdate) {
                updateDurationSummary();
            }
        };
        targetDurationSpinner.addChangeListener(durationListener);
        switchDelaySpinner.addChangeListener(durationListener);

        metaPanel.add(inlineField("Set Name", nameField));
        metaPanel.add(inlineField("Band Layout", layoutCombo));

        JPanel dateTimeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateTimeRow.setOpaque(false);
        dateTimeRow.add(new JLabel("Set Date / Time"));
        dateTimeRow.add(datePicker);
        dateTimeRow.add(new JLabel("at"));
        dateTimeRow.add(timeChooser);
        metaPanel.add(flowRow(dateTimeRow));

        JPanel targetSwitchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        targetSwitchRow.setOpaque(false);
        targetSwitchRow.add(new JLabel("Target Duration"));
        targetSwitchRow.add(targetDurationSpinner);
        targetSwitchRow.add(new JLabel("Switch delay (s)"));
        targetSwitchRow.add(switchDelaySpinner);
        metaPanel.add(flowRow(targetSwitchRow));

        Font summaryFont = rawDurationValue.getFont().deriveFont(Font.PLAIN);
        rawDurationValue.setFont(summaryFont);
        actualDurationValue.setFont(summaryFont);
        remainingValue.setFont(summaryFont);
        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setOpaque(false);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        summary.add(rawDurationValue);
        summary.add(Box.createVerticalStrut(2));
        summary.add(actualDurationValue);
        summary.add(Box.createVerticalStrut(2));
        summary.add(remainingValue);
        metaPanel.add(summary);

        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(200, 80));
        notesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        metaPanel.add(stackedField("Set Notes", notesScroll));

        JPanel lockedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lockedRow.setOpaque(false);
        lockedRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockedRow.add(lockedCheck);
        lockedRow.add(saveMetaButton);
        metaPanel.add(lockedRow);
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

    private static JPanel stackedField(String label, Component field) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel caption = new JLabel(label);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(caption);
        panel.add(Box.createVerticalStrut(3));
        if (field instanceof JComponent component) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension preferred = component.getPreferredSize();
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(preferred.height, 24)));
        }
        panel.add(field);
        panel.add(Box.createVerticalStrut(12));
        return panel;
    }

    private void applyDefaultColumnWidths() {
        TableColumnModel columns = itemTable.getColumnModel();
        for (int i = 0; i < DEFAULT_COLUMN_WIDTHS.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(DEFAULT_COLUMN_WIDTHS[i]);
        }
    }

    private void restoreColumnWidths() {
        if (preferences == null) {
            return;
        }
        Object raw = preferences.extras().get(COLUMN_WIDTHS_PREF_KEY);
        List<Integer> widths = asIntegerList(raw);
        if (widths == null || widths.isEmpty()) {
            applyDefaultColumnWidths();
            return;
        }
        TableColumnModel columns = itemTable.getColumnModel();
        for (int i = 0; i < widths.size() && i < columns.getColumnCount(); i++) {
            Integer width = widths.get(i);
            if (width != null && width > 0) {
                TableColumn column = columns.getColumn(i);
                column.setPreferredWidth(width);
                column.setWidth(width);
            }
        }
    }

    private List<Integer> captureColumnWidths() {
        TableColumnModel columns = itemTable.getColumnModel();
        List<Integer> widths = new ArrayList<>(columns.getColumnCount());
        for (int i = 0; i < columns.getColumnCount(); i++) {
            widths.add(columns.getColumn(i).getWidth());
        }
        return widths;
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
        suppressDurationUpdate = true;
        try {
            nameField.setText("");
            datePicker.setIsoDate(null);
            timeChooser.setHhMm("19:00");
            targetDurationSpinner.setValue(0);
            switchDelaySpinner.setValue(SetlistDetailsDialog.DEFAULT_SWITCH_DELAY_SECONDS);
            notesArea.setText("");
            lockedCheck.setSelected(false);
            layoutCombo.removeAllItems();
            layoutCombo.addItem(new LayoutChoice(null, "(none)"));
            layoutCombo.setSelectedIndex(0);
            rawDurationValue.setText("Raw duration (no switch delays): \u2014");
            actualDurationValue.setText("Actual duration: \u2014");
            remainingValue.setText("Time remaining: \u2014");
        } finally {
            suppressDurationUpdate = false;
        }
        itemModel.setItems(List.of());
        assignmentPanel.clear();
        revalidate();
        repaint();
    }

    private void setEditorEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        layoutCombo.setEnabled(enabled);
        datePicker.setEnabled(enabled);
        timeChooser.setEnabled(enabled);
        targetDurationSpinner.setEnabled(enabled);
        switchDelaySpinner.setEnabled(enabled);
        notesArea.setEnabled(enabled);
        lockedCheck.setEnabled(enabled);
        saveMetaButton.setEnabled(enabled);
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
        suppressDurationUpdate = true;
        try {
            nameField.setText(nullToEmpty(setlist.name()));
            datePicker.setIsoDate(setlist.setDate());
            timeChooser.setHhMm(setlist.setTime());
            targetDurationSpinner.setValue(
                    setlist.targetDurationSeconds() == null ? 0 : setlist.targetDurationSeconds());
            switchDelaySpinner.setValue(
                    setlist.defaultChangeDurationSeconds() == null
                            ? 0
                            : setlist.defaultChangeDurationSeconds());
            notesArea.setText(nullToEmpty(setlist.notes()));
            lockedCheck.setSelected(setlist.locked());
            reloadLayoutChoices(setlist.bandLayoutId());
        } finally {
            suppressDurationUpdate = false;
        }
        setEditorEnabled(true);
        reloadItems(setlist.id());
        revalidate();
        repaint();
    }

    private void reloadLayoutChoices(Long selectedLayoutId) {
        layoutCombo.removeAllItems();
        layoutCombo.addItem(new LayoutChoice(null, "(none)"));
        if (bandRepository == null) {
            layoutCombo.setSelectedIndex(0);
            return;
        }
        try {
            for (BandInfo band : bandRepository.listBands()) {
                BandLayoutInfo layout = bandRepository.getOrCreatePrimaryLayout(band.id());
                layoutCombo.addItem(new LayoutChoice(layout.id(), band.name()));
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
        int select = 0;
        for (int i = 0; i < layoutCombo.getItemCount(); i++) {
            LayoutChoice choice = layoutCombo.getItemAt(i);
            if (Objects.equals(choice.id(), selectedLayoutId)) {
                select = i;
                break;
            }
        }
        layoutCombo.setSelectedIndex(select);
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
            rawDurationValue.setText("Raw duration (no switch delays): \u2014");
            actualDurationValue.setText("Actual duration: \u2014");
            remainingValue.setText("Time remaining: \u2014");
            return;
        }
        int songSeconds = 0;
        for (SetlistItemInfo item : items) {
            Integer duration = item.songDurationSeconds();
            if (duration != null) {
                songSeconds += Math.max(0, duration);
            }
        }
        int delay = Math.max(0, ((Number) switchDelaySpinner.getValue()).intValue());
        int target = Math.max(0, ((Number) targetDurationSpinner.getValue()).intValue());
        int switchSeconds = items.size() > 1 ? delay * (items.size() - 1) : 0;
        int actualWithSwitches = songSeconds + switchSeconds;
        rawDurationValue.setText("Raw duration (no switch delays): "
                + LibraryDisplayFormats.formatSignedDuration(songSeconds));
        actualDurationValue.setText("Actual duration: "
                + LibraryDisplayFormats.formatSignedDuration(actualWithSwitches)
                + " with switch delays");
        if (target <= 0) {
            remainingValue.setText("Time remaining: \u2014");
        } else {
            remainingValue.setText("Time remaining: "
                    + LibraryDisplayFormats.formatSignedDuration(target - actualWithSwitches));
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
                    item.songId(), item.songTitle(), setlistId, item.id()));
        }
        playbackSession.syncFromSetlistIfActive(setlistId, items);
    }

    private void enableItemTablePlaybackActions() {
        itemTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
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
        itemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) {
                    return;
                }
                int viewRow = itemTable.rowAtPoint(e.getPoint());
                int viewCol = itemTable.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol != 0) {
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
                    item.songId(), item.songTitle(), setlist.id(), item.id()));
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
            playbackSession.enqueue(item.songId(), item.songTitle());
        } catch (PlaybackException ex) {
            playbackErrorReporter.accept(ex.getMessage());
        }
    }

    private void reloadAssignments() {
        SetlistInfo setlist = selectedSetlist();
        SetlistItemInfo item = selectedItem();
        if (setlist == null) {
            assignmentPanel.clear();
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
    }

    /**
     * Create or reuse a song layout for {@code (song, band layout)}, link it on the setlist
     * item, and seed null assignments for layout players when the layout is new/empty.
     */
    private Long ensureSongLayout(SetlistItemInfo item, long bandLayoutId) throws LibraryException {
        if (songLayoutRepository == null || setlistRepository == null || bandRepository == null) {
            return item.songLayoutId();
        }
        SongLayoutInfo layout = songLayoutRepository.getOrCreateSongLayout(
                item.songId(), bandLayoutId, "Default");
        List<SongLayoutAssignmentInfo> existing = songLayoutRepository.listAssignments(layout.id());
        if (existing.isEmpty()) {
            for (BandLayoutSlotInfo slot : bandRepository.listSlots(bandLayoutId)) {
                songLayoutRepository.setAssignment(layout.id(), slot.playerId(), null);
            }
        }
        if (!Objects.equals(item.songLayoutId(), layout.id())) {
            setlistRepository.updateItem(
                    item.id(), item.overrideChangeDurationSeconds(), layout.id());
        }
        return layout.id();
    }

    private void saveMetadata() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || setlistRepository == null) {
            return;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Set Name is required.", "Setlist", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LayoutChoice layout = (LayoutChoice) layoutCombo.getSelectedItem();
        Long layoutId = layout == null ? null : layout.id();
        int target = ((Number) targetDurationSpinner.getValue()).intValue();
        int delay = ((Number) switchDelaySpinner.getValue()).intValue();
        try {
            setlistRepository.updateSetlist(
                    setlist.id(),
                    name,
                    layoutId,
                    setlist.folderId(),
                    setlist.sortOrder(),
                    lockedCheck.isSelected(),
                    delay,
                    blankToNull(notesArea.getText()),
                    datePicker.getIsoDate(),
                    timeChooser.getHhMm(),
                    target <= 0 ? null : target);
            reload();
            selectSetlistInTree(setlist.id());
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

    private void deleteSelected() {
        if (setlistRepository == null) {
            return;
        }
        SetlistInfo setlist = selectedSetlist();
        if (setlist != null) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Delete setlist \"" + setlist.name() + "\"?",
                    "Delete",
                    JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                setlistRepository.deleteSetlist(setlist.id());
                reload();
            } catch (LibraryException ex) {
                showError(ex.getMessage());
            }
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
        SongPickerDialog.showPicker(SwingUtilities.getWindowAncestor(this), songRepository)
                .ifPresent(song -> {
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record LayoutChoice(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
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

    private static final class ItemTableModel extends AbstractTableModel {
        private final List<SetlistItemInfo> items = new ArrayList<>();
        private final String[] columns = {"", "#", "Title", "Parts", "Duration", "Composer"};

        void setItems(List<SetlistItemInfo> next) {
            items.clear();
            if (next != null) {
                items.addAll(next);
            }
            fireTableDataChanged();
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
                case 1, 3 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SetlistItemInfo item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> "";
                case 1 -> item.position() + 1;
                case 2 -> item.songTitle();
                case 3 -> item.partCount();
                case 4 -> LibraryDisplayFormats.formatDuration(item.songDurationSeconds());
                case 5 -> item.songComposers();
                default -> "";
            };
        }
    }
}
