package com.aevoreth.abcmm.ui;

import java.awt.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.aevoreth.abcmm.domain.optimizer.*;

/** One instrument-first editor for character roles and shared placement rules. */
final class InstrumentRulesForm extends JPanel {
    private record Instrument(long id, String name) { public String toString() { return name; } }
    private final JComboBox<Instrument> instruments = new JComboBox<>();
    private final JSpinner strength = new JSpinner(BandOptimizerForm.weightModel(10));
    private final List<Long> playerIds;
    private final Map<Long, String> names;
    private final Map<Long, InstrumentPlacementRule> placements = new LinkedHashMap<>();
    private final List<InstrumentAffinityWeight> affinities = new ArrayList<>();
    private Long selected;
    private boolean switching;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Character", "Character's instrument role", "Strength", "Priority", "Instrument's placement"}, 0) {
        public Class<?> getColumnClass(int col) { return col == 2 || col == 3 ? Double.class : String.class; }
        public boolean isCellEditable(int row, int col) {
            return col == 1 || col == 4 || col == 2 && "Prefer".equals(getValueAt(row, 1))
                    || col == 3 && "Always, when present".equals(getValueAt(row, 1));
        }
        public void setValueAt(Object value, int row, int col) {
            super.setValueAt(value, row, col);
            if (col == 1) fireTableRowsUpdated(row, row);
        }
    };
    private final JTable table = OptimizerTooltips.table(model,
            "The character affected by this instrument rule.",
            "Prefer discourages playing another instrument. Always requires this instrument when present, subject to priority and pins.",
            "Soft cost when this character plays another instrument. Multiplied by the Instrument preferences goal weight.",
            "For Always rules only: higher priority gets scarce instrument parts first. Equal priorities let search choose.",
            "Prefer keeps this instrument within the preferred character group. Avoid discourages giving it to this character. Uses the shared placement strength.");

    InstrumentRulesForm(Map<Long, String> instrumentNames, Map<Long, String> playerNames,
            List<InstrumentAffinityWeight> existingAffinities, List<InstrumentPlacementRule> existingPlacements) {
        super(new BorderLayout(8, 8));
        names = playerNames; playerIds = List.copyOf(names.keySet());
        affinities.addAll(existingAffinities);
        existingPlacements.forEach(p -> placements.put(p.instrumentId(), p));
        Map<Long, String> allInstruments = new LinkedHashMap<>(instrumentNames);
        existingAffinities.forEach(a -> allInstruments.putIfAbsent(a.instrumentId(), "Instrument " + a.instrumentId()));
        existingPlacements.forEach(p -> allInstruments.putIfAbsent(p.instrumentId(), "Instrument " + p.instrumentId()));
        allInstruments.forEach((id, name) -> instruments.addItem(new Instrument(id, name)));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        OptimizerTooltips.describe(instruments, "Choose the instrument whose character roles and placement rules you want to edit.");
        OptimizerTooltips.describe(strength, "Shared cost for assigning this instrument outside its preferred group or to an Avoid character. Multiplied by the Instrument preferences goal weight.");
        top.add(new JLabel("Instrument")); top.add(instruments);
        JLabel label = new JLabel("Placement strength (shared)"); label.setLabelFor(strength); label.setToolTipText(strength.getToolTipText());
        top.add(label); top.add(strength);
        JTextArea explanation = new JTextArea("Character role: Prefer discourages playing another instrument; Always requires this instrument when present. "
                + "Priority decides who takes scarce parts. Placement: Prefer keeps this instrument within the preferred group; Avoid discourages assigning it to that character. "
                + "Placement strength applies to every placement choice for this instrument. Soft strengths use the Instrument preferences goal.");
        explanation.setEditable(false); explanation.setLineWrap(true); explanation.setWrapStyleWord(true); explanation.setOpaque(false); explanation.setRows(4);
        JPanel header = new JPanel(new BorderLayout()); header.add(top, BorderLayout.NORTH); header.add(explanation, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"No preference", "Prefer", "Always, when present"})));
        table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"No preference", "Prefer", "Avoid"})));
        table.setDefaultRenderer(Double.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                if (!model.isCellEditable(table.convertRowIndexToModel(row), table.convertColumnIndexToModel(column))) setText("");
                return this;
            }
        });
        table.setRowHeight(26); table.putClientProperty("terminateEditOnFocusLost", true);
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader());
        add(scroll, BorderLayout.CENTER);
        instruments.addActionListener(e -> {
            if (switching) return;
            try { remember(); load(); }
            catch (IllegalArgumentException ex) {
                Long previous = selected;
                switching = true;
                // Restore the selector without discarding the invalid edit.
                for (int i = 0; i < instruments.getItemCount(); i++) if (instruments.getItemAt(i).id() == previous) {
                    instruments.setSelectedIndex(i); break;
                }
                switching = false;
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Instrument rules", JOptionPane.WARNING_MESSAGE);
            }
        });
        load();
    }
    private void load() {
        Instrument instrument = (Instrument) instruments.getSelectedItem();
        selected = instrument == null ? null : instrument.id(); model.setRowCount(0);
        if (selected == null) return;
        InstrumentPlacementRule placement = placements.get(selected);
        strength.setModel(BandOptimizerForm.weightModel(placement == null ? 10 : placement.missPenalty()));
        OptimizerTooltips.describe(strength, strength.getToolTipText());
        for (Long player : playerIds) {
            InstrumentAffinityWeight affinity = affinities.stream().filter(a -> a.playerId() == player && a.instrumentId() == selected).findFirst().orElse(null);
            model.addRow(new Object[]{names.get(player), affinity == null ? "No preference" : affinity.required() ? "Always, when present" : "Prefer",
                    affinity == null || affinity.required() ? 1.0 : affinity.missPenalty(),
                    affinity != null && affinity.required() ? affinity.missPenalty() : 0.0,
                    placement == null ? "No preference" : placement.preferredPlayerIds().contains(player) ? "Prefer" : placement.avoidedPlayerIds().contains(player) ? "Avoid" : "No preference"});
        }
    }
    private static double number(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0)
            throw new IllegalArgumentException("Strength and priority must be finite, nonnegative numbers.");
        return n.doubleValue();
    }
    private void remember() {
        if (selected == null) return;
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) throw new IllegalArgumentException("Enter a valid strength or priority.");
        try { strength.commitEdit(); } catch (java.text.ParseException ex) { throw new IllegalArgumentException("Enter a valid placement strength."); }
        double shared = number(strength.getValue());
        List<InstrumentAffinityWeight> next = new ArrayList<>();
        List<Long> preferred = new ArrayList<>(), avoided = new ArrayList<>();
        for (int row = 0; row < playerIds.size(); row++) {
            long player = playerIds.get(row); String role = (String) model.getValueAt(row, 1);
            if (!"No preference".equals(role)) {
                boolean required = "Always, when present".equals(role);
                next.add(new InstrumentAffinityWeight(player, selected, number(model.getValueAt(row, required ? 3 : 2)), required));
            }
            if ("Prefer".equals(model.getValueAt(row, 4))) preferred.add(player);
            if ("Avoid".equals(model.getValueAt(row, 4))) avoided.add(player);
        }
        affinities.removeIf(a -> a.instrumentId() == selected); affinities.addAll(next);
        placements.remove(selected);
        if (!preferred.isEmpty() || !avoided.isEmpty()) placements.put(selected, new InstrumentPlacementRule(selected, preferred, avoided, shared));
    }
    List<InstrumentAffinityWeight> affinities() { remember(); return List.copyOf(affinities); }
    List<InstrumentPlacementRule> placements() { remember(); return List.copyOf(placements.values()); }
}
