package com.aevoreth.abcmm.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;

/**
 * Read-only Set Play band layout grid: pan + re-center, player highlight.
 * Uses the same Maestro card chrome as {@link SetlistBandAssignmentPanel}.
 */
public final class SetPlayBandGridPanel extends JPanel {

    private static final int UNIT_SIZE = BandLayoutGridPanel.UNIT_SIZE;
    private static final Color CANVAS_BG = Color.BLACK;
    private static final Color GRID_DOT = new Color(0x3A3A3A);
    private static final Color CARD_BORDER = new Color(0x777777);
    private static final Color CARD_SELECTED_FILL = new Color(0x3D5A80);
    private static final Color CARD_SELECTED_BORDER = new Color(0x98C1D9);
    private static final Color TEXT = Color.WHITE;
    private static final Color NEIGHBOR_PART = new Color(0x98C1D9);
    private static final Color DUP_RED = new Color(0xFF4444);
    private static final Color WARN_ORANGE = new Color(0xD48A3A);

    private final List<SetPlayLayoutCard> cards = new ArrayList<>();
    private final Set<Long> highlightPlayerIds = new HashSet<>();
    private final GridCanvas canvas = new GridCanvas();

    private double panX;
    private double panY;
    private boolean panning;
    private Point panStart;

    public SetPlayBandGridPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Up next — band layout"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton recenter = new JButton("Re-center");
        recenter.addActionListener(e -> fitCardsToView());
        toolbar.add(recenter);

        canvas.setPreferredSize(new Dimension(480, 220));
        add(toolbar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                panning = true;
                panStart = e.getPoint();
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!panning || panStart == null) {
                    return;
                }
                double dx = (e.getX() - panStart.x) / (double) UNIT_SIZE;
                double dy = (e.getY() - panStart.y) / (double) UNIT_SIZE;
                panX -= dx;
                panY -= dy;
                panStart = e.getPoint();
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panning = false;
                panStart = null;
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    public void setCards(List<SetPlayLayoutCard> nextCards) {
        cards.clear();
        if (nextCards != null) {
            cards.addAll(nextCards);
        }
        canvas.setVisible(!cards.isEmpty());
        fitCardsToView();
        revalidate();
        repaint();
    }

    public void setHighlightPlayerIds(Set<Long> playerIds) {
        highlightPlayerIds.clear();
        if (playerIds != null) {
            highlightPlayerIds.addAll(playerIds);
        }
        canvas.repaint();
    }

    public void clear() {
        cards.clear();
        highlightPlayerIds.clear();
        canvas.setVisible(false);
        panX = 0;
        panY = 0;
        revalidate();
        repaint();
    }

    public void fitCardsToView() {
        if (cards.isEmpty()) {
            panX = 0;
            panY = 0;
            canvas.repaint();
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SetPlayLayoutCard card : cards) {
            minX = Math.min(minX, card.x());
            maxX = Math.max(maxX, card.x() + card.widthUnits());
            minY = Math.min(minY, card.y());
            maxY = Math.max(maxY, card.y() + card.heightUnits());
        }
        panX = (minX + maxX) / 2.0;
        panY = (minY + maxY) / 2.0;
        canvas.repaint();
    }

    private Point2D.Double logicalToView(double lx, double ly) {
        double cx = canvas.getWidth() / 2.0;
        double cy = canvas.getHeight() / 2.0;
        return new Point2D.Double(
                (lx - panX) * UNIT_SIZE + cx,
                (ly - panY) * UNIT_SIZE + cy);
    }

    private Point2D.Double viewToLogical(double vx, double vy) {
        double cx = canvas.getWidth() / 2.0;
        double cy = canvas.getHeight() / 2.0;
        return new Point2D.Double(
                (vx - cx) / UNIT_SIZE + panX,
                (vy - cy) / UNIT_SIZE + panY);
    }

    private Rectangle cardBounds(SetPlayLayoutCard card) {
        Point2D.Double topLeft = logicalToView(card.x(), card.y());
        return new Rectangle(
                (int) Math.round(topLeft.x),
                (int) Math.round(topLeft.y),
                card.widthUnits() * UNIT_SIZE,
                card.heightUnits() * UNIT_SIZE);
    }

