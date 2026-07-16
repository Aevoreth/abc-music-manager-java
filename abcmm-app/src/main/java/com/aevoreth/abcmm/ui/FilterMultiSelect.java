package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Combo-styled multi-select: single-click checkbox toggles, an All option, and
 * compact closed-state labels ({@code All (n)} / {@code Multiple (n)} / single).
 */
final class FilterMultiSelect<T> extends JPanel {

    private static final int POPUP_MAX_HEIGHT = 260;
    private static final int TRIGGER_PREFERRED_WIDTH = 148;

    private final Function<T, String> itemLabel;
    private final JComboBox<String> trigger = new JComboBox<>();
    private final DefaultComboBoxModel<String> triggerModel = new DefaultComboBoxModel<>();
    private final JPopupMenu popup = new JPopupMenu();
    private final JPanel optionsPanel = new JPanel();
    private final JCheckBox allCheck = new JCheckBox("All");
    private final Map<T, JCheckBox> itemChecks = new LinkedHashMap<>();

    private List<T> items = List.of();
    private Consumer<List<T>> changeListener = selected -> {
    };
    private boolean suppressEvents;

    FilterMultiSelect(Function<T, String> itemLabel) {
        super(new BorderLayout());
        this.itemLabel = Objects.requireNonNull(itemLabel, "itemLabel");
        setOpaque(false);

        triggerModel.addElement("All");
        trigger.setModel(triggerModel);
        trigger.setPrototypeDisplayValue("Multiple (99)");
        trigger.setMaximumRowCount(1);
        Dimension preferred = trigger.getPreferredSize();
        trigger.setPreferredSize(new Dimension(TRIGGER_PREFERRED_WIDTH, preferred.height));
        trigger.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText(value == null ? "" : String.valueOf(value));
                return label;
            }
        });
        trigger.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    trigger.hidePopup();
                    if (popup.isVisible()) {
                        popup.setVisible(false);
                    } else {
                        showCustomPopup();
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        add(trigger, BorderLayout.CENTER);

        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        allCheck.setAlignmentX(LEFT_ALIGNMENT);
        allCheck.addActionListener(e -> {
            if (suppressEvents) {
                return;
            }
            boolean selectAll = allCheck.isSelected();
            suppressEvents = true;
            try {
                for (JCheckBox check : itemChecks.values()) {
                    check.setSelected(selectAll);
                }
            } finally {
                suppressEvents = false;
            }
            updateTriggerLabel();
            fireChange();
        });

        JScrollPane scroll = new JScrollPane(optionsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.CENTER);
        popup.setLayout(new BorderLayout());
        popup.add(content, BorderLayout.CENTER);

        rebuildOptions();
        updateTriggerLabel();
    }

    void setChangeListener(Consumer<List<T>> changeListener) {
        this.changeListener = Objects.requireNonNullElse(changeListener, selected -> {
        });
    }

    void setItems(List<T> items) {
        List<T> previouslySelected = selectedItems();
        this.items = items == null ? List.of() : List.copyOf(items);
        rebuildOptions();
        setSelectedItems(previouslySelected);
    }

    List<T> selectedItems() {
        List<T> selected = new ArrayList<>();
        for (Map.Entry<T, JCheckBox> entry : itemChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    void setSelectedItems(Collection<T> selectedItems) {
        suppressEvents = true;
        try {
            Collection<T> selected = selectedItems == null ? List.of() : selectedItems;
            for (Map.Entry<T, JCheckBox> entry : itemChecks.entrySet()) {
                entry.getValue().setSelected(containsItem(selected, entry.getKey()));
            }
            syncAllCheck();
            updateTriggerLabel();
        } finally {
            suppressEvents = false;
        }
    }

    void clearSelection() {
        setSelectedItems(List.of());
    }

    private static <T> boolean containsItem(Collection<T> selected, T item) {
        for (T candidate : selected) {
            if (Objects.equals(candidate, item)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildOptions() {
        optionsPanel.removeAll();
        itemChecks.clear();

        optionsPanel.add(allCheck);
        if (!items.isEmpty()) {
            optionsPanel.add(Box.createVerticalStrut(2));
            JPanel divider = new JPanel();
            divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            divider.setPreferredSize(new Dimension(10, 1));
            Color separator = UIManager.getColor("Separator.foreground");
            if (separator == null) {
                separator = UIManager.getColor("controlShadow");
            }
            if (separator != null) {
                divider.setBackground(separator);
            }
            divider.setOpaque(true);
            divider.setAlignmentX(LEFT_ALIGNMENT);
            optionsPanel.add(divider);
            optionsPanel.add(Box.createVerticalStrut(2));
        }

        ActionListener itemListener = e -> {
            if (suppressEvents) {
                return;
            }
            syncAllCheck();
            updateTriggerLabel();
            fireChange();
        };

        for (T item : items) {
            JCheckBox check = new JCheckBox(itemLabel.apply(item));
            check.setAlignmentX(LEFT_ALIGNMENT);
            check.addActionListener(itemListener);
            itemChecks.put(item, check);
            optionsPanel.add(check);
        }

        syncAllCheck();
        optionsPanel.revalidate();
        optionsPanel.repaint();
        sizePopup();
    }

    private void showCustomPopup() {
        sizePopup();
        popup.show(trigger, 0, trigger.getHeight());
    }

    private void sizePopup() {
        optionsPanel.doLayout();
        Dimension preferred = optionsPanel.getPreferredSize();
        int width = Math.max(Math.max(trigger.getWidth(), getWidth()), preferred.width + 24);
        int height = Math.min(preferred.height + 12, POPUP_MAX_HEIGHT);
        popup.setPopupSize(new Dimension(width, height));
    }

    private void syncAllCheck() {
        boolean allSelected = !itemChecks.isEmpty()
                && itemChecks.values().stream().allMatch(JCheckBox::isSelected);
        allCheck.setSelected(allSelected);
        allCheck.setEnabled(!itemChecks.isEmpty());
    }

    private void updateTriggerLabel() {
        List<T> selected = selectedItems();
        int total = items.size();
        String label;
        if (selected.isEmpty() || selected.size() == total) {
            label = total > 0 ? "All (" + total + ")" : "All";
        } else if (selected.size() == 1) {
            label = itemLabel.apply(selected.get(0));
        } else {
            label = "Multiple (" + selected.size() + ")";
        }

        suppressEvents = true;
        try {
            triggerModel.removeAllElements();
            triggerModel.addElement(label);
            trigger.setSelectedIndex(0);
        } finally {
            suppressEvents = false;
        }
    }

    private void fireChange() {
        changeListener.accept(selectedItems());
    }
}
