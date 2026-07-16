package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerFilter;
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

    private final PlayersTableModel playersTableModel = new PlayersTableModel();
    private final JTable playersTable = new JTable(playersTableModel);

    private final DefaultListModel<BandInfo> bandListModel = new DefaultListModel<>();
    private final JList<BandInfo> bandList = new JList<>(bandListModel);

    private final JTextField bandNameField = new JTextField(20);
    private final JTextArea bandNotesArea = new JTextArea(3, 28);
    private final BandLayoutGridPanel layoutGrid = new BandLayoutGridPanel();

    private final JTextField playerNameFilter = new JTextField(12);
    private final JSpinner playerLevelMin = new JSpinner(new SpinnerNumberModel(0, 0, 250, 1));
    private final JSpinner playerLevelMax = new JSpinner(new SpinnerNumberModel(0, 0, 250, 1));
    private final JTextField playerClassFilter = new JTextField(10);
    private final JComboBox<InstrumentFilterItem> playerInstrumentFilter = new JComboBox<>();
    private boolean suppressPlayerFilterEvents;

    private boolean suppressBandSelection;
    private String loadedBandName = "";
    private String loadedBandNotes = "";
    private Long loadedBandId;

    public BandsPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        configurePlayersTable();
        bandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bandList.setCellRenderer(namedBandRenderer());
        enableBandListReorder();
        layoutGrid.setEditPlayerHandler(this::editPlayerFromLayoutCard);
        playerInstrumentFilter.addItem(InstrumentFilterItem.ALL);

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

    /**
     * True when the selected band's name or notes differ from the last loaded/saved values.
     * Layout card edits are written immediately and are not considered unsaved.
     */
    public boolean hasUnsavedChanges() {
        BandInfo selected = bandList.getSelectedValue();
        if (selected == null || loadedBandId == null || selected.id() != loadedBandId) {
            return false;
        }
        String name = bandNameField.getText() == null ? "" : bandNameField.getText().strip();
        String notes = bandNotesArea.getText() == null ? "" : bandNotesArea.getText().strip();
        return !name.equals(loadedBandName) || !notes.equals(loadedBandNotes);
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

    private void configurePlayersTable() {
        playersTable.setFillsViewportHeight(true);
        playersTable.setAutoCreateRowSorter(false);
        playersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playersTable.setRowHeight(24);
        playersTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        playersTable.getTableHeader().setReorderingAllowed(false);
        playersTable.setDefaultRenderer(Boolean.class, new ReadOnlyCheckRenderer());
        playersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    PlayerInfo selected = selectedPlayer();
                    if (selected != null) {
                        editPlayer(selected);
                    }
                }
            }
        });
    }

    private JPanel buildPlayersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        playerNameFilter.putClientProperty("JTextField.placeholderText", "Filter by name");
        playerClassFilter.putClientProperty("JTextField.placeholderText", "Filter by class");
        filters.add(playerNameFilter);
        filters.add(new JLabel("Level:"));
        filters.add(playerLevelMin);
        filters.add(new JLabel("to"));
        filters.add(playerLevelMax);
        filters.add(new JLabel("Class:"));
        filters.add(playerClassFilter);
        filters.add(new JLabel("Instrument:"));
        filters.add(playerInstrumentFilter);
        JButton resetFilters = new JButton("Reset Filters");
        resetFilters.addActionListener(e -> resetPlayerFilters());
        filters.add(resetFilters);

        DocumentListener filterListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyPlayerFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyPlayerFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyPlayerFilters();
            }
        };
        playerNameFilter.getDocument().addDocumentListener(filterListener);
        playerClassFilter.getDocument().addDocumentListener(filterListener);
        playerLevelMin.addChangeListener(e -> applyPlayerFilters());
        playerLevelMax.addChangeListener(e -> applyPlayerFilters());
        playerInstrumentFilter.addActionListener(e -> applyPlayerFilters());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton neu = new JButton("New");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        neu.addActionListener(e -> editPlayer(null));
        edit.addActionListener(e -> {
            PlayerInfo selected = selectedPlayer();
            if (selected != null) {
                editPlayer(selected);
            }
        });
        delete.addActionListener(e -> deleteSelectedPlayer());
        toolbar.add(neu);
        toolbar.add(edit);
        toolbar.add(delete);

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(filters, BorderLayout.NORTH);
        north.add(toolbar, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(playersTable), BorderLayout.CENTER);
        return panel;
    }

    private PlayerInfo selectedPlayer() {
        int viewRow = playersTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = playersTable.convertRowIndexToModel(viewRow);
        return playersTableModel.playerAt(modelRow);
    }

    private void configurePlayersTableColumns() {
        int instrumentCount = playersTableModel.instrumentColumnCount();
        if (instrumentCount <= 0) {
            return;
        }
        int firstInst = playersTableModel.firstInstrumentColumn();
        int lastInst = playersTableModel.lastInstrumentColumn();
        DiagonalTableHeader header = DiagonalTableHeader.install(playersTable, firstInst, lastInst);
        Dimension headerSize = header.getPreferredSize();
        header.setPreferredSize(headerSize);

        TableColumn nameCol = playersTable.getColumnModel().getColumn(PlayersTableModel.COL_NAME);
        nameCol.setPreferredWidth(140);
        TableColumn levelCol = playersTable.getColumnModel().getColumn(PlayersTableModel.COL_LEVEL);
        levelCol.setPreferredWidth(56);
        TableColumn classCol = playersTable.getColumnModel().getColumn(PlayersTableModel.COL_CLASS);
        classCol.setPreferredWidth(100);

        for (int col = firstInst; col <= lastInst; col++) {
            TableColumn instrumentCol = playersTable.getColumnModel().getColumn(col);
            instrumentCol.setMinWidth(26);
            instrumentCol.setMaxWidth(32);
            instrumentCol.setPreferredWidth(28);
            instrumentCol.setResizable(false);
        }
        playersTable.getTableHeader().revalidate();
        playersTable.getTableHeader().repaint();
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
        JPanel listHost = new JPanel(new BorderLayout(0, 2));
        listHost.add(new JLabel("Bands (drag to reorder)"), BorderLayout.NORTH);
        listHost.add(new JScrollPane(bandList), BorderLayout.CENTER);
        left.add(listHost, BorderLayout.CENTER);
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
        Long selectedId = selectedPlayer() == null ? null : selectedPlayer().id();
        if (playerRepository == null) {
            playersTableModel.setInstruments(List.of());
            playersTableModel.setRows(List.of());
            refreshInstrumentFilterItems(List.of());
            return;
        }
        try {
            List<InstrumentInfo> instruments = playerRepository.listInstruments();
            playersTableModel.setInstruments(instruments);
            configurePlayersTableColumns();
            refreshInstrumentFilterItems(instruments);

            List<PlayerInfo> players = playerRepository.listPlayers(currentPlayerFilter());
            List<PlayersTableModel.Row> rows = new ArrayList<>(players.size());
            for (PlayerInfo player : players) {
                Set<Long> owned = new HashSet<>();
                for (PlayerInstrumentInfo info : playerRepository.listPlayerInstruments(player.id())) {
                    if (info.hasInstrument()) {
                        owned.add(info.instrumentId());
                    }
                }
                rows.add(PlayersTableModel.Row.of(player, owned));
            }
            playersTableModel.setRows(rows);

            if (selectedId != null) {
                for (int i = 0; i < playersTableModel.getRowCount(); i++) {
                    PlayerInfo rowPlayer = playersTableModel.playerAt(i);
                    if (rowPlayer != null && rowPlayer.id() == selectedId) {
                        int viewRow = playersTable.convertRowIndexToView(i);
                        playersTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                        break;
                    }
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void applyPlayerFilters() {
        if (suppressPlayerFilterEvents) {
            return;
        }
        reloadPlayers();
    }

    private void resetPlayerFilters() {
        suppressPlayerFilterEvents = true;
        try {
            playerNameFilter.setText("");
            playerClassFilter.setText("");
            playerLevelMin.setValue(0);
            playerLevelMax.setValue(0);
            playerInstrumentFilter.setSelectedIndex(0);
        } finally {
            suppressPlayerFilterEvents = false;
        }
        reloadPlayers();
    }

    private PlayerFilter currentPlayerFilter() {
        String name = playerNameFilter.getText() == null ? "" : playerNameFilter.getText().strip();
        String characterClass = playerClassFilter.getText() == null
                ? ""
                : playerClassFilter.getText().strip();
        int levelMin = ((Number) playerLevelMin.getValue()).intValue();
        int levelMax = ((Number) playerLevelMax.getValue()).intValue();
        InstrumentFilterItem instrument = (InstrumentFilterItem) playerInstrumentFilter.getSelectedItem();
        List<Long> instrumentIds = null;
        if (instrument != null && instrument.instrumentId() != null) {
            instrumentIds = List.of(instrument.instrumentId());
        }
        return new PlayerFilter(
                name.isEmpty() ? null : name,
                levelMin > 0 ? levelMin : null,
                levelMax > 0 ? levelMax : null,
                characterClass.isEmpty() ? null : characterClass,
                instrumentIds);
    }

    private void refreshInstrumentFilterItems(List<InstrumentInfo> instruments) {
        suppressPlayerFilterEvents = true;
        try {
            Long selectedId = null;
            Object current = playerInstrumentFilter.getSelectedItem();
            if (current instanceof InstrumentFilterItem item) {
                selectedId = item.instrumentId();
            }
            playerInstrumentFilter.removeAllItems();
            playerInstrumentFilter.addItem(InstrumentFilterItem.ALL);
            for (InstrumentInfo instrument : instruments) {
                playerInstrumentFilter.addItem(new InstrumentFilterItem(
                        instrument.id(),
                        LotroInstrumentDefaults.uiName(instrument.name())));
            }
            int selectIndex = 0;
            if (selectedId != null) {
                for (int i = 0; i < playerInstrumentFilter.getItemCount(); i++) {
                    InstrumentFilterItem item = playerInstrumentFilter.getItemAt(i);
                    if (Objects.equals(item.instrumentId(), selectedId)) {
                        selectIndex = i;
                        break;
                    }
                }
            }
            playerInstrumentFilter.setSelectedIndex(selectIndex);
        } finally {
            suppressPlayerFilterEvents = false;
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

    private void editPlayerFromLayoutCard(long playerId) {
        if (playerRepository == null) {
            showError("Player repository is not connected.");
            return;
        }
        try {
            PlayerInfo player = playerRepository.getPlayer(playerId);
            if (player == null) {
                showError("Player not found.");
                return;
            }
            editPlayer(player);
            layoutGrid.reload();
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
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

        final Map<String, InstrumentInfo> instrumentsByName;
        Map<Long, PlayerInstrumentInfo> byInstrument = new HashMap<>();
        try {
            instrumentsByName = new LinkedHashMap<>();
            for (InstrumentInfo instrument : playerRepository.listInstruments()) {
                instrumentsByName.put(normalizeInstrumentKey(instrument.name()), instrument);
            }
            if (existing != null) {
                for (PlayerInstrumentInfo info : playerRepository.listPlayerInstruments(existing.id())) {
                    byInstrument.put(info.instrumentId(), info);
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return;
        }

        Map<String, JCheckBox> instrumentChecks = new LinkedHashMap<>();
        JPanel instrumentsPanel = buildGroupedInstrumentsPanel(instrumentsByName, instrumentChecks);

        Runnable applyClassDefaults = () -> {
            String characterClass = (String) classCombo.getSelectedItem();
            for (Map.Entry<String, JCheckBox> entry : instrumentChecks.entrySet()) {
                InstrumentInfo instrument = instrumentsByName.get(normalizeInstrumentKey(entry.getKey()));
                if (instrument == null) {
                    continue;
                }
                entry.getValue().setSelected(
                        LotroInstrumentDefaults.defaultHasInstrument(characterClass, instrument.name()));
            }
        };
        classCombo.addActionListener(e -> applyClassDefaults.run());

        if (existing == null) {
            applyClassDefaults.run();
        } else {
            for (Map.Entry<String, JCheckBox> entry : instrumentChecks.entrySet()) {
                InstrumentInfo instrument = instrumentsByName.get(normalizeInstrumentKey(entry.getKey()));
                if (instrument == null) {
                    continue;
                }
                PlayerInstrumentInfo current = byInstrument.get(instrument.id());
                entry.getValue().setSelected(current != null && current.hasInstrument());
            }
        }

        JButton selectAll = new JButton("Select / Deselect all");
        final boolean[] selectAllState = {true};
        selectAll.addActionListener(e -> {
            boolean value = selectAllState[0];
            for (JCheckBox check : instrumentChecks.values()) {
                check.setSelected(value);
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
        instrumentHeader.add(new JLabel("Instruments"), BorderLayout.WEST);
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
                for (Map.Entry<String, JCheckBox> entry : instrumentChecks.entrySet()) {
                    InstrumentInfo instrument = instrumentsByName.get(normalizeInstrumentKey(entry.getKey()));
                    if (instrument == null) {
                        continue;
                    }
                    boolean hasInstrument = entry.getValue().isSelected();
                    // has_proficiency is unused (Python always writes false); flagged for schema drop.
                    PlayerInstrumentInfo prior = byInstrument.get(instrument.id());
                    String notes = prior == null ? null : prior.notes();
                    if (hasInstrument || prior != null) {
                        playerRepository.setPlayerInstrument(
                                playerId, instrument.id(), hasInstrument, false, notes);
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
        JScrollPane instrumentsScroll = new JScrollPane(instrumentsPanel);
        instrumentsScroll.setPreferredSize(new Dimension(720, 320));
        instrumentsScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(instrumentsScroll, BorderLayout.CENTER);

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
        PlayerInfo selected = selectedPlayer();
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
        String name = nullToEmpty(selected.name());
        String notes = nullToEmpty(selected.notes()).strip();
        bandNameField.setText(name);
        bandNotesArea.setText(nullToEmpty(selected.notes()));
        loadedBandId = selected.id();
        loadedBandName = name.strip();
        loadedBandNotes = notes;
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
            JOptionPane.showMessageDialog(
                    this,
                    "Band name cannot be empty.",
                    "Save",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (layoutGrid.hasAnyOverlap()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Some cards overlap. Save is allowed, but consider rearranging.",
                    "Layout overlap",
                    JOptionPane.WARNING_MESSAGE);
        }
        String notes = bandNotesArea.getText();
        try {
            bandRepository.updateBand(selected.id(), name, notes);
            loadedBandId = selected.id();
            loadedBandName = name.strip();
            loadedBandNotes = notes == null ? "" : notes.strip();
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
        loadedBandId = null;
        loadedBandName = "";
        loadedBandNotes = "";
        layoutGrid.setLayoutId(null, null);
    }

    private void enableBandListReorder() {
        bandList.setDragEnabled(true);
        bandList.setDropMode(DropMode.INSERT);
        bandList.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                fromIndex = bandList.getSelectedIndex();
                return new StringSelection(Integer.toString(fromIndex));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        && fromIndex >= 0;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support) || bandRepository == null) {
                    return false;
                }
                JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
                int toIndex = dropLocation.getIndex();
                if (toIndex < 0) {
                    toIndex = bandListModel.getSize();
                }
                if (fromIndex < 0 || fromIndex >= bandListModel.getSize()) {
                    return false;
                }
                suppressBandSelection = true;
                try {
                    BandInfo moved = bandListModel.remove(fromIndex);
                    if (toIndex > fromIndex) {
                        toIndex--;
                    }
                    toIndex = Math.max(0, Math.min(toIndex, bandListModel.getSize()));
                    bandListModel.add(toIndex, moved);
                    bandList.setSelectedIndex(toIndex);
                } finally {
                    suppressBandSelection = false;
                }
                persistBandOrder();
                fromIndex = -1;
                return true;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                fromIndex = -1;
            }
        });
    }

    private void persistBandOrder() {
        if (bandRepository == null) {
            return;
        }
        List<Long> order = new ArrayList<>(bandListModel.size());
        for (int i = 0; i < bandListModel.size(); i++) {
            order.add(bandListModel.get(i).id());
        }
        try {
            bandRepository.reorderBands(order);
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            reloadBands();
        }
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

    private record InstrumentFilterItem(Long instrumentId, String label) {
        static final InstrumentFilterItem ALL = new InstrumentFilterItem(null, "All");

        @Override
        public String toString() {
            return label;
        }
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

    /**
     * Read-only instrument possession indicator (matches Python players-table checkmarks).
     */
    private static final class ReadOnlyCheckRenderer extends DefaultTableCellRenderer {
        private final JCheckBox check = new JCheckBox();

        ReadOnlyCheckRenderer() {
            check.setHorizontalAlignment(SwingConstants.CENTER);
            check.setBorderPainted(false);
            check.setOpaque(true);
            // Keep the normal checkbox look but disable interaction cues.
            check.setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            check.setSelected(Boolean.TRUE.equals(value));
            if (isSelected) {
                check.setBackground(table.getSelectionBackground());
                check.setForeground(table.getSelectionForeground());
            } else {
                check.setBackground(table.getBackground());
                check.setForeground(table.getForeground());
            }
            return check;
        }
    }

    /**
     * Same 4-column instrument layout as Python {@code player_dialog.INSTRUMENT_COLUMNS}.
     */
    private static final List<List<InstrumentSection>> INSTRUMENT_COLUMNS = List.of(
            List.of(new InstrumentSection(
                    "Fiddles",
                    List.of(
                            "Basic Fiddle",
                            "Student's Fiddle",
                            "Bardic Fiddle",
                            "Lonely Mountain Fiddle",
                            "Sprightly Fiddle",
                            "Traveler's Trusty Fiddle"))),
            List.of(
                    new InstrumentSection(
                            "Bassoons",
                            List.of("Basic Bassoon", "Lonely Mountain Bassoon", "Brusque Bassoon")),
                    new InstrumentSection(
                            null,
                            List.of(
                                    "Basic Flute",
                                    "Basic Horn",
                                    "Basic Clarinet",
                                    "Basic Bagpipe",
                                    "Basic Pibgorn"))),
            List.of(
                    new InstrumentSection("Harps", List.of("Basic Harp", "Misty Mountain Harp")),
                    new InstrumentSection("Lutes", List.of("Basic Lute", "Lute of Ages")),
                    new InstrumentSection(null, List.of("Basic Theorbo"))),
            List.of(new InstrumentSection(
                    null,
                    List.of("Basic Drum", "Basic Cowbell", "Moor Cowbell", "Jaunty Hand-Knells"))));

    private record InstrumentSection(String groupName, List<String> names) {
    }

    private static JPanel buildGroupedInstrumentsPanel(
            Map<String, InstrumentInfo> instrumentsByName, Map<String, JCheckBox> outChecks) {
        JPanel row = new JPanel(new GridLayout(1, INSTRUMENT_COLUMNS.size(), 8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        for (List<InstrumentSection> columnSections : INSTRUMENT_COLUMNS) {
            JPanel column = new JPanel();
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            column.setAlignmentY(Component.TOP_ALIGNMENT);
            for (InstrumentSection section : columnSections) {
                JPanel host = section.groupName() == null
                        ? column
                        : titledGroup(section.groupName());
                for (String catalogName : section.names()) {
                    InstrumentInfo info = instrumentsByName.get(normalizeInstrumentKey(catalogName));
                    if (info == null) {
                        continue;
                    }
                    JCheckBox check = new JCheckBox(instrumentCheckboxLabel(info.name()));
                    check.setAlignmentX(Component.LEFT_ALIGNMENT);
                    check.setHorizontalAlignment(SwingConstants.LEFT);
                    outChecks.put(info.name(), check);
                    host.add(check);
                }
                if (section.groupName() != null) {
                    host.setAlignmentX(Component.LEFT_ALIGNMENT);
                    column.add(host);
                    column.add(Box.createVerticalStrut(4));
                }
            }
            column.add(Box.createVerticalGlue());
            row.add(column);
        }
        return row;
    }

    private static JPanel titledGroup(String title) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBorder(BorderFactory.createTitledBorder(title));
        return group;
    }

    private static String instrumentCheckboxLabel(String instrumentName) {
        String label = LotroInstrumentDefaults.uiName(instrumentName);
        String escaped = escapeHtml(label);
        if (LotroInstrumentDefaults.isFestivalInstrument(instrumentName)) {
            return "<html><font color=\"#9B59B6\">●</font> " + escaped + "</html>";
        }
        if (LotroInstrumentDefaults.isCofferInstrument(instrumentName)) {
            return "<html><font color=\"#D4AF37\">●</font> " + escaped + "</html>";
        }
        return label;
    }

    private static String normalizeInstrumentKey(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
