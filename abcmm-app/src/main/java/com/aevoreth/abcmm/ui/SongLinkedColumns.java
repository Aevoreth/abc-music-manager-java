package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.aevoreth.abcmm.domain.library.LibrarySong;

/**
 * Library columns that can be shown on the setlist editor and Set Play song tables.
 * Values come from the original {@link LibrarySong}, not a copy on the setlist item.
 */
final class SongLinkedColumns {

    enum Kind {
        LAST_PLAYED("Last played", "Last played"),
        HISTORY("Playback History", "Playback History"),
        RATING("Rating", "Rating"),
        SET("Set", "Set"),
        /** Set Play already uses "Status" for the NOW/NEXT badge. */
        STATUS("Status", "Song status"),
        TRANSCRIBER("Transcriber", "Transcriber"),
        EDIT("Edit", "Edit");

        private final String menuLabel;
        private final String setPlayHeader;

        Kind(String menuLabel, String setPlayHeader) {
            this.menuLabel = menuLabel;
            this.setPlayHeader = setPlayHeader;
        }

        /** Name used on the Library page and in the column menu. */
        String menuLabel() {
            return menuLabel;
        }

        String columnHeader(boolean setPlay) {
            return setPlay ? setPlayHeader : menuLabel;
        }

        String menuTip() {
            return switch (this) {
                case LAST_PLAYED -> "Last played time from the original song";
                case HISTORY -> "Mark played, set a time, or open history on the original song";
                case RATING -> "Set the original song's rating";
                case SET -> "Open a setlist that contains the original song";
                case STATUS -> "Set the original song's status";
                case TRANSCRIBER -> "Transcriber from the original song";
                case EDIT -> "Open the original song";
            };
        }
    }

    private SongLinkedColumns() {
    }

    static Kind kindAt(int modelIndex, int firstModelIndex) {
        int offset = modelIndex - firstModelIndex;
        Kind[] values = Kind.values();
        if (offset < 0 || offset >= values.length) {
            return null;
        }
        return values[offset];
    }

