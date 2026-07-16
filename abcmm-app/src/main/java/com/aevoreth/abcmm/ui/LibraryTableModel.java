package com.aevoreth.abcmm.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.aevoreth.abcmm.domain.library.LibrarySong;

/**
 * Read-only library table model with Python-aligned columns (inline actions deferred).
 * Cell values are sort-friendly; renderers format display text.
 */
final class LibraryTableModel extends AbstractTableModel {

    static final String[] COLUMN_NAMES = {
            "Title",
            "Composer(s)",
            "Duration",
            "Last played",
            "Parts",
            "Rating",
            "Set",
            "Status",
            "Transcriber"
    };

    private final List<LibrarySong> songs = new ArrayList<>();

    void setSongs(List<LibrarySong> songs) {
        this.songs.clear();
        if (songs != null) {
            this.songs.addAll(songs);
        }
        fireTableDataChanged();
    }

    LibrarySong songAt(int modelRow) {
        if (modelRow < 0 || modelRow >= songs.size()) {
            return null;
        }
        return songs.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return songs.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 2, 4, 5 -> Integer.class;
            case 6 -> Boolean.class;
            default -> String.class;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LibrarySong song = songs.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> song.title();
            case 1 -> song.composers();
            case 2 -> song.durationSeconds() == null ? Integer.valueOf(-1) : song.durationSeconds();
            case 3 -> song.lastPlayedAt() == null ? "" : song.lastPlayedAt();
            case 4 -> song.partCount();
            case 5 -> song.rating() == null ? Integer.valueOf(-1) : song.rating();
            case 6 -> song.inUpcomingSet();
            case 7 -> song.statusName() == null ? "" : song.statusName();
            case 8 -> song.transcriber() == null ? "" : song.transcriber();
            default -> "";
        };
    }

    String tooltipAt(int modelRow, int column) {
        LibrarySong song = songAt(modelRow);
        if (song == null || column != 4) {
            return null;
        }
        List<String> names = song.partNames();
        if (names.isEmpty()) {
            return null;
        }
        return "Parts:\n" + String.join("\n", names);
    }

    void configureSorter(TableRowSorter<LibraryTableModel> sorter) {
        sorter.setComparator(0, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(1, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(2, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(3, Comparator.comparing(o -> String.valueOf(o)));
        sorter.setComparator(4, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(5, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(6, Comparator.comparing(o -> (Boolean) o));
        sorter.setComparator(7, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(8, String.CASE_INSENSITIVE_ORDER);
    }

    static final class DurationRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            Integer seconds = value instanceof Integer i ? i : null;
            if (seconds == null || seconds < 0) {
                setText("\u2014");
            } else {
                setText(LibraryDisplayFormats.formatDuration(seconds));
            }
        }
    }

    static final class LastPlayedRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            String iso = value == null ? null : String.valueOf(value);
            setText(LibraryDisplayFormats.formatLastPlayed(iso == null || iso.isBlank() ? null : iso));
        }
    }

    static final class RatingRenderer extends DefaultTableCellRenderer {

        private static final Color STAR_GOLD = new Color(255, 200, 0);
        private static final Color STAR_EMPTY = new Color(140, 140, 140);
        private static final float STAR_SIZE_DELTA = 4f;

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            Integer rating = value instanceof Integer i && i >= 0 ? i : null;
            int stars = rating == null ? 0 : Math.max(0, Math.min(5, rating));
            float size = table.getFont().getSize2D() + STAR_SIZE_DELTA;
            StringBuilder html = new StringBuilder("<html><span style='font-size:")
                    .append((int) size)
                    .append("pt'>");
            for (int i = 1; i <= 5; i++) {
                Color color = i <= stars ? STAR_GOLD : STAR_EMPTY;
                html.append("<span style='color:")
                        .append(LibraryDisplayFormats.toCssHex(color))
                        .append("'>")
                        .append(i <= stars
                                ? LibraryDisplayFormats.STAR_FILLED
                                : LibraryDisplayFormats.STAR_EMPTY)
                        .append("</span>");
            }
            html.append("</span></html>");
            label.setText(html.toString());
            label.setHorizontalAlignment(CENTER);
            return label;
        }
    }

    static final class SetRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            boolean inSet = Boolean.TRUE.equals(value);
            setText(LibraryDisplayFormats.formatSet(inSet));
            setHorizontalAlignment(CENTER);
        }
    }

    static final class EmDashRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            String text = value == null ? "" : String.valueOf(value);
            setText(text.isBlank() ? "\u2014" : text);
        }
    }

    static final class StatusCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            label.setOpaque(isSelected);
            String name = value == null ? "" : String.valueOf(value);
            if (name.isBlank()) {
                name = "\u2014";
            }
            Color statusColor = null;
            if (table.getModel() instanceof LibraryTableModel model) {
                LibrarySong song = model.songAt(table.convertRowIndexToModel(row));
                if (song != null) {
                    statusColor = LibraryDisplayFormats.parseStatusColor(song.statusColor());
                }
            }
            Color nameColor = isSelected ? table.getSelectionForeground() : table.getForeground();
            if (statusColor != null) {
                label.setText("<html><span style='color:"
                        + LibraryDisplayFormats.toCssHex(statusColor) + "'>"
                        + LibraryDisplayFormats.STATUS_DOT + "</span>&nbsp;<span style='color:"
                        + LibraryDisplayFormats.toCssHex(nameColor) + "'>"
                        + LibraryDisplayFormats.escapeHtml(name) + "</span></html>");
            } else {
                label.setText(LibraryDisplayFormats.STATUS_DOT + " " + name);
                label.setForeground(nameColor);
            }
            return label;
        }
    }
}
