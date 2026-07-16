package com.aevoreth.abcmm.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/**
 * Small vector icons for playback transport controls.
 */
final class PlaybackIcons {

    private PlaybackIcons() {
    }

    static Icon play(int size) {
        return new GlyphIcon(size, (g, s) -> {
            Path2D path = new Path2D.Float();
            float left = s * 0.28f;
            float top = s * 0.2f;
            float bottom = s * 0.8f;
            float right = s * 0.78f;
            path.moveTo(left, top);
            path.lineTo(right, s * 0.5f);
            path.lineTo(left, bottom);
            path.closePath();
            g.fill(path);
        });
    }

    static Icon pause(int size) {
        return new GlyphIcon(size, (g, s) -> {
            int barW = Math.max(2, s / 5);
            int gap = Math.max(2, s / 6);
            int left = (s - (barW * 2 + gap)) / 2;
            int top = s / 5;
            int height = s - top * 2;
            g.fillRect(left, top, barW, height);
            g.fillRect(left + barW + gap, top, barW, height);
        });
    }

    static Icon stop(int size) {
        return new GlyphIcon(size, (g, s) -> {
            int pad = s / 4;
            g.fillRect(pad, pad, s - pad * 2, s - pad * 2);
        });
    }

    static Icon previous(int size) {
        return new GlyphIcon(size, (g, s) -> {
            int barW = Math.max(2, s / 8);
            g.fillRect(s / 5, s / 5, barW, s - s / 5 * 2);
            Path2D path = new Path2D.Float();
            path.moveTo(s * 0.78f, s * 0.2f);
            path.lineTo(s * 0.35f, s * 0.5f);
            path.lineTo(s * 0.78f, s * 0.8f);
            path.closePath();
            g.fill(path);
        });
    }

    static Icon next(int size) {
        return new GlyphIcon(size, (g, s) -> {
            int barW = Math.max(2, s / 8);
            g.fillRect(s - s / 5 - barW, s / 5, barW, s - s / 5 * 2);
            Path2D path = new Path2D.Float();
            path.moveTo(s * 0.22f, s * 0.2f);
            path.lineTo(s * 0.65f, s * 0.5f);
            path.lineTo(s * 0.22f, s * 0.8f);
            path.closePath();
            g.fill(path);
        });
    }

    static Icon list(int size) {
        return new GlyphIcon(size, (g, s) -> {
            g.setStroke(new BasicStroke(Math.max(1.5f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int left = s / 4;
            int right = s - s / 5;
            int y1 = s / 4;
            int y2 = s / 2;
            int y3 = s - s / 4;
            g.drawLine(left, y1, right, y1);
            g.drawLine(left, y2, right, y2);
            g.drawLine(left, y3, right, y3);
        });
    }

    static Icon plus(int size) {
        return new GlyphIcon(size, (g, s) -> {
            g.setStroke(new BasicStroke(Math.max(1.8f, s / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int mid = s / 2;
            int pad = s / 4;
            g.drawLine(pad, mid, s - pad, mid);
            g.drawLine(mid, pad, mid, s - pad);
        });
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g, int size);
    }

    private static final class GlyphIcon implements Icon {
        private final int size;
        private final Painter painter;

        GlyphIcon(int size, Painter painter) {
            this.size = size;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = c != null && c.isEnabled() ? c.getForeground() : Color.GRAY;
                g2.setColor(color);
                painter.paint(g2, size);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
