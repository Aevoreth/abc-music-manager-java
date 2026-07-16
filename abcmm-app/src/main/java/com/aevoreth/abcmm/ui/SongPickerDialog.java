package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.SongRepository;

/**
 * Simple searchable picker for {@link LibrarySong} rows.
 */
public final class SongPickerDialog extends JDialog {

    private final SongTableModel tableModel = new SongTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<SongTableModel> sorter = new TableRowSorter<>(tableModel);
    private LibrarySong selected;

    public SongPickerDialog(java.awt.Window owner, SongRepository songs) {
        super(owner, "Add song", ModalityType.APPLICATION_MODAL);
        Objects.requireNonNull(songs, "songs");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(560, 420));
        setPreferredSize(new Dimension(640, 480));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(sorter);
        table.setAutoCreateRowSorter(false);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    acceptSelection();
                }
            }
        });

        JTextField search = new JTextField(24);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter(search.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter(search.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter(search.getText());
            }
        });

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Search"));
        north.add(search);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton add = new JButton("Add");
        cancel.addActionListener(e -> {
            selected = null;
            dispose();
        });
        add.addActionListener(e -> acceptSelection());
        buttons.add(cancel);
        buttons.add(add);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(north, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        try {
            tableModel.setSongs(songs.listLibrarySongs(LibraryFilter.cleared()));
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(
                    owner,
                    ex.getMessage(),
                    "Add song",
                    JOptionPane.ERROR_MESSAGE);
        }
        pack();
        setLocationRelativeTo(owner);
    }

    public Optional<LibrarySong> selectedSong() {
        return Optional.ofNullable(selected);
    }

    /**
     * Shows the dialog and returns the chosen song, if any.
     */
    public static Optional<LibrarySong> showPicker(java.awt.Window owner, SongRepository songs) {
        SongPickerDialog dialog = new SongPickerDialog(owner, songs);
        dialog.setVisible(true);
        return dialog.selectedSong();
    }

    private void acceptSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a song.", "Add song", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        selected = tableModel.songAt(modelRow);
        dispose();
    }

    private void applyFilter(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends SongTableModel, ? extends Integer> entry) {
                LibrarySong song = entry.getModel().songAt(entry.getIdentifier());
                String title = song.title() == null ? "" : song.title().toLowerCase(Locale.ROOT);
                String composers = song.composers() == null ? "" : song.composers().toLowerCase(Locale.ROOT);
                return title.contains(query) || composers.contains(query);
            }
        });
    }

    private static final class SongTableModel extends AbstractTableModel {
        private final List<LibrarySong> songs = new ArrayList<>();
        private final String[] columns = {"Title", "Composers", "Duration", "Parts"};

        void setSongs(List<LibrarySong> next) {
            songs.clear();
            if (next != null) {
                songs.addAll(next);
            }
            fireTableDataChanged();
        }

        LibrarySong songAt(int row) {
            return songs.get(row);
        }

        @Override
        public int getRowCount() {
            return songs.size();
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
            LibrarySong song = songs.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> song.title();
                case 1 -> song.composers();
                case 2 -> LibraryDisplayFormats.formatDuration(song.durationSeconds());
                case 3 -> song.partCount();
                default -> "";
            };
        }
    }
}
