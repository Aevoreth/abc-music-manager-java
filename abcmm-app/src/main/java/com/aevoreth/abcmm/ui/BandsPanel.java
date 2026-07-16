package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Bands and Players management. A band is exactly one layout; members come from grid cards.
 */
public final class BandsPanel extends JPanel {

    private PlayerRepository playerRepository;
    private BandRepository bandRepository;

    private final DefaultListModel<PlayerInfo> playerListModel = new DefaultListModel<>();
    private final JList<PlayerInfo> playerList = new JList<>(playerListModel);

    private final DefaultListModel<BandInfo> bandListModel = new DefaultListModel<>();
    private final JList<BandInfo> bandList = new JList<>(bandListModel);

    private final JTextField bandNameField = new JTextField(20);
    private final JTextArea bandNotesArea = new JTextArea(3, 28);
    private final BandLayoutGridPanel layoutGrid = new BandLayoutGridPanel();

    private boolean suppressBandSelection;

    public BandsPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerList.setCellRenderer(namedPlayerRenderer());
        bandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bandList.setCellRenderer(namedBandRenderer());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Bands", buildBandsTab());
        tabs.addTab("Players", buildPlayersTab());
        add(tabs, BorderLayout.CENTER);

        bandList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressBandSelection) {
                loadSelectedBand();
            }
        });
    }

    public void setPlayerRepository(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        layoutGrid.setPlayerRepository(playerRepository);
    }

    public void setBandRepository(BandRepository bandRepository) {
        this.bandRepository = bandRepository;
        layoutGrid.setBandRepository(bandRepository);
    }

    public void bind(PlayerRepository players, BandRepository bands) {
        setPlayerRepository(players);
        setBandRepository(bands);
        reload();
    }

    public void reload() {
        reloadPlayers();
        reloadBands();
    }

    private JPanel buildPlayersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton neu = new JButton("New");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        neu.addActionListener(e -> editPlayer(null));
        edit.addActionListener(e -> {
            PlayerInfo selected = playerList.getSelectedValue();
            if (selected != null) {
                editPlayer(selected);
            }
        });
        delete.addActionListener(e -> deleteSelectedPlayer());
        toolbar.add(neu);
        toolbar.add(edit);
        toolbar.add(delete);

        playerList.setVisibleRowCount(16);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(playerList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBandsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel left = new JPanel(new BorderLayout(4, 4));
        JPanel bandToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton add = new JButton("Add");
        JButton duplicate = new JButton("Duplicate");
        JButton delete = new JButton("Delete");
        add.addActionListener(e -> addBand());
        duplicate.addActionListener(e -> duplicateBand());
        delete.addActionListener(e -> deleteBand());
        bandToolbar.add(add);
        bandToolbar.add(duplicate);
        bandToolbar.add(delete);
        left.add(bandToolbar, BorderLayout.NORTH);
        left.add(new JScrollPane(bandList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(220, 400));

        JPanel editor = new JPanel(new BorderLayout(8, 8));
        editor.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        JPanel meta = new JPanel(new BorderLayout(8, 4));
        JPanel metaTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton saveBand = new JButton("Save");
        saveBand.addActionListener(e -> saveSelectedBand());
        metaTop.add(saveBand);
        metaTop.add(new JLabel("Name"));
        bandNameField.setColumns(18);
        metaTop.add(bandNameField);

        JPanel notesPanel = new JPanel(new BorderLayout(4, 0));
        notesPanel.add(new JLabel("Notes"), BorderLayout.WEST);
        bandNotesArea.setLineWrap(true);
        bandNotesArea.setWrapStyleWord(true);
        notesPanel.add(new JScrollPane(bandNotesArea), BorderLayout.CENTER);
        notesPanel.setPreferredSize(new Dimension(280, 72));

        meta.add(metaTop, BorderLayout.WEST);
        meta.add(notesPanel, BorderLayout.CENTER);

        editor.add(meta, BorderLayout.NORTH);
        editor.add(layoutGrid, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editor);
        split.setResizeWeight(0.25);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void reloadPlayers() {
        Long selectedId = playerList.getSelectedValue() == null ? null : playerList.getSelectedValue().id();
        playerListModel.clear();
        if (playerRepository == null) {
            return;
        }
        try {
            List<PlayerInfo> players = playerRepository.listPlayers();
            for (PlayerInfo player : players) {
                playerListModel.addElement(player);
            }
            if (selectedId != null) {
                for (int i = 0; i < playerListModel.size(); i++) {
                    if (playerListModel.get(i).id() == selectedId) {
                        playerList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void reloadBands() {
        Long selectedId = bandList.getSelectedValue() == null ? null : bandList.getSelectedValue().id();
        suppressBandSelection = true;
        bandListModel.clear();
        try {
            if (bandRepository != null) {
                for (BandInfo band : bandRepository.listBands()) {
                    bandListModel.addElement(band);
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        } finally {
            suppressBandSelection = false;
        }
        if (selectedId != null) {
            for (int i = 0; i < bandListModel.size(); i++) {
                if (bandListModel.get(i).id() == selectedId) {
                    bandList.setSelectedIndex(i);
                    loadSelectedBand();
                    return;
                }
            }
        }
        clearBandEditor();
    }

    private void editPlayer(PlayerInfo existing) {
        if (playerRepository == null) {
            showError("Player repository is not connected.");
            return;
        }
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                existing == null ? "New player" : "Edit player",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField nameField = new JTextField(existing == null ? "" : nullToEmpty(existing.name()), 20);
        JSpinner levelSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null || existing.level() == null ? 1 : existing.level(),
                1, 200, 1));
        JComboBox<String> classCombo = new JComboBox<>();
        classCombo.addItem("");
        for (String characterClass : LotroInstrumentDefaults.CHARACTER_CLASSES) {
            classCombo.addItem(characterClass);
        }
        classCombo.setEditable(false);
        if (existing != null && existing.characterClass() != null && !existing.characterClass().isBlank()) {
            String current = existing.characterClass().strip();
            boolean found = false;
            for (int i = 0; i < classCombo.getItemCount(); i++) {
                if (current.equalsIgnoreCase(classCombo.getItemAt(i))) {
                    classCombo.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                classCombo.addItem(current);
                classCombo.setSelectedItem(current);
            }
        }

        DefaultTableModel instrumentModel = new DefaultTableModel(
                new Object[] {"Instrument", "Has instrument", "Has proficiency"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? String.class : Boolean.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        JTable instrumentTable = new JTable(instrumentModel);
        instrumentTable.setRowHeight(22);
        instrumentTable.getColumnModel().getColumn(0).setCellRenderer(new InstrumentNameRenderer());
        final List<InstrumentInfo> instruments;
        Map<Long, PlayerInstrumentInfo> byInstrument = new HashMap<>();
        try {
            instruments = playerRepository.listInstruments();
            if (existing != null) {
                for (PlayerInstrumentInfo info : playerRepository.listPlayerInstruments(existing.id())) {
                    byInstrument.put(info.instrumentId(), info);
                }
            }
            for (InstrumentInfo instrument : instruments) {
                PlayerInstrumentInfo current = byInstrument.get(instrument.id());
                boolean hasInstrument;
                boolean hasProficiency;
                if (existing == null) {
                    String characterClass = (String) classCombo.getSelectedItem();
                    hasInstrument = LotroInstrumentDefaults.defaultHasInstrument(
                            characterClass, instrument.name());
                    hasProficiency = LotroInstrumentDefaults.defaultHasProficiency(
                            characterClass, instrument.name());
                } else if (current != null) {
                    hasInstrument = current.hasInstrument();
                    hasProficiency = current.hasProficiency();
                } else {
                    hasInstrument = false;
                    hasProficiency = false;
                }
                instrumentModel.addRow(new Object[] {
                        instrument.name(),
                        hasInstrument,
                        hasProficiency
                });
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return;
        }

        Runnable applyClassDefaults = () -> {
            String characterClass = (String) classCombo.getSelectedItem();
            for (int row = 0; row < instruments.size(); row++) {
                InstrumentInfo instrument = instruments.get(row);
                instrumentModel.setValueAt(
                        LotroInstrumentDefaults.defaultHasInstrument(characterClass, instrument.name()),
                        row,
                        1);
                instrumentModel.setValueAt(
                        LotroInstrumentDefaults.defaultHasProficiency(characterClass, instrument.name()),
                        row,
                        2);
            }
        };
        classCombo.addActionListener(e -> applyClassDefaults.run());
        if (existing == null) {
            applyClassDefaults.run();
        }

        JButton selectAll = new JButton("Select / Deselect all");
        final boolean[] selectAllState = {true};
        selectAll.addActionListener(e -> {
            boolean value = selectAllState[0];
            for (int row = 0; row < instrumentModel.getRowCount(); row++) {
                instrumentModel.setValueAt(value, row, 1);
                instrumentModel.setValueAt(value, row, 2);
            }
            selectAllState[0] = !value;
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.gridy = 0;
        form.add(new JLabel("Name"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(nameField, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Level"), gc);
        gc.gridx = 1;
        form.add(levelSpinner, gc);
        gc.gridx = 0;
        gc.gridy = 2;
        form.add(new JLabel("Class"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(classCombo, gc);

        JPanel instrumentHeader = new JPanel(new BorderLayout());
        instrumentHeader.add(new JLabel("Instruments / proficiencies"), BorderLayout.WEST);
        instrumentHeader.add(selectAll, BorderLayout.EAST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Name is required.", "Player", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Integer level = ((Number) levelSpinner.getValue()).intValue();
            String characterClass = classCombo.getSelectedItem() == null
                    ? ""
                    : classCombo.getSelectedItem().toString().trim();
            try {
                long playerId;
                if (existing == null) {
                    playerId = playerRepository.addPlayer(name, level, characterClass);
                } else {
                    playerId = existing.id();
                    playerRepository.updatePlayer(playerId, name, level, characterClass);
                }
                for (int row = 0; row < instruments.size(); row++) {
                    InstrumentInfo instrument = instruments.get(row);
                    boolean hasInstrument = Boolean.TRUE.equals(instrumentModel.getValueAt(row, 1));
                    boolean hasProficiency = Boolean.TRUE.equals(instrumentModel.getValueAt(row, 2));
                    PlayerInstrumentInfo prior = byInstrument.get(instrument.id());
                    String notes = prior == null ? null : prior.notes();
                    if (hasInstrument || hasProficiency || prior != null) {
                        playerRepository.setPlayerInstrument(
                                playerId, instrument.id(), hasInstrument, hasProficiency, notes);
                    }
                }
                dialog.dispose();
                reloadPlayers();
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Player", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(instrumentHeader, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(instrumentTable);
        tableScroll.setPreferredSize(new Dimension(460, 300));
        center.add(tableScroll, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void deleteSelectedPlayer() {
        PlayerInfo selected = playerList.getSelectedValue();
        if (selected == null || playerRepository == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete player \"" + selected.name() + "\"?",
                "Delete player",
                JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            playerRepository.deletePlayer(selected.id());
            reloadPlayers();
            BandInfo band = bandList.getSelectedValue();
            if (band != null) {
                loadSelectedBand();
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void addBand() {
        if (bandRepository == null) {
            showError("Band repository is not connected.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Band name:", "Add band", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            long id = bandRepository.addBand(name.trim(), "");
            reloadBands();
            selectBandById(id);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void duplicateBand() {
        BandInfo selected = bandList.getSelectedValue();
        if (selected == null || bandRepository == null) {
            return;
        }
        try {
            long id = bandRepository.duplicateBand(selected.id());
            reloadBands();
            selectBandById(id);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteBand() {
        BandInfo selected = bandList.getSelectedValue();
        if (selected == null || bandRepository == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete band \"" + selected.name() + "\"?",
                "Delete band",
                JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            bandRepository.deleteBand(selected.id());
            reloadBands();
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadSelectedBand() {
        BandInfo selected = bandList.getSelectedValue();
        if (selected == null || bandRepository == null) {
            clearBandEditor();
            return;
        }
        bandNameField.setText(nullToEmpty(selected.name()));
        bandNotesArea.setText(nullToEmpty(selected.notes()));
        try {
            BandLayoutInfo layout = bandRepository.getOrCreatePrimaryLayout(selected.id());
            layoutGrid.setLayoutId(layout.id(), selected.id());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            layoutGrid.setLayoutId(null, selected.id());
        }
    }

    private void saveSelectedBand() {
        BandInfo selected = bandList.getSelectedValue();
        if (selected == null || bandRepository == null) {
            return;
        }
        String name = bandNameField.getText() == null ? "" : bandNameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Band", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String notes = bandNotesArea.getText();
        try {
            bandRepository.updateBand(selected.id(), name, notes);
            reloadBands();
            selectBandById(selected.id());
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void selectBandById(long id) {
        for (int i = 0; i < bandListModel.size(); i++) {
            if (bandListModel.get(i).id() == id) {
                bandList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void clearBandEditor() {
        bandNameField.setText("");
        bandNotesArea.setText("");
        layoutGrid.setLayoutId(null, null);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message == null || message.isBlank() ? "Operation failed." : message,
                "Bands",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static DefaultListCellRenderer namedPlayerRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PlayerInfo player) {
                    setText(player.name());
                }
                return this;
            }
        };
    }

    private static DefaultListCellRenderer namedBandRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BandInfo band) {
                    setText(band.name());
                }
                return this;
            }
        };
    }

    private static final class InstrumentNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String name = value == null ? "" : value.toString();
            String escaped = escapeHtml(name);
            if (LotroInstrumentDefaults.isFestivalInstrument(name)) {
                setText("<html><font color=\"#9B59B6\">●</font> " + escaped + "</html>");
            } else if (LotroInstrumentDefaults.isCofferInstrument(name)) {
                setText("<html><font color=\"#D4AF37\">●</font> " + escaped + "</html>");
            } else {
                setText(name);
            }
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }

        private static String escapeHtml(String value) {
            return value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
