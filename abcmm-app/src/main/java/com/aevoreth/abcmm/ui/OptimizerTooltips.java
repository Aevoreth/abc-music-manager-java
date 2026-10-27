package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

/** Short explanations on controls, table cells, headers, and active editors. */
final class OptimizerTooltips {
    private OptimizerTooltips() { }

    static void describe(JComponent component, String text) {
        component.setToolTipText(text);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent nested) describe(nested, text);
        }
    }

    static JTable table(TableModel model, String... descriptions) {
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int column = columnAtPoint(event.getPoint());
                return column < 0 ? null : descriptions[convertColumnIndexToModel(column)];
            }

            @Override
            public Component prepareEditor(TableCellEditor editor, int row, int column) {
                Component component = super.prepareEditor(editor, row, column);
                if (component instanceof JComponent field) describe(field, descriptions[convertColumnIndexToModel(column)]);
                return component;
            }
        };
        table.setToolTipText(descriptions[0]);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int column = columnAtPoint(event.getPoint());
                return column < 0 ? null : descriptions[table.convertColumnIndexToModel(column)];
            }
        });
        table.getTableHeader().setToolTipText(descriptions[0]);
        return table;
    }
}