    static String[] menuLabels() {
        Kind[] values = Kind.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].menuLabel();
        }
        return labels;
    }

    static Object value(Kind kind, LibrarySong song) {
        if (song == null || kind == null) {
            return null;
        }
        return switch (kind) {
            case LAST_PLAYED -> song.lastPlayedAt() == null ? "" : song.lastPlayedAt();
            case HISTORY, EDIT -> "";
            case RATING -> song.rating() == null ? Integer.valueOf(0) : song.rating();
            case SET -> song.inUpcomingSet();
            case STATUS -> song.statusName() == null ? "" : song.statusName();
            case TRANSCRIBER -> song.transcriber() == null ? "" : song.transcriber();
        };
    }

    static String cellTooltip(Kind kind, LibrarySong song) {
        if (kind == null) {
            return null;
        }
        if (song == null) {
            return "Song is not in this library";
        }
        return switch (kind) {
            case HISTORY -> "Played Now — Set… date/time — History: playback log";
            case RATING -> "Click stars to set rating; click again to clear";
            case SET -> song.inUpcomingSet() ? "Go to setlist" : null;
            case STATUS -> "Click to change status";
            case EDIT -> "Edit song";
            case LAST_PLAYED, TRANSCRIBER -> null;
        };
    }

    static void installRenderer(JTable table, TableColumn column, Kind kind, IntFunction<LibrarySong> songAtViewRow) {
        if (column == null || kind == null) {
            return;
        }
        TableCellRenderer delegate = switch (kind) {
            case LAST_PLAYED -> new LibraryTableModel.LastPlayedRenderer();
            case HISTORY -> new LibraryTableModel.HistoryRenderer();
            case RATING -> new LibraryTableModel.RatingRenderer();
            case SET -> new LibraryTableModel.SetRenderer();
            case STATUS -> new StatusRenderer(songAtViewRow);
            case TRANSCRIBER -> new LibraryTableModel.EmDashRenderer();
            case EDIT -> new LibraryTableModel.EditRenderer();
        };
        DefaultTableCellRenderer missing = new DefaultTableCellRenderer();
        missing.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        column.setCellRenderer((tbl, value, isSelected, hasFocus, row, col) -> {
            LibrarySong song = songAtViewRow == null ? null : songAtViewRow.apply(row);
            if (song == null && kind != Kind.STATUS) {
                return missing.getTableCellRendererComponent(tbl, "\u2014", isSelected, hasFocus, row, col);
            }
            return delegate.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
        });
    }

    static void applyDefaultWidth(JTable table, TableColumn column, Kind kind) {
        if (column == null || kind == null || table.getTableHeader() == null) {
            return;
        }
        FontMetrics headerMetrics = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int pad = 16;
        int width = switch (kind) {
            case LAST_PLAYED -> headerMetrics.stringWidth("Last played") + pad;
            case HISTORY -> 140;
            case RATING -> {
                Font starFont = table.getFont().deriveFont(table.getFont().getSize2D() + 4f);
                yield table.getFontMetrics(starFont).stringWidth("\u2605\u2605\u2605\u2605\u2605") + pad;
            }
            case SET -> headerMetrics.stringWidth("Set") + pad;
            case STATUS -> 100;
            case TRANSCRIBER -> 120;
            case EDIT -> 52;
        };
        int safe = Math.max(width, 28);
        column.setMinWidth(kind == Kind.EDIT ? 44 : 28);
        column.setPreferredWidth(safe);
        column.setWidth(safe);
        column.setMaxWidth(switch (kind) {
            case HISTORY -> 180;
            case EDIT -> 64;
            case STATUS -> 160;
            case TRANSCRIBER -> Integer.MAX_VALUE;
            default -> Math.max(safe, 28);
        });
    }

    /**
     * Right-click the header to show or hide these Library columns.
     * {@code fixedLeading} is how many leading columns stay put when a column is shown
     * on a table that allows reordering. Pass a negative value to always append.
     */
    static void attachHeaderMenu(
            JTable table,
            TableColumn[] columns,
            String[] labels,
            int fixedLeading,
            Runnable onChanged) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        JMenuItem title = new JMenuItem("Library columns");
        title.setEnabled(false);
        menu.add(title);
        menu.addSeparator();
        JCheckBoxMenuItem[] items = new JCheckBoxMenuItem[columns.length];
        for (int i = 0; i < columns.length; i++) {
            TableColumn column = columns[i];
            String label = labels[i];
            Kind kind = Kind.values()[i];
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, isVisible(table, column));
            item.setToolTipText(kind.menuTip());
            items[i] = item;
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    showColumn(table, column, fixedLeading);
                } else if (table.getColumnCount() <= 1) {
                    item.setSelected(true);
                } else {
                    table.removeColumn(column);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            });
            menu.add(item);
        }
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                for (int i = 0; i < items.length; i++) {
                    items[i].setSelected(isVisible(table, columns[i]));
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        table.getTableHeader().setComponentPopupMenu(menu);
    }

    static void applyHidden(
            JTable table,
            TableColumn[] columns,
            String[] labels,
            Collection<String> hidden,
            int fixedLeading) {
        Set<String> hide = new HashSet<>();
        if (hidden != null) {
            hide.addAll(hidden);
        }
        for (int i = 0; i < columns.length; i++) {
            boolean shouldHide = hide.contains(labels[i]);
            boolean visible = isVisible(table, columns[i]);
            if (shouldHide && visible) {
                table.removeColumn(columns[i]);
            } else if (!shouldHide && !visible) {
                showColumn(table, columns[i], fixedLeading);
            }
        }
    }

    static List<String> hiddenLabels(JTable table, TableColumn[] columns, String[] labels) {
        List<String> hidden = new ArrayList<>();
        for (int i = 0; i < columns.length; i++) {
            if (!isVisible(table, columns[i])) {
                hidden.add(labels[i]);
            }
        }
        return hidden;
    }

    static void hideAll(JTable table, TableColumn[] columns) {
        for (TableColumn column : columns) {
            if (column != null && isVisible(table, column)) {
                table.removeColumn(column);
            }
        }
    }

    private static boolean isVisible(JTable table, TableColumn column) {
        return column != null && table.convertColumnIndexToView(column.getModelIndex()) >= 0;
    }

    private static void showColumn(JTable table, TableColumn column, int fixedLeading) {
        if (column == null || isVisible(table, column)) {
            return;
        }
        table.addColumn(column);
        if (!table.getTableHeader().getReorderingAllowed() || fixedLeading < 0) {
            return;
        }
        int from = table.getColumnCount() - 1;
        int modelIndex = column.getModelIndex();
        int to = from;
        int start = Math.min(fixedLeading, from);
        for (int view = start; view < from; view++) {
            if (table.getColumnModel().getColumn(view).getModelIndex() > modelIndex) {
                to = view;
                break;
            }
        }
        if (to != from && to >= fixedLeading) {
            table.moveColumn(from, to);
        }
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        private final IntFunction<LibrarySong> songAtViewRow;

        StatusRenderer(IntFunction<LibrarySong> songAtViewRow) {
            this.songAtViewRow = songAtViewRow;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            LibrarySong song = songAtViewRow == null ? null : songAtViewRow.apply(row);
            String name = song == null || song.statusName() == null || song.statusName().isBlank()
                    ? "\u2014"
                    : song.statusName();
            java.awt.Color statusColor = song == null
                    ? null
                    : LibraryDisplayFormats.parseStatusColor(song.statusColor());
            java.awt.Color nameColor = isSelected ? table.getSelectionForeground() : table.getForeground();
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
