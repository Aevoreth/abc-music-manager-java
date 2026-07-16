package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
import javax.swing.table.AbstractTableModel;
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
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.Preferences;
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
    private static final int[] DEFAULT_COLUMN_WIDTHS = {36, 220, 48, 64, 160};
    private static final int MAIN_SPLIT_INITIAL = 200;
    private static final int MAIN_SPLIT_MIN_LEFT = 100;

    private PlayerRepository playerRepository;
    private BandRepository bandRepository;
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private SongLayoutRepository songLayoutRepository;
    private Preferences preferences;

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Setlists");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);

    private final JTextField nameField = new JTextField(24);
    private final JComboBox<LayoutChoice> layoutCombo = new JComboBox<>();
    private final JTextField setDateField = new JTextField(12);
    private final JTextField setTimeField = new JTextField(8);
    private final JSpinner targetDurationSpinner = DurationSpinners.create(0, 0, 24 * 3600, 1);
    private final JSpinner defaultChangeSpinner = DurationSpinners.create(0, 0, 3600, 1);
    private final JTextArea notesArea = new JTextArea(4, 20);
    private final JCheckBox lockedCheck = new JCheckBox("Locked");
    private final JButton saveMetaButton = new JButton("Save metadata");

    private final ItemTableModel itemModel = new ItemTableModel();
    private final JTable itemTable = new JTable(itemModel);

    private final DefaultListModel<AssignmentRow> assignmentModel = new DefaultListModel<>();
    private final JList<AssignmentRow> assignmentList = new JList<>(assignmentModel);
    private final JComboBox<Integer> partCombo = new JComboBox<>();
    private final JPanel assignmentPanel = new JPanel(new BorderLayout(4, 4));

    private final JPanel editorPanel = new JPanel(new BorderLayout(8, 8));
    private final JPanel metaPanel = new JPanel(new GridBagLayout());
    private final JPanel songsPanel = new JPanel(new BorderLayout(4, 4));
    private final JButton addSongButton = new JButton("Add song");
    private final JButton removeSongButton = new JButton("Remove");
    private final JButton moveUpButton = new JButton("Move up");
    private final JButton moveDownButton = new JButton("Move down");

    private JSplitPane mainSplit;
    private boolean suppressSelection;
    private boolean columnWidthsRestored;
    private boolean mainSplitInitialized;

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

        assignmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assignmentList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AssignmentRow row) {
                    setText(row.label());
                }
                return this;
            }
        });
        assignmentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                syncPartComboFromSelection();
            }
        });

        for (int part = 1; part <= 24; part++) {
            partCombo.addItem(part);
        }
        partCombo.insertItemAt(null, 0);
        partCombo.setSelectedIndex(0);
        partCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "(none)" : "Part " + value);
                return this;
            }
        });

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
        reload();
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
        if (isShowing()) {
            restoreColumnWidths();
            columnWidthsRestored = true;
        }
    }

    public void persistUiState(Preferences preferences) {
        if (preferences == null) {
            return;
        }
        preferences.extras().put(COLUMN_WIDTHS_PREF_KEY, captureColumnWidths());
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

    public void reload() {
        Long selectedSetlistId = selectedSetlistId();
        Long selectedFolderId = selectedFolderId();
        rebuildTree();
        if (selectedSetlistId != null) {
            selectSetlistInTree(selectedSetlistId);
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
        metaPanel.setBorder(BorderFactory.createTitledBorder("Setlist"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addLabeled(metaPanel, gc, row++, "Name", nameField);
        addLabeled(metaPanel, gc, row++, "Band layout", layoutCombo);
        addLabeled(metaPanel, gc, row++, "Set date", setDateField);
        addLabeled(metaPanel, gc, row++, "Set time", setTimeField);
        addLabeled(metaPanel, gc, row++, "Target duration", targetDurationSpinner);
        addLabeled(metaPanel, gc, row++, "Default change", defaultChangeSpinner);

        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        metaPanel.add(new JLabel("Notes"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 1;
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(200, 80));
        metaPanel.add(notesScroll, gc);
        row++;

        gc.gridx = 1;
        gc.gridy = row;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        metaPanel.add(lockedCheck, gc);
        row++;

        gc.gridx = 1;
        gc.gridy = row;
        saveMetaButton.addActionListener(e -> saveMetadata());
        metaPanel.add(saveMetaButton, gc);

        metaPanel.setPreferredSize(new Dimension(260, 280));
        metaPanel.setMinimumSize(new Dimension(180, 120));

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

        assignmentPanel.setBorder(BorderFactory.createTitledBorder("Part assignments"));
        JPanel assignToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton applyPart = new JButton("Apply part");
        applyPart.addActionListener(e -> applyAssignment());
        assignToolbar.add(new JLabel("Part"));
        assignToolbar.add(partCombo);
        assignToolbar.add(applyPart);
        assignmentPanel.add(assignToolbar, BorderLayout.NORTH);
        assignmentPanel.add(new JScrollPane(assignmentList), BorderLayout.CENTER);
        assignmentPanel.setPreferredSize(new Dimension(400, 140));
        assignmentPanel.setMinimumSize(new Dimension(120, 80));

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, metaPanel, songsPanel);
        topSplit.setResizeWeight(0.28);
        topSplit.setContinuousLayout(true);
        topSplit.setDividerLocation(260);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, assignmentPanel);
        editorSplit.setResizeWeight(0.7);
        editorSplit.setContinuousLayout(true);
        editorSplit.setDividerLocation(320);

        editorPanel.setMinimumSize(new Dimension(280, 0));
        editorPanel.add(editorSplit, BorderLayout.CENTER);
    }

    private static void addLabeled(JPanel panel, GridBagConstraints gc, int row, String label, Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        panel.add(field, gc);
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

    private void enableItemTableReorder() {
        itemTable.setDragEnabled(true);
        itemTable.setDropMode(DropMode.INSERT_ROWS);
        itemTable.setTransferHandler(new TransferHandler() {
            private int[] dragRows = new int[0];

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                dragRows = itemTable.getSelectedRows();
                Arrays.sort(dragRows);
                return new StringSelection(Arrays.toString(dragRows));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        && dragRows.length > 0
                        && selectedSetlist() != null;
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
        if (setlist == null || setlistRepository == null || selectedRows.length == 0) {
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
            DefaultMutableTreeNode unfiled = new DefaultMutableTreeNode(new FolderNode(null));
            boolean hasUnfiled = false;
            for (SetlistInfo setlist : setlists) {
                DefaultMutableTreeNode setlistNode = new DefaultMutableTreeNode(new SetlistNode(setlist));
                if (setlist.folderId() == null) {
                    unfiled.add(setlistNode);
                    hasUnfiled = true;
                } else {
                    DefaultMutableTreeNode parent = folderNodes.get(setlist.folderId());
                    if (parent == null) {
                        unfiled.add(setlistNode);
                        hasUnfiled = true;
                    } else {
                        parent.add(setlistNode);
                    }
                }
            }
            if (hasUnfiled) {
                treeRoot.add(unfiled);
            }
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
        nameField.setText("");
        setDateField.setText("");
        setTimeField.setText("");
        targetDurationSpinner.setValue(0);
        defaultChangeSpinner.setValue(0);
        notesArea.setText("");
        lockedCheck.setSelected(false);
        layoutCombo.removeAllItems();
        layoutCombo.addItem(new LayoutChoice(null, "(none)"));
        layoutCombo.setSelectedIndex(0);
        itemModel.setItems(List.of());
        assignmentModel.clear();
        assignmentPanel.setVisible(true);
        revalidate();
        repaint();
    }

    private void setEditorEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        layoutCombo.setEnabled(enabled);
        setDateField.setEnabled(enabled);
        setTimeField.setEnabled(enabled);
        targetDurationSpinner.setEnabled(enabled);
        defaultChangeSpinner.setEnabled(enabled);
        notesArea.setEnabled(enabled);
        lockedCheck.setEnabled(enabled);
        saveMetaButton.setEnabled(enabled);
        addSongButton.setEnabled(enabled);
        removeSongButton.setEnabled(enabled);
        moveUpButton.setEnabled(enabled);
        moveDownButton.setEnabled(enabled);
        itemTable.setEnabled(enabled);
        assignmentList.setEnabled(enabled);
        partCombo.setEnabled(enabled);
    }

    private void loadSetlistEditor(SetlistInfo setlist) {
        nameField.setText(nullToEmpty(setlist.name()));
        setDateField.setText(nullToEmpty(setlist.setDate()));
        setTimeField.setText(nullToEmpty(setlist.setTime()));
        targetDurationSpinner.setValue(
                setlist.targetDurationSeconds() == null ? 0 : setlist.targetDurationSeconds());
        defaultChangeSpinner.setValue(
                setlist.defaultChangeDurationSeconds() == null ? 0 : setlist.defaultChangeDurationSeconds());
        notesArea.setText(nullToEmpty(setlist.notes()));
        lockedCheck.setSelected(setlist.locked());
        reloadLayoutChoices(setlist.bandLayoutId());
        reloadItems(setlist.id());
        boolean hasLayout = setlist.bandLayoutId() != null;
        assignmentPanel.setVisible(hasLayout);
        if (!hasLayout) {
            assignmentModel.clear();
        }
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
            return;
        }
        try {
            itemModel.setItems(setlistRepository.listItems(setlistId));
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            itemModel.setItems(List.of());
        }
        reloadAssignments();
    }

    private void reloadAssignments() {
        assignmentModel.clear();
        SetlistInfo setlist = selectedSetlist();
        SetlistItemInfo item = selectedItem();
        if (setlist == null || item == null || setlist.bandLayoutId() == null || bandRepository == null
                || setlistRepository == null) {
            return;
        }
        try {
            List<BandLayoutSlotInfo> slots = bandRepository.listSlots(setlist.bandLayoutId());
            Map<Long, Integer> assigned = new HashMap<>();
            for (SetlistBandAssignmentInfo info : setlistRepository.listBandAssignments(item.id())) {
                assigned.put(info.playerId(), info.partNumber());
            }
            for (BandLayoutSlotInfo slot : slots) {
                assignmentModel.addElement(new AssignmentRow(
                        slot.playerId(),
                        slot.playerName(),
                        assigned.get(slot.playerId())));
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
        if (!assignmentModel.isEmpty()) {
            assignmentList.setSelectedIndex(0);
        }
    }

    private void syncPartComboFromSelection() {
        AssignmentRow row = assignmentList.getSelectedValue();
        if (row == null) {
            return;
        }
        partCombo.setSelectedItem(row.partNumber());
    }

    private void applyAssignment() {
        SetlistItemInfo item = selectedItem();
        AssignmentRow row = assignmentList.getSelectedValue();
        if (item == null || row == null || setlistRepository == null) {
            return;
        }
        Integer part = (Integer) partCombo.getSelectedItem();
        try {
            setlistRepository.upsertBandAssignment(item.id(), row.playerId(), part);
            reloadAssignments();
            for (int i = 0; i < assignmentModel.size(); i++) {
                if (assignmentModel.get(i).playerId() == row.playerId()) {
                    assignmentList.setSelectedIndex(i);
                    break;
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void saveMetadata() {
        SetlistInfo setlist = selectedSetlist();
        if (setlist == null || setlistRepository == null) {
            return;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Setlist", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LayoutChoice layout = (LayoutChoice) layoutCombo.getSelectedItem();
        Long layoutId = layout == null ? null : layout.id();
        int target = ((Number) targetDurationSpinner.getValue()).intValue();
        int change = ((Number) defaultChangeSpinner.getValue()).intValue();
        try {
            setlistRepository.updateSetlist(
                    setlist.id(),
                    name,
                    layoutId,
                    setlist.folderId(),
                    setlist.sortOrder(),
                    lockedCheck.isSelected(),
                    change,
                    notesArea.getText(),
                    blankToNull(setDateField.getText()),
                    blankToNull(setTimeField.getText()),
                    target);
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
        String name = JOptionPane.showInputDialog(this, "Setlist name:", "Add setlist", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        Long folderId = selectedFolderId();
        try {
            long id = setlistRepository.addSetlist(name.trim(), folderId);
            reload();
            selectSetlistInTree(id);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
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
        if (setlist == null || setlistRepository == null || songRepository == null) {
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
        if (setlist == null || rows.length == 0 || setlistRepository == null) {
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
        if (setlist == null || setlistRepository == null || selected.length == 0) {
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
        SetlistFolderInfo folder = selectedFolder();
        if (folder != null) {
            return folder.id();
        }
        SetlistInfo setlist = selectedSetlist();
        return setlist == null ? null : setlist.folderId();
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

    private record LayoutChoice(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record AssignmentRow(long playerId, String playerName, Integer partNumber) {
        String label() {
            String name = playerName == null || playerName.isBlank() ? ("#" + playerId) : playerName;
            if (partNumber == null) {
                return name + " — (none)";
            }
            return name + " — Part " + partNumber;
        }
    }

    private static final class ItemTableModel extends AbstractTableModel {
        private final List<SetlistItemInfo> items = new ArrayList<>();
        private final String[] columns = {"#", "Title", "Parts", "Duration", "Composer"};

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
                case 0, 2 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SetlistItemInfo item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.position() + 1;
                case 1 -> item.songTitle();
                case 2 -> item.partCount();
                case 3 -> LibraryDisplayFormats.formatDuration(item.songDurationSeconds());
                case 4 -> item.songComposers();
                default -> "";
            };
        }
    }
}
