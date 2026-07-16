package com.aevoreth.abcmm.ui;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.aevoreth.abcmm.domain.library.StatusInfo;

/**
 * Display formatting for library table cells (Python library_view helpers).
 */
final class LibraryDisplayFormats {

    static final char STAR_FILLED = '\u2605';
    static final char STAR_EMPTY = '\u2606';
    static final char STATUS_DOT = '\u2B24'; // ⬤

    private LibraryDisplayFormats() {
    }

    static Color parseStatusColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            if (value.length() == 6) {
                return new Color(Integer.parseInt(value, 16));
            }
            if (value.length() == 3) {
                int r = Integer.parseInt(value.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(value.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(value.substring(2, 3), 16) * 17;
                return new Color(r, g, b);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    static String toCssHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String statusFilterLabel(StatusInfo status) {
        if (status == null) {
            return "";
        }
        String name = status.name() == null ? "" : status.name();
        Color color = parseStatusColor(status.color());
        if (color == null) {
            return "<html>" + STATUS_DOT + " " + escapeHtml(name) + "</html>";
        }
        return "<html><span style='color:" + toCssHex(color) + "'>"
                + STATUS_DOT + "</span>&nbsp;"
                + escapeHtml(name) + "</html>";
    }

    static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return "\u2014";
        }
        int safe = Math.max(0, seconds);
        int minutes = safe / 60;
        int secs = safe % 60;
        return minutes + ":" + String.format("%02d", secs);
    }

    /** Format seconds as zero-padded {@code HH:MM} (target duration style). */
    static String formatHoursMinutes(Integer seconds) {
        if (seconds == null) {
            return "\u2014";
        }
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Format seconds as H:mm:ss / m:ss / s, supporting negative values (time remaining).
     */
    static String formatSignedDuration(int seconds) {
        boolean negative = seconds < 0;
        int safe = Math.abs(seconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int secs = safe % 60;
        String out;
        if (hours > 0) {
            out = hours + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", secs);
        } else if (minutes > 0) {
            out = minutes + ":" + String.format("%02d", secs);
        } else {
            out = String.valueOf(secs);
        }
        return negative ? "-" + out : out;
    }

    /**
     * Parses {@code m:ss}, {@code m:s}, or a plain seconds integer. Returns null if invalid.
     */
    static Integer parseDurationToSeconds(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            int colon = value.indexOf(':');
            if (colon < 0) {
                return Math.max(0, Integer.parseInt(value));
            }
            String minutesPart = value.substring(0, colon).trim();
            String secondsPart = value.substring(colon + 1).trim();
            int minutes = minutesPart.isEmpty() ? 0 : Integer.parseInt(minutesPart);
            int seconds = secondsPart.isEmpty() ? 0 : Integer.parseInt(secondsPart);
            if (minutes < 0 || seconds < 0 || seconds > 59) {
                return null;
            }
            return Math.addExact(Math.multiplyExact(minutes, 60), seconds);
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    /**
     * Parses {@code H:mm} / {@code HH:mm} (or plain minutes) into seconds. Returns null if invalid.
     */
    static Integer parseHoursMinutesToSeconds(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            int colon = value.indexOf(':');
            if (colon < 0) {
                int minutes = Integer.parseInt(value);
                if (minutes < 0) {
                    return null;
                }
                return Math.multiplyExact(minutes, 60);
            }
            String hoursPart = value.substring(0, colon).trim();
            String minutesPart = value.substring(colon + 1).trim();
            int hours = hoursPart.isEmpty() ? 0 : Integer.parseInt(hoursPart);
            int minutes = minutesPart.isEmpty() ? 0 : Integer.parseInt(minutesPart);
            if (hours < 0 || minutes < 0 || minutes > 59) {
                return null;
            }
            return Math.addExact(Math.multiplyExact(hours, 3600), Math.multiplyExact(minutes, 60));
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    static String formatLastPlayed(String iso) {
        if (iso == null || iso.isBlank()) {
            return "\u2014";
        }
        try {
            Instant played = Instant.parse(normalizeIso(iso));
            Duration delta = Duration.between(played, Instant.now());
            if (delta.isNegative()) {
                return "Just now";
            }
            long days = delta.toDays();
            if (days > 365) {
                return (days / 365) + "y ago";
            }
            if (days > 30) {
                return (days / 30) + "mo ago";
            }
            if (days > 0) {
                return days + "d ago";
            }
            long hours = delta.toHours();
            if (hours >= 1) {
                return hours + "h ago";
            }
            long minutes = delta.toMinutes();
            if (minutes >= 1) {
                return minutes + "m ago";
            }
            return "Just now";
        } catch (DateTimeParseException | ArithmeticException ex) {
            return iso;
        }
    }

    static String formatRating(Integer rating) {
        int stars = rating == null ? 0 : Math.max(0, Math.min(5, rating));
        StringBuilder builder = new StringBuilder(5);
        for (int i = 1; i <= 5; i++) {
            builder.append(i <= stars ? STAR_FILLED : STAR_EMPTY);
        }
        return builder.toString();
    }

    static String formatSet(boolean inUpcomingSet) {
        return inUpcomingSet ? "\u2713" : "";
    }

    private static String normalizeIso(String iso) {
        String value = iso.trim();
        if (value.endsWith("Z") || value.contains("+") || value.lastIndexOf('-') > 10) {
            return value;
        }
        // SQLite / Python sometimes store space-separated local timestamps; treat as UTC.
        if (value.length() == 19 && value.charAt(10) == ' ') {
            return value.replace(' ', 'T') + "Z";
        }
        if (value.length() == 19 && value.charAt(10) == 'T') {
            return value + "Z";
        }
        return value;
    }
}
