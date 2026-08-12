package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.aevoreth.abcmm.domain.scan.AbcDiff;
import com.aevoreth.abcmm.domain.scan.DuplicateAnalysis;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlan;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlanValidator;
import com.aevoreth.abcmm.domain.scan.DuplicateFile;
import com.aevoreth.abcmm.domain.scan.DuplicateGroup;
import com.aevoreth.abcmm.domain.scan.DuplicateMatchType;
import com.aevoreth.abcmm.domain.scan.DuplicateReviewResult;
import com.aevoreth.abcmm.domain.scan.FileDisposition;
import com.aevoreth.abcmm.domain.scan.FileResolution;
import com.aevoreth.abcmm.domain.scan.FolderDisposition;
import com.aevoreth.abcmm.domain.scan.FolderDuplicateCluster;
import com.aevoreth.abcmm.domain.scan.FolderResolution;

/**
 * Batch review of peer duplicate groups and folder clusters.
 * Folders are reviewed separately from individual file groups; file groups fully covered by
 * folder clusters are deferred until folders are resolved (or shown under a secondary section).
 */
public final class DuplicateReviewDialog extends JDialog {

    private final DuplicateAnalysis analysis;
    private final AtomicReference<DuplicateReviewResult> result =
            new AtomicReference<>(DuplicateReviewResult.cancelled());

    private final Map<String, Map<Path, FileDisposition>> fileDispositions = new LinkedHashMap<>();
    private final Map<String, Map<Path, FolderDisposition>> folderDispositions = new LinkedHashMap<>();

    private final JTree groupTree;
    private final DefaultMutableTreeNode rootNode;
    private final PeerTableModel peerModel = new PeerTableModel();
    private final JTable peerTable = new JTable(peerModel);
    private final JTextArea detailArea = new JTextArea();
    private final JEditorPane diffPane = new JEditorPane("text/html", "");
    private final JLabel statusLabel = new JLabel();
    private final JComboBox<FolderDisposition> folderActionCombo = new JComboBox<>(FolderDisposition.values());

    private Object selectedNodeUserObject;

