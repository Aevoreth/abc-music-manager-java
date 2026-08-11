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
 * Vector icons for playback transport controls.
 * Play / pause / stop colors match ABC Player's {@code play.png}, {@code pause.png},
 * and {@code stop.png} glyphs.
 */
final class PlaybackIcons {

    /** Lime green — ABC Player {@code play.png}. */
    static final Color PLAY_COLOR = new Color(132, 221, 11);
    /** Yellow — ABC Player {@code pause.png}. */
    static final Color PAUSE_COLOR = new Color(255, 216, 60);
    /** Orange-red — ABC Player {@code stop.png}. */
    static final Color STOP_COLOR = new Color(255, 77, 0);
    /** Sky blue — previous / next transport buttons. */
    static final Color SKIP_COLOR = new Color(56, 176, 240);

    private PlaybackIcons() {
    }

    static Icon play(int size) {
        return play(size, null);
    }

    /** Play triangle tinted like ABC Player (or theme foreground when {@code color} is null). */
    static Icon play(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
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
        return pause(size, null);
    }

    /** Pause bars tinted like ABC Player (or theme foreground when {@code color} is null). */
    static Icon pause(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
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
        return stop(size, null);
    }

    /** Stop square tinted like ABC Player (or theme foreground when {@code color} is null). */
    static Icon stop(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
            int pad = s / 4;
            g.fillRect(pad, pad, s - pad * 2, s - pad * 2);
        });
    }

    static Icon previous(int size) {
        return previous(size, null);
    }

    static Icon previous(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
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
        return next(size, null);
    }

    static Icon next(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
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
        return new GlyphIcon(size, null, (g, s) -> {
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
        return new GlyphIcon(size, null, (g, s) -> {
            g.setStroke(new BasicStroke(Math.max(1.8f, s / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int mid = s / 2;
            int pad = s / 4;
            g.drawLine(pad, mid, s - pad, mid);
            g.drawLine(mid, pad, mid, s - pad);
        });
    }

    /** Trash can — default red for destructive actions. */
    static final Color TRASH_COLOR = new Color(0xE53935);

    static Icon trash(int size) {
        return trash(size, TRASH_COLOR);
    }

    static Icon trash(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
            float stroke = Math.max(1.4f, s / 9f);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float left = s * 0.22f;
            float right = s * 0.78f;
            float lidY = s * 0.28f;
            float bodyTop = s * 0.36f;
            float bodyBottom = s * 0.82f;
            float handleLeft = s * 0.40f;
            float handleRight = s * 0.60f;
            float handleTop = s * 0.14f;
            // Lid handle
            g.drawLine(Math.round(handleLeft), Math.round(lidY), Math.round(handleLeft), Math.round(handleTop));
            g.drawLine(Math.round(handleRight), Math.round(lidY), Math.round(handleRight), Math.round(handleTop));
            g.drawLine(Math.round(handleLeft), Math.round(handleTop), Math.round(handleRight), Math.round(handleTop));
            // Lid
            g.drawLine(Math.round(left - s * 0.04f), Math.round(lidY), Math.round(right + s * 0.04f), Math.round(lidY));
            // Can body (slightly tapered)
            Path2D body = new Path2D.Float();
            body.moveTo(left, bodyTop);
            body.lineTo(right, bodyTop);
            body.lineTo(s * 0.72f, bodyBottom);
            body.lineTo(s * 0.28f, bodyBottom);
            body.closePath();
            g.draw(body);
            // Interior lines
            float mid = s * 0.5f;
            float innerLeft = s * 0.38f;
            float innerRight = s * 0.62f;
            g.drawLine(Math.round(mid), Math.round(bodyTop + stroke), Math.round(mid), Math.round(bodyBottom - stroke));
            g.drawLine(Math.round(innerLeft), Math.round(bodyTop + stroke), Math.round(s * 0.34f), Math.round(bodyBottom - stroke));
            g.drawLine(Math.round(innerRight), Math.round(bodyTop + stroke), Math.round(s * 0.66f), Math.round(bodyBottom - stroke));
        });
    }

    /** Padlock for locked setlists in the tree. */
    static Icon lock(int size) {
        return lock(size, null);
    }

    static Icon lock(int size, Color color) {
        return new GlyphIcon(size, color, (g, s) -> {
            float stroke = Math.max(1.3f, s / 9f);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int bodyX = Math.round(s * 0.22f);
            int bodyW = Math.round(s * 0.56f);
            int bodyY = Math.round(s * 0.45f);
            int bodyH = Math.round(s * 0.38f);
            g.fillRoundRect(bodyX, bodyY, bodyW, bodyH, Math.max(2, s / 6), Math.max(2, s / 6));
            int shackleW = Math.round(s * 0.38f);
            int shackleX = Math.round((s - shackleW) / 2f);
            int shackleTop = Math.round(s * 0.18f);
            int shackleBottom = bodyY + Math.round(stroke);
            g.drawArc(shackleX, shackleTop, shackleW, Math.round(s * 0.42f), 0, 180);
            g.drawLine(shackleX, Math.round(s * 0.39f), shackleX, shackleBottom);
            g.drawLine(shackleX + shackleW, Math.round(s * 0.39f), shackleX + shackleW, shackleBottom);
        });
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g, int size);
    }

    private static final class GlyphIcon implements Icon {
        private final int size;
        private final Color fixedColor;
        private final Painter painter;

        GlyphIcon(int size, Color fixedColor, Painter painter) {
            this.size = size;
            this.fixedColor = fixedColor;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color;
                if (c != null && !c.isEnabled()) {
                    color = Color.GRAY;
                } else if (fixedColor != null) {
                    color = fixedColor;
                } else {
                    color = c != null ? c.getForeground() : Color.BLACK;
                }
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
