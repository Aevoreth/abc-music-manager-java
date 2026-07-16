package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Searchable player picker (Python {@code AddPlayerDialog} parity).
 */
public final class PlayerPickerDialog extends JDialog {

    private final PlayerRepository playerRepository;
    private final Set<Long> excludePlayerIds;
    private final DefaultListModel<PlayerInfo> listModel = new DefaultListModel<>();
    private final JList<PlayerInfo> list = new JList<>(listModel);
    private final JTextField search = new JTextField(28);
    private final List<PlayerInfo> allPlayers = new ArrayList<>();
    private PlayerInfo selected;

    public PlayerPickerDialog(
            Window owner,
            PlayerRepository playerRepository,
            Set<Long> excludePlayerIds,
            String title) {
        super(owner, title == null || title.isBlank() ? "Add Player" : title, ModalityType.APPLICATION_MODAL);
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.excludePlayerIds = excludePlayerIds == null ? Set.of() : Set.copyOf(excludePlayerIds);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(360, 320));
        setPreferredSize(new Dimension(400, 360));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (value != null) {
                String name = value.name() == null || value.name().isBlank()
                        ? ("#" + value.id())
                        : value.name();
                label.setText(name);
            }
            if (isSelected) {
                label.setBackground(jList.getSelectionBackground());
                label.setForeground(jList.getSelectionForeground());
            } else {
                label.setBackground(jList.getBackground());
                label.setForeground(jList.getForeground());
            }
            return label;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    acceptSelection();
                }
            }
        });

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> acceptSelection());
        cancel.addActionListener(e -> {
            selected = null;
            dispose();
        });

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(new JLabel("Search"), BorderLayout.WEST);
        north.add(search, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(north, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        loadPlayers();
        pack();
        setLocationRelativeTo(owner);
    }

    public Optional<PlayerInfo> showDialog() {
        setVisible(true);
        return Optional.ofNullable(selected);
    }

    private void loadPlayers() {
        allPlayers.clear();
        try {
            for (PlayerInfo player : playerRepository.listPlayers()) {
                if (!excludePlayerIds.contains(player.id())) {
                    allPlayers.add(player);
                }
            }
        } catch (LibraryException ex) {
            // Leave empty; caller can show a message if needed.
        }
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        PlayerInfo previous = list.getSelectedValue();
        listModel.clear();
        for (PlayerInfo player : allPlayers) {
            String name = player.name() == null ? "" : player.name();
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
                listModel.addElement(player);
            }
        }
        if (previous != null) {
            list.setSelectedValue(previous, true);
        } else if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void acceptSelection() {
        PlayerInfo value = list.getSelectedValue();
        if (value == null) {
            return;
        }
        selected = value;
        dispose();
    }
}