    public DuplicateReviewDialog(java.awt.Window owner, DuplicateAnalysis analysis) {
        super(owner, "Review duplicates", ModalityType.APPLICATION_MODAL);
        this.analysis = Objects.requireNonNull(analysis, "analysis");

        for (DuplicateGroup group : analysis.groups()) {
            Map<Path, FileDisposition> map = new LinkedHashMap<>();
            for (DuplicateFile file : group.files()) {
                map.put(file.path(), null);
            }
            fileDispositions.put(group.groupId(), map);
        }
        for (FolderDuplicateCluster cluster : analysis.folderClusters()) {
            Map<Path, FolderDisposition> map = new LinkedHashMap<>();
            for (Path folder : cluster.folderPaths()) {
                map.put(folder, null);
            }
            folderDispositions.put(cluster.clusterId(), map);
        }

        rootNode = new DefaultMutableTreeNode("Duplicates");
        buildTree();
        groupTree = new JTree(new DefaultTreeModel(rootNode));
        groupTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        groupTree.addTreeSelectionListener(e -> onTreeSelection());
        expandFolderSection();

        peerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerTable.setRowHeight(24);
        installFileDispositionEditor();
        peerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailAndDiff();
            }
        });

        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        diffPane.setEditable(false);

        folderActionCombo.setRenderer(new FolderDispositionRenderer());

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Review folders first, then individual files"), BorderLayout.NORTH);
        left.add(new JScrollPane(groupTree), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        JPanel folderBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        folderBar.add(new JLabel("Folder action:"));
        folderBar.add(folderActionCombo);
        JButton applyFolder = new JButton("Set on selected folder");
        applyFolder.addActionListener(e -> applyFolderAction());
        folderBar.add(applyFolder);
        right.add(folderBar, BorderLayout.NORTH);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(peerTable),
                new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                        new JScrollPane(detailArea),
                        new JScrollPane(diffPane)));
        rightSplit.setResizeWeight(0.45);
        right.add(rightSplit, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.34);

        JPanel south = new JPanel(new BorderLayout());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        south.add(statusLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ignoreRemaining = new JButton("Ignore remaining");
        ignoreRemaining.addActionListener(e -> ignoreUnresolved());
        JButton applyRescan = new JButton("Apply rules and rescan");
        applyRescan.setToolTipText(
                "Apply decided folder/file rules now, then re-inventory so remaining individual duplicates are clearer.");
        applyRescan.addActionListener(e -> applyRulesAndRescan());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            result.set(DuplicateReviewResult.cancelled());
            dispose();
        });
        JButton ok = new JButton("Finish…");
        ok.addActionListener(e -> confirmAndClose());
        buttons.add(ignoreRemaining);
        buttons.add(applyRescan);
        buttons.add(cancel);
        buttons.add(ok);
        south.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(split, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(1100, 700));
        pack();
        setLocationRelativeTo(owner);
        refreshStatus();
        selectFirstLeaf();
    }

    public DuplicateReviewResult showAndWait() {
        setVisible(true);
        return result.get();
    }

    private void buildTree() {
        rootNode.removeAllChildren();

        if (!analysis.folderClusters().isEmpty()) {
            DefaultMutableTreeNode folders = new DefaultMutableTreeNode(
                    "1. Duplicated folders (" + analysis.folderClusters().size() + ")");
            for (FolderDuplicateCluster cluster : analysis.folderClusters()) {
                String label = describeCluster(cluster);
                folders.add(new DefaultMutableTreeNode(new FolderNode(cluster, label)));
            }
            rootNode.add(folders);
        }

        List<DuplicateGroup> individual = new ArrayList<>();
        List<DuplicateGroup> covered = new ArrayList<>();
        for (DuplicateGroup group : analysis.groups()) {
            if (isFullyCoveredByFolderCluster(group)) {
                covered.add(group);
            } else {
                individual.add(group);
            }
        }

        DefaultMutableTreeNode individualRoot = new DefaultMutableTreeNode(
                "2. Individual file groups (" + individual.size() + ")");
        addGroupsByType(individualRoot, individual);
        if (individualRoot.getChildCount() > 0) {
            rootNode.add(individualRoot);
        }

        if (!covered.isEmpty()) {
            DefaultMutableTreeNode coveredRoot = new DefaultMutableTreeNode(
                    "Covered by folder duplicates — resolve folders first, or Apply rules and rescan ("
                            + covered.size() + ")");
            addGroupsByType(coveredRoot, covered);
            rootNode.add(coveredRoot);
        }
    }

    private void addGroupsByType(DefaultMutableTreeNode parent, List<DuplicateGroup> groups) {
        EnumMap<DuplicateMatchType, DefaultMutableTreeNode> sections = new EnumMap<>(DuplicateMatchType.class);
        sections.put(DuplicateMatchType.EXACT_FILE, new DefaultMutableTreeNode("Exact matches"));
        sections.put(DuplicateMatchType.STRONG_METADATA_MATCH, new DefaultMutableTreeNode("Likely matches"));
        sections.put(DuplicateMatchType.AMBIGUOUS, new DefaultMutableTreeNode("Ambiguous"));
        for (DuplicateGroup group : groups) {
            String label = group.displayTitle() + "  (" + group.files().size() + " copies)";
            sections.get(group.matchType()).add(new DefaultMutableTreeNode(new GroupNode(group, label)));
        }
        for (DuplicateMatchType type : DuplicateMatchType.values()) {
            DefaultMutableTreeNode section = sections.get(type);
            if (section.getChildCount() > 0) {
                parent.add(section);
            }
        }
    }

    private boolean isFullyCoveredByFolderCluster(DuplicateGroup group) {
        if (analysis.folderClusters().isEmpty() || group.files().isEmpty()) {
            return false;
        }
        for (FolderDuplicateCluster cluster : analysis.folderClusters()) {
            boolean allUnder = group.files().stream().allMatch(file ->
                    cluster.folderPaths().stream().anyMatch(folder -> pathUnder(file.path(), folder)));
            if (allUnder) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathUnder(Path path, Path folder) {
        Path p = path.toAbsolutePath().normalize();
        Path f = folder.toAbsolutePath().normalize();
        return p.startsWith(f);
    }

    private static String describeCluster(FolderDuplicateCluster cluster) {
        String names = cluster.folderPaths().stream()
                .map(p -> p.getFileName() == null ? p.toString() : p.getFileName().toString())
                .reduce((a, b) -> a + " ↔ " + b)
                .orElse("folders");
        return names + "  ·  " + cluster.identicalFileCount() + " identical"
                + (cluster.differingFileCount() > 0 ? ", " + cluster.differingFileCount() + " differing" : "")
                + (cluster.uniqueFileCount() > 0 ? ", " + cluster.uniqueFileCount() + " unique" : "");
    }

    private void expandFolderSection() {
        if (rootNode.getChildCount() > 0) {
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) rootNode.getFirstChild();
            groupTree.expandPath(new TreePath(first.getPath()));
        }
    }

    private void selectFirstLeaf() {
        DefaultMutableTreeNode leaf = firstLeaf(rootNode);
        if (leaf != null) {
            groupTree.setSelectionPath(new TreePath(leaf.getPath()));
        }
    }

    private static DefaultMutableTreeNode firstLeaf(DefaultMutableTreeNode node) {
        if (node.isLeaf() && node != node.getRoot()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = firstLeaf((DefaultMutableTreeNode) node.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void onTreeSelection() {
        TreePath path = groupTree.getSelectionPath();
        if (path == null) {
            selectedNodeUserObject = null;
            peerModel.setGroup(null, null);
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = node.getUserObject();
        selectedNodeUserObject = uo;
        if (uo instanceof GroupNode groupNode) {
            peerModel.setGroup(groupNode.group(), fileDispositions.get(groupNode.group().groupId()));
            folderActionCombo.setEnabled(false);
            installFileDispositionEditor();
        } else if (uo instanceof FolderNode folderNode) {
            peerModel.setFolderCluster(folderNode.cluster(), folderDispositions.get(folderNode.cluster().clusterId()));
            folderActionCombo.setEnabled(true);
            installFolderDispositionEditor();
        } else {
            peerModel.setGroup(null, null);
            folderActionCombo.setEnabled(false);
        }
        updateDetailAndDiff();
        refreshStatus();
    }

    private void installFileDispositionEditor() {
        if (peerTable.getColumnModel().getColumnCount() == 0) {
            return;
        }
        TableColumn actionCol = peerTable.getColumnModel().getColumn(0);
        JComboBox<FileDisposition> combo = new JComboBox<>(FileDisposition.values());
        combo.setRenderer(new FileDispositionRenderer());
        actionCol.setCellEditor(new DefaultCellEditor(combo));
        actionCol.setCellRenderer(new FileDispositionTableRenderer());
    }

    private void installFolderDispositionEditor() {
        if (peerTable.getColumnModel().getColumnCount() == 0) {
            return;
        }
        TableColumn actionCol = peerTable.getColumnModel().getColumn(0);
        JComboBox<FolderDisposition> combo = new JComboBox<>(FolderDisposition.values());
        combo.setRenderer(new FolderDispositionRenderer());
        actionCol.setCellEditor(new DefaultCellEditor(combo));
        actionCol.setCellRenderer(new FolderDispositionTableRenderer());
    }

    private void applyFolderAction() {
        if (!(selectedNodeUserObject instanceof FolderNode folderNode)) {
            return;
        }
        FolderDisposition disposition = (FolderDisposition) folderActionCombo.getSelectedItem();
        if (disposition == null) {
            return;
        }
        int row = peerTable.getSelectedRow();
        Map<Path, FolderDisposition> map = folderDispositions.get(folderNode.cluster().clusterId());
        if (map == null) {
            return;
        }
        if (row >= 0 && row < folderNode.cluster().folderPaths().size()) {
            map.put(folderNode.cluster().folderPaths().get(row), disposition);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Select a folder row, then set an action. No folder is preferred automatically.",
                    "Folder action",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        peerModel.fireTableDataChanged();
        refreshStatus();
    }

    private void ignoreUnresolved() {
        for (Map<Path, FileDisposition> map : fileDispositions.values()) {
            for (Map.Entry<Path, FileDisposition> e : map.entrySet()) {
                if (e.getValue() == null) {
                    e.setValue(FileDisposition.IGNORE);
                }
            }
        }
        for (Map<Path, FolderDisposition> map : folderDispositions.values()) {
            for (Map.Entry<Path, FolderDisposition> e : map.entrySet()) {
                if (e.getValue() == null) {
                    e.setValue(FolderDisposition.REVIEW_INDIVIDUALLY);
                }
            }
        }
        peerModel.fireTableDataChanged();
        refreshStatus();
    }

    private void applyRulesAndRescan() {
        DuplicateCleanupPlan plan = buildPartialPlan();
        List<String> errors = DuplicateCleanupPlanValidator.validatePartial(analysis, plan);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Cannot apply yet:\n• " + String.join("\n• ", errors)
                            + "\n\nTip: fully resolve at least one folder cluster (e.g. keep one, exclude the copy),"
                            + " then Apply rules and rescan to reveal leftover individual files.",
                    "Apply rules and rescan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                summarizePlan(plan, true)
                        + "\n\nApply these rules now and re-scan for remaining duplicates?",
                "Apply rules and rescan",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        result.set(DuplicateReviewResult.applyAndRescan(plan));
        dispose();
    }

    private void confirmAndClose() {
        if (!allRequiredResolved()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Some items are unresolved. Ignore remaining and finish?",
                    "Unresolved duplicates",
                    JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            ignoreUnresolved();
        }

        DuplicateCleanupPlan plan = buildFullPlan();
        List<String> errors = DuplicateCleanupPlanValidator.validate(analysis, plan);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Plan is invalid:\n• " + String.join("\n• ", errors),
                    "Finish",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!showPlanSummary(plan)) {
            return;
        }
        result.set(DuplicateReviewResult.finished(plan));
        dispose();
    }

    private DuplicateCleanupPlan buildPartialPlan() {
        List<FileResolution> files = new ArrayList<>();
        List<FolderResolution> folders = new ArrayList<>();

        for (FolderDuplicateCluster cluster : analysis.folderClusters()) {
            Map<Path, FolderDisposition> map = folderDispositions.get(cluster.clusterId());
            if (map == null || map.containsValue(null)) {
                continue;
            }
            for (Path folder : cluster.folderPaths()) {
                folders.add(new FolderResolution(cluster.clusterId(), folder, map.get(folder)));
            }
        }

        Set<Path> suppressed = suppressedPrefixes(folders);
        for (DuplicateGroup group : analysis.groups()) {
            Map<Path, FileDisposition> map = fileDispositions.get(group.groupId());
            if (map == null) {
                continue;
            }
            // Skip groups entirely under suppressed folders — apply handles them via folder action
            if (!suppressed.isEmpty() && group.files().stream().allMatch(f -> underAny(f.path(), suppressed))) {
                continue;
            }
            if (map.containsValue(null)) {
                continue;
            }
            files.addAll(fileResolutionsForGroup(group, map));
        }
        return new DuplicateCleanupPlan(files, folders);
    }

    private DuplicateCleanupPlan buildFullPlan() {
        List<FolderResolution> folders = new ArrayList<>();
        for (FolderDuplicateCluster cluster : analysis.folderClusters()) {
            Map<Path, FolderDisposition> map = folderDispositions.get(cluster.clusterId());
            for (Path folder : cluster.folderPaths()) {
                FolderDisposition disposition = map.get(folder);
                if (disposition == null) {
                    disposition = FolderDisposition.REVIEW_INDIVIDUALLY;
                }
                folders.add(new FolderResolution(cluster.clusterId(), folder, disposition));
            }
        }
        Set<Path> suppressed = suppressedPrefixes(folders);

        List<FileResolution> files = new ArrayList<>();
        for (DuplicateGroup group : analysis.groups()) {
            Map<Path, FileDisposition> map = fileDispositions.get(group.groupId());
            Map<Path, FileDisposition> effective = new LinkedHashMap<>();
            for (DuplicateFile file : group.files()) {
                FileDisposition disposition = map.get(file.path());
                if (disposition == null && underAny(file.path(), suppressed)) {
                    disposition = FileDisposition.IGNORE;
                }
                if (disposition == null) {
                    disposition = FileDisposition.IGNORE;
                }
                effective.put(file.path(), disposition);
            }
            files.addAll(fileResolutionsForGroup(group, effective));
        }
        return new DuplicateCleanupPlan(files, folders);
    }

    private List<FileResolution> fileResolutionsForGroup(
            DuplicateGroup group, Map<Path, FileDisposition> map) {
        List<Long> songIds = group.files().stream()
                .map(DuplicateFile::currentSongId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<FileResolution> out = new ArrayList<>();
        for (DuplicateFile file : group.files()) {
            FileDisposition disposition = map.get(file.path());
            Long bind = null;
            if (disposition == FileDisposition.KEEP) {
                if (songIds.size() == 1) {
                    bind = songIds.get(0);
                } else if (songIds.size() > 1 && file.currentSongId() != null) {
                    bind = file.currentSongId();
                }
            }
            out.add(new FileResolution(group.groupId(), file.path(), disposition, bind));
        }
        return out;
    }

    private static Set<Path> suppressedPrefixes(List<FolderResolution> folders) {
        java.util.HashSet<Path> set = new java.util.HashSet<>();
        for (FolderResolution resolution : folders) {
            switch (resolution.disposition()) {
                case EXCLUDE_FROM_SCANS, REMOVE_FROM_LIBRARY, TRASH ->
                        set.add(resolution.folderPath().toAbsolutePath().normalize());
                default -> {
                }
            }
        }
        return set;
    }

    private static boolean underAny(Path path, Set<Path> folders) {
        Path p = path.toAbsolutePath().normalize();
        String pathStr = p.toString();
        for (Path folder : folders) {
            String folderStr = folder.toAbsolutePath().normalize().toString();
            if (pathStr.equals(folderStr)
                    || pathStr.startsWith(folderStr + "\\")
                    || pathStr.startsWith(folderStr + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean showPlanSummary(DuplicateCleanupPlan plan) {
        int choice = JOptionPane.showConfirmDialog(this, summarizePlan(plan, false) + "\n\nApply this plan?",
                "Confirm cleanup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    private String summarizePlan(DuplicateCleanupPlan plan, boolean partial) {
        int keep = 0;
        int separate = 0;
        int ignore = 0;
        int trash = 0;
        for (FileResolution r : plan.fileResolutions()) {
            switch (r.disposition()) {
                case KEEP -> keep++;
                case KEEP_SEPARATE -> separate++;
                case IGNORE -> ignore++;
                case TRASH -> trash++;
            }
        }
        int exclude = 0;
        int remove = 0;
        int folderTrash = 0;
        int keepFolders = 0;
        int reviewFolders = 0;
        for (FolderResolution r : plan.folderResolutions()) {
            switch (r.disposition()) {
                case EXCLUDE_FROM_SCANS -> exclude++;
                case REMOVE_FROM_LIBRARY -> remove++;
                case TRASH -> folderTrash++;
                case KEEP_AND_SCAN -> keepFolders++;
                case REVIEW_INDIVIDUALLY -> reviewFolders++;
            }
        }
        return String.format(Locale.ROOT,
                """
                        %s

                        File actions: keep=%d, keep separate=%d, ignore=%d, trash=%d
                        Folder actions: keep=%d, exclude=%d, remove=%d, trash=%d, review individually=%d
                        """,
                partial ? "Partial cleanup (then rescan)" : "Cleanup plan summary",
                keep, separate, ignore, trash, keepFolders, exclude, remove, folderTrash, reviewFolders);
    }

    private boolean allRequiredResolved() {
        for (DuplicateGroup group : analysis.groups()) {
            if (isFullyCoveredByFolderCluster(group)) {
                // Covered groups may remain unresolved until folders are handled / ignored on finish
                continue;
            }
            Map<Path, FileDisposition> map = fileDispositions.get(group.groupId());
            if (map != null && map.containsValue(null)) {
                return false;
            }
        }
        for (Map<Path, FolderDisposition> map : folderDispositions.values()) {
            if (map.containsValue(null)) {
                return false;
            }
        }
        return true;
    }

    private void refreshStatus() {
        int unresolvedFolders = 0;
        for (Map<Path, FolderDisposition> map : folderDispositions.values()) {
            for (FolderDisposition d : map.values()) {
                if (d == null) {
                    unresolvedFolders++;
                }
            }
        }
        int unresolvedIndividual = 0;
        int coveredUnresolved = 0;
        for (DuplicateGroup group : analysis.groups()) {
            Map<Path, FileDisposition> map = fileDispositions.get(group.groupId());
            if (map == null) {
                continue;
            }
            int nulls = 0;
            for (FileDisposition d : map.values()) {
                if (d == null) {
                    nulls++;
                }
            }
            if (isFullyCoveredByFolderCluster(group)) {
                coveredUnresolved += nulls;
            } else {
                unresolvedIndividual += nulls;
            }
        }
        if (unresolvedFolders == 0 && unresolvedIndividual == 0) {
            statusLabel.setText(coveredUnresolved == 0
                    ? "All items resolved"
                    : "Ready — " + coveredUnresolved
                            + " file choice(s) still under folder clusters (Apply rules and rescan, or Finish)");
        } else {
            statusLabel.setText(String.format(Locale.ROOT,
                    "%d folder action(s) and %d individual file action(s) unresolved"
                            + " — use Apply rules and rescan after folder decisions",
                    unresolvedFolders, unresolvedIndividual));
        }
    }

    private void updateDetailAndDiff() {
        detailArea.setText("");
        diffPane.setText("");
        if (!(selectedNodeUserObject instanceof GroupNode groupNode)) {
            if (selectedNodeUserObject instanceof FolderNode folderNode) {
                FolderDuplicateCluster c = folderNode.cluster();
                detailArea.setText(String.format(Locale.ROOT,
                        """
                                Possible duplicated folder tree

                                %s

                                Identical files: %d
                                Differing files: %d
                                Unique to one folder: %d

                                Suggested workflow:
                                1. Keep one folder (Keep and scan)
                                2. Exclude / remove / trash the other(s)
                                3. Click Apply rules and rescan
                                4. Review any leftover individual file groups

                                Exclude from future scans leaves files on disk, removes them from the
                                managed library, and persists a folder exclusion rule.
                                """,
                        c.folderPaths().stream().map(Path::toString).reduce((a, b) -> a + "\n" + b).orElse(""),
                        c.identicalFileCount(),
                        c.differingFileCount(),
                        c.uniqueFileCount()));
            }
            return;
        }
        DuplicateGroup group = groupNode.group();
        int row = peerTable.getSelectedRow();
        DuplicateFile selected = row >= 0 && row < group.files().size() ? group.files().get(row) : null;
        if (selected == null && !group.files().isEmpty()) {
            selected = group.files().get(0);
        }
        if (selected == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(group.displayTitle()).append('\n');
        sb.append(group.matchType()).append(" · ").append(group.files().size()).append(" files");
        if (isFullyCoveredByFolderCluster(group)) {
            sb.append(" · covered by a folder cluster");
        }
        sb.append("\n\n");
        sb.append("File hash: ").append(nullToDash(selected.fileHash())).append('\n');
        sb.append("Title: ").append(selected.metadata().title()).append('\n');
        sb.append("Composer: ").append(selected.metadata().composers()).append('\n');
        sb.append("Part count: ").append(selected.metadata().parts().size()).append('\n');
        sb.append("Duration: ").append(selected.metadata().durationSeconds() == null
                ? "—" : selected.metadata().durationSeconds() + "s").append('\n');
        sb.append("DB song id: ").append(selected.currentSongId() == null ? "—" : selected.currentSongId()).append('\n');
        sb.append("Indexed: ").append(selected.currentlyIndexed()).append('\n');
        sb.append("Full path: ").append(selected.path()).append('\n');
        detailArea.setText(sb.toString());

        if (group.matchType() != DuplicateMatchType.EXACT_FILE && group.files().size() >= 2) {
            DuplicateFile left = group.files().get(0);
            DuplicateFile right = selected.path().equals(left.path()) && group.files().size() > 1
                    ? group.files().get(1)
                    : selected;
            if (!left.path().equals(right.path())) {
                try {
                    String leftText = Files.readString(left.path());
                    String rightText = Files.readString(right.path());
                    diffPane.setText(AbcDiff.toHtml(
                            left.path().getFileName().toString(),
                            right.path().getFileName().toString(),
                            leftText,
                            rightText));
                    diffPane.setCaretPosition(0);
                } catch (Exception ex) {
                    diffPane.setText("<html>Unable to load ABC for diff: " + ex.getMessage() + "</html>");
                }
            }
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String labelFileDisposition(FileDisposition disposition) {
        if (disposition == null) {
            return "(choose)";
        }
        return switch (disposition) {
            case KEEP -> "Keep";
            case KEEP_SEPARATE -> "Keep separate";
            case IGNORE -> "Ignore";
            case TRASH -> "Trash";
        };
    }

    private static String labelFolderDisposition(FolderDisposition disposition) {
        if (disposition == null) {
            return "(choose)";
        }
        return switch (disposition) {
            case KEEP_AND_SCAN -> "Keep and scan";
            case REMOVE_FROM_LIBRARY -> "Remove from library";
            case EXCLUDE_FROM_SCANS -> "Exclude from future scans";
            case TRASH -> "Move to Recycle Bin";
            case REVIEW_INDIVIDUALLY -> "Review files individually";
        };
    }

    private record GroupNode(DuplicateGroup group, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record FolderNode(FolderDuplicateCluster cluster, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class FileDispositionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(labelFileDisposition((FileDisposition) value));
            return this;
        }
    }

    private static final class FolderDispositionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(labelFolderDisposition((FolderDisposition) value));
            return this;
        }
    }

    private static final class FileDispositionTableRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            setText(labelFileDisposition((FileDisposition) value));
        }
    }

    private static final class FolderDispositionTableRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            setText(labelFolderDisposition((FolderDisposition) value));
        }
    }

    private final class PeerTableModel extends AbstractTableModel {
        private DuplicateGroup group;
        private FolderDuplicateCluster cluster;
        private Map<Path, FileDisposition> fileMap = Map.of();
        private Map<Path, FolderDisposition> folderMap = Map.of();
        private boolean folderMode;

        void setGroup(DuplicateGroup group, Map<Path, FileDisposition> map) {
            this.group = group;
            this.cluster = null;
            this.fileMap = map == null ? Map.of() : map;
            this.folderMode = false;
            fireTableStructureChanged();
            installFileDispositionEditor();
        }

        void setFolderCluster(FolderDuplicateCluster cluster, Map<Path, FolderDisposition> map) {
            this.cluster = cluster;
            this.group = null;
            this.folderMap = map == null ? Map.of() : map;
            this.folderMode = true;
            fireTableStructureChanged();
            installFolderDispositionEditor();
        }

        @Override
        public int getRowCount() {
            if (folderMode) {
                return cluster == null ? 0 : cluster.folderPaths().size();
            }
            return group == null ? 0 : group.files().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Action";
                case 1 -> folderMode ? "Folder" : "File";
                default -> "Location";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? (folderMode ? FolderDisposition.class : FileDisposition.class) : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (folderMode) {
                Path folder = cluster.folderPaths().get(rowIndex);
                return switch (columnIndex) {
                    case 0 -> folderMap.get(folder);
                    case 1 -> folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
                    default -> folder.toString();
                };
            }
            DuplicateFile file = group.files().get(rowIndex);
            return switch (columnIndex) {
                case 0 -> fileMap.get(file.path());
                case 1 -> file.path().getFileName() == null
                        ? file.path().toString()
                        : file.path().getFileName().toString();
                default -> {
                    Path parent = file.path().getParent();
                    yield parent == null ? "" : parent.toString();
                }
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            if (folderMode) {
                Path folder = cluster.folderPaths().get(rowIndex);
                folderMap.put(folder, (FolderDisposition) aValue);
            } else {
                DuplicateFile file = group.files().get(rowIndex);
                fileMap.put(file.path(), (FileDisposition) aValue);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
            refreshStatus();
        }
    }
}
