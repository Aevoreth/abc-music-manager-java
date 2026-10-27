package com.aevoreth.abcmm.ui;

import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** Mutually exclusive character preferences; table editing also works from the keyboard. */
final class CharacterInstrumentForm extends JPanel {
    static final String NONE = "No preference",
            PREFER = "Prefer",
            ALWAYS = "Always, when present",
            AVOID = "Avoid";
    private final List<Long> instrumentIds;
    private final DefaultTableModel model =
            new DefaultTableModel(
                    new String[] {"Instrument", "Preference", "Penalty / priority"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column > 0;
                }

                @Override
                public Class<?> getColumnClass(int column) {
                    return column == 2 ? Double.class : String.class;
                }

                @Override
                public void setValueAt(Object value, int row, int column) {
                    if (column == 1 && !value.equals(getValueAt(row, 1))) {
                        double previous = ((Number) getValueAt(row, 2)).doubleValue();
                        if (AVOID.equals(value) && previous == 1) super.setValueAt(10.0, row, 2);
                        if (ALWAYS.equals(value) && previous == 1) super.setValueAt(0.0, row, 2);
                    }
                    super.setValueAt(value, row, column);
                }
            };
    private final JTable table = new JTable(model);

    CharacterInstrumentForm(
            Map<Long, String> instruments,
            Map<Long, InstrumentAffinityWeight> affinities,
            Map<Long, Double> avoided) {
        super(new BorderLayout(8, 8));
        instrumentIds = List.copyOf(instruments.keySet());
        JTextArea explanation =
                new JTextArea(
                        "Prefer discourages playing something else; Avoid discourages playing this"
                            + " instrument. Both use Role preservation. Always is a hard"
                            + " requirement when the song includes it; its number is a priority,"
                            + " not a penalty. Higher priorities take scarce parts first. An Avoid"
                            + " penalty is shared by this instrument. Ownership never changes.");
        explanation.setEditable(false);
        explanation.setOpaque(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setRows(4);
        add(explanation, BorderLayout.NORTH);
        for (Long id : instrumentIds) {
            InstrumentAffinityWeight affinity = affinities.get(id);
            String mode =
                    avoided.containsKey(id)
                            ? AVOID
                            : affinity == null ? NONE : affinity.required() ? ALWAYS : PREFER;
            double penalty =
                    avoided.containsKey(id)
                            ? avoided.get(id)
                            : affinity == null ? 1 : affinity.missPenalty();
            model.addRow(new Object[] {instruments.get(id), mode, penalty});
        }
        table.getColumnModel()
                .getColumn(1)
                .setCellEditor(
                        new DefaultCellEditor(
                                new JComboBox<>(new String[] {NONE, PREFER, ALWAYS, AVOID})));
        table.setRowHeight(26);
        table.putClientProperty("terminateEditOnFocusLost", true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(700, 330));
        add(scroll, BorderLayout.CENTER);
    }

    private void commit() {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            throw new IllegalArgumentException("Enter a valid numeric penalty or priority.");
        }
        for (int row = 0; row < model.getRowCount(); row++) {
            double value = ((Number) model.getValueAt(row, 2)).doubleValue();
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(
                        "Penalties and priorities must be finite, nonnegative numbers.");
            }
        }
    }

    List<InstrumentAffinityWeight> affinities(long player) {
        commit();
        List<InstrumentAffinityWeight> values = new ArrayList<>();
        for (int row = 0; row < instrumentIds.size(); row++) {
            String choice = (String) model.getValueAt(row, 1);
            if (PREFER.equals(choice) || ALWAYS.equals(choice)) {
                values.add(
                        new InstrumentAffinityWeight(
                                player,
                                instrumentIds.get(row),
                                ((Number) model.getValueAt(row, 2)).doubleValue(),
                                ALWAYS.equals(choice)));
            }
        }
        return values;
    }

    Map<Long, Double> avoided() {
        commit();
        Map<Long, Double> values = new LinkedHashMap<>();
        for (int row = 0; row < instrumentIds.size(); row++) {
            if (AVOID.equals(model.getValueAt(row, 1))) {
                values.put(
                        instrumentIds.get(row), ((Number) model.getValueAt(row, 2)).doubleValue());
            }
        }
        return values;
    }
}
