package com.aevoreth.abcmm.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.aevoreth.abcmm.domain.library.LibrarySong;

/**
 * Library table model with play/queue actions and Python-aligned columns.
 */
final class LibraryTableModel extends AbstractTableModel {

    static final String[] COLUMN_NAMES = {
            "Actions",
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

    static final int COL_ACTIONS = 0;
    static final int COL_TITLE = 1;
    static final int COL_COMPOSERS = 2;
    static final int COL_DURATION = 3;
    static final int COL_LAST_PLAYED = 4;
    static final int COL_PARTS = 5;
    static final int COL_RATING = 6;
    static final int COL_SET = 7;
    static final int COL_STATUS = 8;
    static final int COL_TRANSCRIBER = 9;

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
            case COL_ACTIONS -> String.class;
            case COL_DURATION, COL_PARTS, COL_RATING -> Integer.class;
            case COL_SET -> Boolean.class;
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
            case COL_ACTIONS -> "";
            case COL_TITLE -> song.title();
            case COL_COMPOSERS -> song.composers();
            case COL_DURATION -> song.durationSeconds() == null ? Integer.valueOf(-1) : song.durationSeconds();
            case COL_LAST_PLAYED -> song.lastPlayedAt() == null ? "" : song.lastPlayedAt();
            case COL_PARTS -> song.partCount();
            case COL_RATING -> song.rating() == null ? Integer.valueOf(-1) : song.rating();
            case COL_SET -> song.inUpcomingSet();
            case COL_STATUS -> song.statusName() == null ? "" : song.statusName();
            case COL_TRANSCRIBER -> song.transcriber() == null ? "" : song.transcriber();
            default -> "";
        };
    }

    String tooltipAt(int modelRow, int column) {
        LibrarySong song = songAt(modelRow);
        if (song == null) {
            return null;
        }
        if (column == COL_ACTIONS) {
            return "Play / Add to queue";
        }
        if (column != COL_PARTS) {
            return null;
        }
        List<String> names = song.partNames();
        if (names.isEmpty()) {
            return null;
        }
        return "Parts:\n" + String.join("\n", names);
    }

    void configureSorter(TableRowSorter<LibraryTableModel> sorter) {
        sorter.setSortable(COL_ACTIONS, false);
        sorter.setComparator(COL_TITLE, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(COL_COMPOSERS, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(COL_DURATION, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(COL_LAST_PLAYED, Comparator.comparing(o -> String.valueOf(o)));
        sorter.setComparator(COL_PARTS, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(COL_RATING, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(COL_SET, Comparator.comparing(o -> (Boolean) o));
        sorter.setComparator(COL_STATUS, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(COL_TRANSCRIBER, String.CASE_INSENSITIVE_ORDER);
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

    static final class ActionsRenderer extends DefaultTableCellRenderer {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        private final JLabel play = new JLabel(PlaybackIcons.play(14));
        private final JLabel plus = new JLabel(PlaybackIcons.plus(14));

        ActionsRenderer() {
            panel.setOpaque(true);
            play.setToolTipText("Play");
            plus.setToolTipText("Add to queue");
            panel.add(play);
            panel.add(plus);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            play.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            plus.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return panel;
        }
    }
}
