package com.aevoreth.abcmm.ui;

import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Instrument-first editor; all placement choices are soft preferences. */
final class InstrumentPlacementForm extends JPanel {
    private static final String NORMAL = "No preference", PREFER = "Prefer", AVOID = "Avoid";
    private final JComboBox<Instrument> instruments = new JComboBox<>();
    private final JSpinner penalty = new JSpinner(BandOptimizerForm.weightModel(10));
    private final DefaultTableModel model =
            new DefaultTableModel(new String[] {"Character", "Placement"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 1;
                }
            };
    private final JTable table = new JTable(model);
    private final List<Long> playerIds;
    private final Map<Long, String> names;
    private final Map<Long, InstrumentPlacementRule> rules = new LinkedHashMap<>();
    private Long selected;

    InstrumentPlacementForm(
            Map<Long, String> instrumentNames,
            Map<Long, String> playerNames,
            List<InstrumentPlacementRule> existing) {
        super(new BorderLayout(8, 8));
        playerIds = List.copyOf(playerNames.keySet());
        names = playerNames;
        existing.forEach(rule -> rules.put(rule.instrumentId(), rule));
        instrumentNames.forEach((id, name) -> instruments.addItem(new Instrument(id, name)));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Instrument"));
        top.add(instruments);
        JLabel penaltyLabel = new JLabel("Placement penalty");
        penaltyLabel.setLabelFor(penalty);
        top.add(penaltyLabel);
        top.add(penalty);
        penalty.setToolTipText(
                "Multiplied by Role preservation. Shared by this instrument's Prefer and Avoid"
                    + " choices.");
        JPanel header = new JPanel(new BorderLayout());
        header.add(top, BorderLayout.NORTH);
        header.add(
                new JLabel(
                        "<html>Choose back-row characters by name. Prefer and Avoid are"
                            + " soft;<br>ownership and positions stay unchanged.</html>"),
                BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        table.getColumnModel()
                .getColumn(1)
                .setCellEditor(
                        new DefaultCellEditor(
                                new JComboBox<>(new String[] {NORMAL, PREFER, AVOID})));
        table.setRowHeight(26);
        table.putClientProperty("terminateEditOnFocusLost", true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(700, 330));
        add(scroll, BorderLayout.CENTER);
        JButton help = new JButton("Optimizer help…");
        help.addActionListener(event -> OptimizerHelpDialog.show(this));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.add(help);
        add(tools, BorderLayout.SOUTH);
        instruments.addActionListener(
                event -> {
                    remember();
                    load();
                });
        load();
    }

    private void load() {
        Instrument instrument = (Instrument) instruments.getSelectedItem();
        selected = instrument == null ? null : instrument.id();
        model.setRowCount(0);
        if (selected == null) return;
        InstrumentPlacementRule rule = rules.get(selected);
        penalty.setModel(BandOptimizerForm.weightModel(rule == null ? 10 : rule.missPenalty()));
        for (Long player : playerIds) {
            String choice =
                    rule != null && rule.preferredPlayerIds().contains(player)
                            ? PREFER
                            : rule != null && rule.avoidedPlayerIds().contains(player)
                                    ? AVOID
                                    : NORMAL;
            model.addRow(new Object[] {names.get(player), choice});
        }
    }

    private void remember() {
        if (selected == null) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        List<Long> preferred = new ArrayList<>(), avoided = new ArrayList<>();
        for (int row = 0; row < playerIds.size(); row++) {
            if (PREFER.equals(model.getValueAt(row, 1))) preferred.add(playerIds.get(row));
            if (AVOID.equals(model.getValueAt(row, 1))) avoided.add(playerIds.get(row));
        }
        if (preferred.isEmpty() && avoided.isEmpty()) rules.remove(selected);
        else
            rules.put(
                    selected,
                    new InstrumentPlacementRule(
                            selected,
                            preferred,
                            avoided,
                            ((Number) penalty.getValue()).doubleValue()));
    }

    List<InstrumentPlacementRule> values() {
        remember();
        return List.copyOf(rules.values());
    }

    private record Instrument(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