    private static void drawFitting(
            Graphics2D g2, Font base, String text, int x, int y, int width, int height, Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int size = base.getSize();
        FontMetrics fm;
        Font font = base;
        while (size >= 6) {
            font = base.deriveFont((float) size);
            fm = g2.getFontMetrics(font);
            if (fm.stringWidth(text) <= width) {
                break;
            }
            size--;
        }
        g2.setFont(font);
        g2.setColor(color);
        fm = g2.getFontMetrics();
        int tx = x + Math.max(0, (width - fm.stringWidth(text)) / 2);
        int ty = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);
    }

    private static String elide(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisW = fm.stringWidth(ellipsis);
        if (maxWidth <= ellipsisW) {
            return ellipsis;
        }
        for (int i = text.length() - 1; i >= 0; i--) {
            String candidate = text.substring(0, i) + ellipsis;
            if (fm.stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private static Color cardFill() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : new Color(0x2B2B2B);
    }

    private final class GridCanvas extends JPanel {
        GridCanvas() {
            setBackground(CANVAS_BG);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(GRID_DOT);
            Point2D.Double topLeft = viewToLogical(0, 0);
            Point2D.Double bottomRight = viewToLogical(getWidth(), getHeight());
            int lxMin = (int) Math.floor(topLeft.x);
            int lxMax = (int) Math.ceil(bottomRight.x) + 1;
            int lyMin = (int) Math.floor(topLeft.y);
            int lyMax = (int) Math.ceil(bottomRight.y) + 1;
            for (int lx = lxMin; lx <= lxMax; lx++) {
                for (int ly = lyMin; ly <= lyMax; ly++) {
                    Point2D.Double view = logicalToView(lx, ly);
                    int vx = (int) Math.round(view.x);
                    int vy = (int) Math.round(view.y);
                    if (vx >= 0 && vx < getWidth() && vy >= 0 && vy < getHeight()) {
                        g2.fillRect(vx, vy, 1, 1);
                    }
                }
            }

            Font baseFont = getFont().deriveFont(Font.PLAIN, 13f);
            final int lineGap = 1;
            Color defaultFill = cardFill();
            for (SetPlayLayoutCard card : cards) {
                Rectangle r = cardBounds(card);
                boolean highlighted = highlightPlayerIds.contains(card.playerId());
                g2.setColor(highlighted ? CARD_SELECTED_FILL : defaultFill);
                g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.setStroke(new BasicStroke(highlighted ? 2f : 1f));
                g2.setColor(highlighted ? CARD_SELECTED_BORDER : CARD_BORDER);
                g2.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);

                int margin = 3;
                int innerX = r.x + margin;
                int innerW = r.width - 2 * margin;
                int y = r.y + margin;
                FontMetrics fm = g2.getFontMetrics(baseFont);
                int lineH = fm.getAscent() + fm.getDescent();

                if (card.useSetlistPlayerHeader()) {
                    Font neighborFont = baseFont.deriveFont(Font.BOLD);
                    FontMetrics neighborFm = g2.getFontMetrics(neighborFont);
                    int gutter = neighborFm.stringWidth("999") + 6;
                    g2.setFont(neighborFont);
                    g2.setColor(NEIGHBOR_PART);
                    g2.drawString(
                            card.neighborPrevPartLabel() == null ? "" : card.neighborPrevPartLabel(),
                            innerX,
                            y + neighborFm.getAscent());
                    String next = card.neighborNextPartLabel() == null
                            ? ""
                            : card.neighborNextPartLabel();
                    g2.drawString(
                            next,
                            innerX + innerW - neighborFm.stringWidth(next),
                            y + neighborFm.getAscent());
                    String name = card.playerName();
                    int centerW = Math.max(1, innerW - 2 * gutter);
                    String elided = elide(fm, name, centerW);
                    g2.setFont(baseFont);
                    g2.setColor(TEXT);
                    int nameX = innerX + gutter + (centerW - fm.stringWidth(elided)) / 2;
                    g2.drawString(elided, nameX, y + fm.getAscent());
                } else {
                    drawFitting(g2, baseFont, card.playerName(), innerX, y, innerW, lineH, TEXT);
                }
                y += lineH + lineGap;

                Font big = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 16f);
                FontMetrics bigFm = g2.getFontMetrics(big);
                int bigH = bigFm.getAscent() + bigFm.getDescent();
                Color partColor = card.partDuplicate()
                        ? DUP_RED
                        : (card.instrumentChangedFromPriorInSet() ? WARN_ORANGE : TEXT);
                drawFitting(g2, big, card.partNumber(), innerX, y, innerW, bigH, partColor);
                y += bigH + lineGap;

                Color instColor = card.partDuplicate()
                        ? DUP_RED
                        : (card.instrumentWarning() ? WARN_ORANGE : TEXT);
                drawFitting(
                        g2, baseFont, card.instrumentName(), innerX, y, innerW, lineH, instColor);
                y += lineH + lineGap;

                Font small = baseFont.deriveFont(Math.max(10f, baseFont.getSize2D() - 1f));
                Color nameColor = card.partDuplicate() ? DUP_RED : TEXT;
                int remaining = Math.max(lineH, r.y + r.height - margin - y);
                drawFitting(
                        g2, small, card.partName(), innerX, y, innerW, remaining, nameColor);
            }
            g2.dispose();
        }
    }
}
