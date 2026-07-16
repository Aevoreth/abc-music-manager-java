package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.PlayLogEntry;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;

/**
 * View/edit/delete play log entries for a song.
 */
final class PlayHistoryDialog extends JDialog {

    private static final DateTimeFormatter LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final PlayLogRepository playLogRepository;
    private final long songId;
    private final String songTitle;
    private final HistoryTableModel model = new HistoryTableModel();
    private final JTable table = new JTable(model);
    private boolean changed;

    PlayHistoryDialog(Window owner, PlayLogRepository playLogRepository, long songId, String songTitle) {
        super(owner, "Play history — " + (songTitle == null ? "" : songTitle), ModalityType.APPLICATION_MODAL);
        this.playLogRepository = playLogRepository;
        this.songId = songId;
        this.songTitle = songTitle == null ? "" : songTitle;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton("Add…");
        add.addActionListener(e -> addEntry());
        JButton edit = new JButton("Edit…");
        edit.addActionListener(e -> editSelected());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(560, 360);
        setLocationRelativeTo(owner);
        reload();
    }

    boolean showDialog() {
        setVisible(true);
        return changed;
    }

    private void reload() {
        try {
            model.setEntries(playLogRepository.getPlayHistory(songId, 500));
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Play history", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addEntry() {
        PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                this, "Add play", Instant.now(), null, true);
        dialog.showDialog().ifPresent(result -> {
            try {
                playLogRepository.logPlayAt(songId, result.playedAtIso(), null, result.contextNote());
                changed = true;
                reload();
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Play history", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void editSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        PlayLogEntry entry = model.entryAt(viewRow);
        if (entry == null) {
            return;
        }
        Instant initial = parseInstant(entry.playedAt());
        PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                this, "Edit play", initial, entry.contextNote(), true);
        dialog.showDialog().ifPresent(result -> {
            try {
                playLogRepository.updatePlay(entry.id(), result.playedAtIso(), result.contextNote());
                changed = true;
                reload();
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Play history", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        PlayLogEntry entry = model.entryAt(viewRow);
        if (entry == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this play entry?",
                "Play history",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            playLogRepository.deletePlay(entry.id());
            changed = true;
            reload();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Play history", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(iso.replace("Z", "+00:00"));
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private static final class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"When", "Setlist", "Note"};
        private List<PlayLogEntry> entries = List.of();

        void setEntries(List<PlayLogEntry> entries) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            fireTableDataChanged();
        }

        PlayLogEntry entryAt(int row) {
            if (row < 0 || row >= entries.size()) {
                return null;
            }
            return entries.get(row);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PlayLogEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> formatWhen(entry.playedAt());
                case 1 -> entry.setlistName() == null ? "\u2014" : entry.setlistName();
                case 2 -> entry.contextNote() == null ? "" : entry.contextNote();
                default -> "";
            };
        }

        private static String formatWhen(String iso) {
            try {
                return LOCAL.format(Instant.parse(iso.replace("Z", "+00:00")));
            } catch (Exception ex) {
                return iso == null ? "" : iso;
            }
        }
    }
}
