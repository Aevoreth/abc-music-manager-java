package com.aevoreth.abcmm.ui;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;

/**
 * Compact combo-like control whose popup shows setlists grouped under collapsible folders
 * (Python {@code SetlistPickerCombo} parity).
 */
final class SetlistPickerCombo extends JPanel {

    private final JTextField display = new JTextField("(select)");
    private final JButton dropBtn = new JButton("\u25BC");
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final JScrollPane treeScroll = new JScrollPane(tree);
    private JWindow popup;
    private AWTEventListener outsideClickListener;

    private Long selectedId;
    private String selectedText = "(select)";
    private Consumer<Long> selectionListener;

    SetlistPickerCombo() {
        super(new BorderLayout(0, 0));
        display.setEditable(false);
        display.setFocusable(false);
        display.setBorder(UIManager.getBorder("TextField.border"));

        dropBtn.setMargin(new java.awt.Insets(1, 6, 1, 6));
        dropBtn.setFocusable(false);

        int rowH = Math.max(22, display.getPreferredSize().height);
        Dimension fieldPref = new Dimension(160, rowH);
        display.setPreferredSize(fieldPref);
        display.setMinimumSize(new Dimension(80, rowH));
        display.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH));

        Dimension btnPref = new Dimension(Math.max(28, dropBtn.getPreferredSize().width), rowH);
        dropBtn.setPreferredSize(btnPref);
        dropBtn.setMinimumSize(btnPref);
        dropBtn.setMaximumSize(btnPref);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH));
        setPreferredSize(new Dimension(200, rowH));

        add(display, BorderLayout.CENTER);
        add(dropBtn, BorderLayout.EAST);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new PickerTreeRenderer());
        tree.setVisibleRowCount(12);
        treeScroll.setPreferredSize(new Dimension(280, 240));

        ActionListener toggle = e -> togglePopup();
        dropBtn.addActionListener(toggle);
        display.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    togglePopup();
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (last instanceof FolderNode) {
                    if (tree.isExpanded(path)) {
                        tree.collapsePath(path);
                    } else {
                        tree.expandPath(path);
                    }
                    return;
                }
                if (last instanceof SetlistNode setlistNode) {
                    applySelection(setlistNode.id(), setlistNode.displayName());
                    hidePopup();
                }
            }
        });
    }

    void setSelectionListener(Consumer<Long> listener) {
        this.selectionListener = listener;
    }

    Long getSelectedSetlistId() {
        return selectedId;
    }

    void populate(List<SetlistFolderInfo> folders, List<SetlistInfo> setlists, Long preserveId) {
        root.removeAllChildren();

        Map<Long, DefaultMutableTreeNode> folderNodes = new LinkedHashMap<>();
        List<SetlistFolderInfo> sortedFolders = new ArrayList<>(folders);
        sortedFolders.sort(Comparator
                .comparingInt(SetlistFolderInfo::sortOrder)
                .thenComparing(SetlistFolderInfo::name, String.CASE_INSENSITIVE_ORDER));
        for (SetlistFolderInfo folder : sortedFolders) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FolderNode(folder));
            folderNodes.put(folder.id(), node);
            root.add(node);
        }
        DefaultMutableTreeNode unfiled = new DefaultMutableTreeNode(new FolderNode(null));
        root.add(unfiled);

        Map<Long, List<SetlistInfo>> byFolder = new HashMap<>();
        for (SetlistInfo setlist : setlists) {
            byFolder.computeIfAbsent(setlist.folderId(), k -> new ArrayList<>()).add(setlist);
        }
        for (List<SetlistInfo> group : byFolder.values()) {
            group.sort(Comparator
                    .comparingInt(SetlistInfo::sortOrder)
                    .thenComparing(SetlistInfo::name, String.CASE_INSENSITIVE_ORDER));
        }

        for (Map.Entry<Long, DefaultMutableTreeNode> entry : folderNodes.entrySet()) {
            for (SetlistInfo setlist : byFolder.getOrDefault(entry.getKey(), List.of())) {
                entry.getValue().add(new DefaultMutableTreeNode(new SetlistNode(setlist)));
            }
        }
        for (SetlistInfo setlist : byFolder.getOrDefault(null, List.of())) {
            unfiled.add(new DefaultMutableTreeNode(new SetlistNode(setlist)));
        }

        List<DefaultMutableTreeNode> toRemove = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object uo = child.getUserObject();
            if (uo instanceof FolderNode folderNode
                    && folderNode.folder() != null
                    && child.getChildCount() == 0) {
                toRemove.add(child);
            }
        }
        for (DefaultMutableTreeNode node : toRemove) {
            root.remove(node);
        }
        if (unfiled.getChildCount() == 0) {
            root.remove(unfiled);
        }

        treeModel.reload();
        for (int i = 0; i < root.getChildCount(); i++) {
            tree.collapsePath(new TreePath(((DefaultMutableTreeNode) root.getChildAt(i)).getPath()));
        }

        if (preserveId != null && selectSetlistId(preserveId, true)) {
            return;
        }
        selectedId = null;
        selectedText = "(select)";
        display.setText(selectedText);
    }

    private boolean selectSetlistId(long setlistId, boolean expandParent) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode folder = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < folder.getChildCount(); j++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) folder.getChildAt(j);
                if (child.getUserObject() instanceof SetlistNode sn && sn.id() == setlistId) {
                    if (expandParent) {
                        tree.expandPath(new TreePath(folder.getPath()));
                    }
                    applySelection(sn.id(), sn.displayName());
                    return true;
                }
            }
        }
        return false;
    }

    private void applySelection(long id, String text) {
        Long previous = selectedId;
        selectedId = id;
        selectedText = text;
        display.setText(text);
        if (!Objects.equals(previous, selectedId) && selectionListener != null) {
            selectionListener.accept(selectedId);
        }
    }

    private void togglePopup() {
        if (popup != null && popup.isVisible()) {
            hidePopup();
        } else {
            showPopup();
        }
    }

    private void showPopup() {
        hidePopup();
        Window owner = SwingUtilities.getWindowAncestor(this);
        popup = new JWindow(owner);
        popup.setFocusableWindowState(false);
        popup.getContentPane().add(treeScroll);
        java.awt.Color border = UIManager.getColor("Component.borderColor");
        treeScroll.setBorder(BorderFactory.createLineBorder(
                border != null ? border : java.awt.Color.GRAY));

        Point loc = getLocationOnScreen();
        int width = Math.max(getWidth(), 280);
        treeScroll.setPreferredSize(new Dimension(width, preferredPopupHeight()));
        popup.pack();
        popup.setLocation(loc.x, loc.y + getHeight());
        popup.setVisible(true);

        outsideClickListener = this::handleOutsideClick;
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private int preferredPopupHeight() {
        int rows = Math.min(15, Math.max(8, tree.getRowCount()));
        int rowH = tree.getRowHeight() > 0
                ? tree.getRowHeight()
                : tree.getFontMetrics(tree.getFont()).getHeight() + 6;
        return Math.min(320, rows * rowH + 8);
    }

    private void hidePopup() {
        if (outsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
        }
        if (popup != null) {
            popup.setVisible(false);
            popup.dispose();
            popup = null;
        }
    }

    private void handleOutsideClick(AWTEvent event) {
        if (!(event instanceof MouseEvent me) || me.getID() != MouseEvent.MOUSE_PRESSED) {
            return;
        }
        Component src = me.getComponent();
        if (src == null) {
            return;
        }
        if (SwingUtilities.isDescendingFrom(src, this)) {
            return;
        }
        if (popup != null && SwingUtilities.isDescendingFrom(src, popup)) {
            return;
        }
        hidePopup();
    }

    private record FolderNode(SetlistFolderInfo folder) {
        @Override
        public String toString() {
            return folder == null ? "Unfiled" : folder.name();
        }
    }

    private record SetlistNode(SetlistInfo setlist) {
        long id() {
            return setlist.id();
        }

        String displayName() {
            return setlist.locked() ? setlist.name() + " [locked]" : setlist.name();
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    private static final class PickerTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object uo = node.getUserObject();
            if (uo instanceof FolderNode) {
                setIcon(expanded ? getOpenIcon() : getClosedIcon());
            } else if (uo instanceof SetlistNode) {
                setIcon(getLeafIcon());
            }
            return this;
        }
    }
}
