package com.aevoreth.abcmm.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * Horizontal header that paints a column range with text rotated -45°, matching
 * Python {@code DiagonalHeaderView} for instrument columns on the Players tab.
 */
final class DiagonalTableHeader extends JTableHeader {

    private final int diagonalStart;
    private final int diagonalEndInclusive;

    DiagonalTableHeader(TableColumnModel columnModel, int diagonalStart, int diagonalEndInclusive) {
        super(columnModel);
        this.diagonalStart = diagonalStart;
        this.diagonalEndInclusive = diagonalEndInclusive;
        setReorderingAllowed(false);
    }

    static DiagonalTableHeader install(JTable table, int diagonalStart, int diagonalEndInclusive) {
        DiagonalTableHeader header = new DiagonalTableHeader(
                table.getColumnModel(), diagonalStart, diagonalEndInclusive);
        table.setTableHeader(header);
        return header;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension base = super.getPreferredSize();
        int minHeight = preferredDiagonalHeight();
        if (minHeight <= base.height) {
            return base;
        }
        return new Dimension(base.width, minHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Color bg = getBackground();
            if (bg == null) {
                bg = UIManager.getColor("TableHeader.background");
            }
            if (bg == null) {
                bg = getTable() != null ? getTable().getBackground() : Color.LIGHT_GRAY;
            }
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            Color sep = UIManager.getColor("Table.gridColor");
            if (sep == null) {
                sep = UIManager.getColor("TableHeader.separatorColor");
            }
            if (sep == null) {
                sep = Color.GRAY;
            }

            g2.setFont(getFont());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fg = getForeground();
            if (fg == null) {
                fg = UIManager.getColor("TableHeader.foreground");
            }
            if (fg == null) {
                fg = Color.BLACK;
            }
            g2.setColor(fg);

            FontMetrics fm = g2.getFontMetrics();
            int headerHeight = getHeight();
            int columnCount = getColumnModel().getColumnCount();

            for (int col = 0; col < columnCount; col++) {
                Rectangle rect = getHeaderRect(col);
                g2.setColor(sep);
                g2.drawLine(rect.x + rect.width - 1, 0, rect.x + rect.width - 1, headerHeight - 1);

                String text = headerText(col);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                g2.setColor(fg);
                if (isDiagonal(col)) {
                    AffineTransform saved = g2.getTransform();
                    // Bottom-center of cell, padded up 5px; rotate -45°; text runs up-right.
                    g2.translate(rect.x + rect.width / 2.0, headerHeight - 5.0);
                    g2.rotate(Math.toRadians(-45));
                    // Baseline so glyph bottoms sit near the origin (Python AlignBottom).
                    g2.drawString(text, 0, -fm.getDescent());
                    g2.setTransform(saved);
                } else {
                    int textWidth = fm.stringWidth(text);
                    int tx = rect.x + Math.max(0, (rect.width - textWidth) / 2);
                    int ty = headerHeight - 8 - fm.getDescent();
                    g2.drawString(text, tx, ty);
                }
            }
            g2.setColor(sep);
            g2.drawLine(0, headerHeight - 1, getWidth(), headerHeight - 1);
        } finally {
            g2.dispose();
        }
    }

    private boolean isDiagonal(int column) {
        return column >= diagonalStart && column <= diagonalEndInclusive;
    }

    private String headerText(int column) {
        JTable table = getTable();
        if (table != null && table.getModel() != null && column < table.getModel().getColumnCount()) {
            return table.getModel().getColumnName(column);
        }
        Object value = getColumnModel().getColumn(column).getHeaderValue();
        return value == null ? "" : String.valueOf(value);
    }

    private int preferredDiagonalHeight() {
        if (diagonalEndInclusive < diagonalStart) {
            return 0;
        }
        FontMetrics fm = getFontMetrics(getFont());
        double maxDiag = 0;
        int columnCount = getColumnModel().getColumnCount();
        for (int col = diagonalStart; col <= diagonalEndInclusive && col < columnCount; col++) {
            String text = headerText(col);
            if (text == null || text.isEmpty()) {
                continue;
            }
            double tw = fm.stringWidth(text);
            double th = fm.getHeight();
            maxDiag = Math.max(maxDiag, (tw + th) / Math.sqrt(2.0));
        }
        return maxDiag <= 0 ? 100 : (int) Math.ceil(maxDiag) + 12;
    }
}
