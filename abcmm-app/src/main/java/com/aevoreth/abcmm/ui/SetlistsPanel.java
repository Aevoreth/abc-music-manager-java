package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
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
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Setlist folders, metadata editor, song order, and per-item part assignments.
 */
public final class SetlistsPanel extends JPanel {

    private PlayerRepository playerRepository;
    private BandRepository bandRepository;
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private SongLayoutRepository songLayoutRepository;

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Setlists");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);

    private final JTextField nameField = new JTextField(24);
    private final JComboBox<LayoutChoice> layoutCombo = new JComboBox<>();
    private final JTextField setDateField = new JTextField(12);
    private final JTextField setTimeField = new JTextField(8);
    private final JSpinner targetDurationSpinner = DurationSpinners.create(0, 0, 24 * 3600, 1);
    private final JSpinner defaultChangeSpinner = DurationSpinners.create(0, 0, 3600, 1);
    private final JTextArea notesArea = new JTextArea(3, 24);
    private final JCheckBox lockedCheck = new JCheckBox("Locked");

    private final ItemTableModel itemModel = new ItemTableModel();
    private final JTable itemTable = new JTable(itemModel);

    private final DefaultListModel<AssignmentRow> assignmentModel = new DefaultListModel<>();
    private final JList<AssignmentRow> assignmentList = new JList<>(assignmentModel);
    private final JComboBox<Integer> partCombo = new JComboBox<>();
    private final JPanel assignmentPanel = new JPanel(new BorderLayout(4, 4));

    private final JPanel editorPanel = new JPanel(new BorderLayout(8, 8));
    private boolean suppressSelection;

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

        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadAssignments();
            }
        });

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
        editorPanel.setVisible(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorPanel);
        split.setResizeWeight(0.28);
        add(split, BorderLayout.CENTER);
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
        panel.setPreferredSize(new Dimension(240, 400));
        return panel;
    }

    private void buildEditorPane() {
        JPanel meta = new JPanel(new GridBagLayout());
        meta.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addLabeled(meta, gc, row++, "Name", nameField);
        addLabeled(meta, gc, row++, "Band layout", layoutCombo);
        addLabeled(meta, gc, row++, "Set date", setDateField);
        addLabeled(meta, gc, row++, "Set time", setTimeField);
        addLabeled(meta, gc, row++, "Target duration", targetDurationSpinner);
        addLabeled(meta, gc, row++, "Default change", defaultChangeSpinner);

        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        meta.add(new JLabel("Notes"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 0.2;
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        meta.add(new JScrollPane(notesArea), gc);
        row++;

        gc.gridx = 1;
        gc.gridy = row;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.NONE;
        meta.add(lockedCheck, gc);
        row++;

        gc.gridx = 1;
        gc.gridy = row;
        JButton saveMeta = new JButton("Save metadata");
        saveMeta.addActionListener(e -> saveMetadata());
        meta.add(saveMeta, gc);

        JPanel songs = new JPanel(new BorderLayout(4, 4));
        songs.setBorder(BorderFactory.createTitledBorder("Songs"));
        JPanel songToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addSong = new JButton("Add song");
        JButton removeSong = new JButton("Remove");
        JButton moveUp = new JButton("Move up");
        JButton moveDown = new JButton("Move down");
        addSong.addActionListener(e -> addSong());
        removeSong.addActionListener(e -> removeSong());
        moveUp.addActionListener(e -> moveSong(-1));
        moveDown.addActionListener(e -> moveSong(1));
        songToolbar.add(addSong);
        songToolbar.add(removeSong);
        songToolbar.add(moveUp);
        songToolbar.add(moveDown);
        songs.add(songToolbar, BorderLayout.NORTH);
        songs.add(new JScrollPane(itemTable), BorderLayout.CENTER);

        assignmentPanel.setBorder(BorderFactory.createTitledBorder("Part assignments"));
        JPanel assignToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton applyPart = new JButton("Apply part");
        applyPart.addActionListener(e -> applyAssignment());
        assignToolbar.add(new JLabel("Part"));
        assignToolbar.add(partCombo);
        assignToolbar.add(applyPart);
        assignmentPanel.add(assignToolbar, BorderLayout.NORTH);
        assignmentPanel.add(new JScrollPane(assignmentList), BorderLayout.CENTER);
        assignmentPanel.setPreferredSize(new Dimension(220, 160));

        JSplitPane songsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, songs, assignmentPanel);
        songsSplit.setResizeWeight(0.7);

        editorPanel.add(meta, BorderLayout.NORTH);
        editorPanel.add(songsSplit, BorderLayout.CENTER);
    }

    private static void addLabeled(JPanel panel, GridBagConstraints gc, int row, String label, Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        panel.add(field, gc);
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
            editorPanel.setVisible(false);
            itemModel.setItems(List.of());
            assignmentModel.clear();
            return;
        }
        editorPanel.setVisible(true);
        loadSetlistEditor(setlist);
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
        SetlistItemInfo item = selectedItem();
        if (setlist == null || item == null || setlistRepository == null) {
            return;
        }
        try {
            setlistRepository.removeItem(item.id());
            reloadItems(setlist.id());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void moveSong(int delta) {
        SetlistInfo setlist = selectedSetlist();
        int index = itemTable.getSelectedRow();
        int target = index + delta;
        if (setlist == null || setlistRepository == null || index < 0 || target < 0
                || target >= itemModel.getRowCount()) {
            return;
        }
        List<Long> order = new ArrayList<>();
        for (SetlistItemInfo item : itemModel.items()) {
            order.add(item.id());
        }
        Long moved = order.remove(index);
        order.add(target, moved);
        try {
            setlistRepository.reorderItems(setlist.id(), order);
            reloadItems(setlist.id());
            itemTable.setRowSelectionInterval(target, target);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private SetlistItemInfo selectedItem() {
        int row = itemTable.getSelectedRow();
        if (row < 0) {
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
        private final String[] columns = {"#", "Title", "Duration", "Change override"};

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
        public Object getValueAt(int rowIndex, int columnIndex) {
            SetlistItemInfo item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.position() + 1;
                case 1 -> item.songTitle();
                case 2 -> LibraryDisplayFormats.formatDuration(item.songDurationSeconds());
                case 3 -> item.overrideChangeDurationSeconds() == null
                        ? ""
                        : LibraryDisplayFormats.formatDuration(item.overrideChangeDurationSeconds());
                default -> "";
            };
        }
    }
}
