package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SettingsRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.prefs.AppearanceOptions;
import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;

/**
 * Settings dialog with all Python edition tabs. Preferences persist on Save;
 * Status / Folder rule / Account target CRUD uses a writable {@link SettingsRepository}.
 */
public final class SettingsDialog extends JDialog {

    private final Preferences working;
    private final SettingsRepository settingsRepository;
    private final Runnable onResetWindowGeometry;
    private final Consumer<Preferences> onSaved;
    private final Runnable onEntitiesChanged;

    private final JComboBox<String> themeCombo = new JComboBox<>(AppearanceOptions.THEMES);
    private final JComboBox<String> fontSizeCombo = new JComboBox<>();
    private final JTextField lotroRootField = new JTextField(36);
    private final JTextField setExportDirField = new JTextField(36);
    private final JComboBox<StatusInfo> defaultStatusCombo = new JComboBox<>();
    private final DefaultFiltersPanel defaultFiltersPanel = new DefaultFiltersPanel();
    private final JTextField soundfontField = new JTextField(28);
    private final JSpinner volumeSpinner = new JSpinner(new SpinnerNumberModel(100.0, 0.0, 100.0, 1.0));
    private final JSpinner tempoSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 2.0, 0.05));
    private final JComboBox<String> stereoModeCombo =
            new JComboBox<>(new String[] {"maestro", "maestro_user_pan", "band_layout"});
    private final JSpinner stereoSlider = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private boolean saved;
    private boolean entitiesChanged;

    private List<StatusInfo> statuses;
    private List<FolderRuleInfo> folderRules;
    private List<AccountTargetInfo> accountTargets;

    private DefaultTableModel folderRulesModel;
    private DefaultTableModel statusesModel;
    private DefaultTableModel accountTargetsModel;
    private JTable folderRulesTable;
    private JTable statusesTable;
    private JTable accountTargetsTable;

    public SettingsDialog(
            JFrame owner,
            Preferences preferences,
            List<StatusInfo> statuses,
            List<FolderRuleInfo> folderRules,
            List<AccountTargetInfo> accountTargets,
            Runnable onResetWindowGeometry,
            Consumer<Preferences> onSaved) {
        this(owner, preferences, null, statuses, folderRules, accountTargets,
                onResetWindowGeometry, onSaved, null);
    }

    public SettingsDialog(
            JFrame owner,
            Preferences preferences,
            SettingsRepository settingsRepository,
            List<StatusInfo> statuses,
            List<FolderRuleInfo> folderRules,
            List<AccountTargetInfo> accountTargets,
            Runnable onResetWindowGeometry,
            Consumer<Preferences> onSaved,
            Runnable onEntitiesChanged) {
        super(owner, "Settings", true);
        this.working = Objects.requireNonNull(preferences, "preferences").copy();
        this.settingsRepository = settingsRepository;
        this.statuses = statuses == null ? List.of() : new ArrayList<>(statuses);
        this.folderRules = folderRules == null ? List.of() : new ArrayList<>(folderRules);
        this.accountTargets = accountTargets == null ? List.of() : new ArrayList<>(accountTargets);
        this.onResetWindowGeometry = Objects.requireNonNullElse(onResetWindowGeometry, () -> {
        });
        this.onSaved = Objects.requireNonNullElse(onSaved, prefs -> {
        });
        this.onEntitiesChanged = Objects.requireNonNullElse(onEntitiesChanged, () -> {
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(720, 520));
        setPreferredSize(new Dimension(820, 600));

        LotroPaths.ensureDefaultLotroRoot(working);

        for (int size : AppearanceOptions.FONT_SIZES) {
            fontSizeCombo.addItem(Integer.toString(size));
        }
        SpinnerMouseWheel.install(volumeSpinner);
        SpinnerMouseWheel.install(tempoSpinner);
        SpinnerMouseWheel.install(stereoSlider);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appearance", buildAppearanceTab());
        tabs.addTab("Default filters", buildDefaultFiltersTab());
        tabs.addTab("Folder rules", buildFolderRulesTab());
        tabs.addTab("Statuses", buildStatusesTab());
        tabs.addTab("Account targets", buildAccountTargetsTab());
        tabs.addTab("ABC Playback", buildPlaybackTab());
        tabs.addTab("Set Playback", buildSetPlaybackTab());
        tabs.addChangeListener(e -> {
            if ("Folder rules".equals(tabs.getTitleAt(tabs.getSelectedIndex()))) {
                LotroPaths.ensureDefaultLotroRoot(working);
                if (lotroRootField.getText().isBlank() && !working.lotroRoot().isBlank()) {
                    lotroRootField.setText(working.lotroRoot());
                }
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        cancel.addActionListener(e -> {
            if (entitiesChanged) {
                onEntitiesChanged.run();
            }
            dispose();
        });
        save.addActionListener(e -> saveAndClose());
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(tabs, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        loadWorkingIntoControls();
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean wasSaved() {
        return saved;
    }

    private void loadWorkingIntoControls() {
        themeCombo.setSelectedItem(working.theme());
        fontSizeCombo.setSelectedItem(Integer.toString(working.baseFontSize()));
        lotroRootField.setText(working.lotroRoot());
        setExportDirField.setText(working.setExportDir());
        defaultFiltersPanel.setFilters(working.defaultFilters());
        soundfontField.setText(working.playbackSoundfontPath());
        volumeSpinner.setValue(working.playbackVolume());
        tempoSpinner.setValue(working.playbackTempo());
        stereoModeCombo.setSelectedItem(working.playbackStereoMode());
        stereoSlider.setValue(working.playbackStereoSlider());

        defaultStatusCombo.removeAllItems();
        defaultStatusCombo.addItem(null);
        for (StatusInfo status : statuses) {
            defaultStatusCombo.addItem(status);
        }
        defaultStatusCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "(none)" : value.name());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        Long defaultId = working.defaultStatusId();
        if (defaultId != null) {
            for (int i = 0; i < defaultStatusCombo.getItemCount(); i++) {
                StatusInfo item = defaultStatusCombo.getItemAt(i);
                if (item != null && item.id() == defaultId) {
                    defaultStatusCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else {
            defaultStatusCombo.setSelectedIndex(0);
        }
    }

    private void collectWorkingFromControls() {
        working.setTheme(String.valueOf(themeCombo.getSelectedItem()));
        working.setBaseFontSize(Integer.parseInt(String.valueOf(fontSizeCombo.getSelectedItem())));
        working.setLotroRoot(lotroRootField.getText());
        String setExport = setExportDirField.getText();
        if (setExport != null && !setExport.isBlank()) {
            setExport = LotroPaths.toMusicRelative(setExport, working.lotroRoot());
        }
        working.setSetExportDir(setExport);
        setExportDirField.setText(working.setExportDir());
        working.setDefaultFilters(defaultFiltersPanel.toFilters());
        StatusInfo selectedStatus = (StatusInfo) defaultStatusCombo.getSelectedItem();
        working.setDefaultStatusId(selectedStatus == null ? null : selectedStatus.id());
        working.setPlaybackSoundfontPath(soundfontField.getText());
        working.setPlaybackVolume((Double) volumeSpinner.getValue());
        working.setPlaybackTempo((Double) tempoSpinner.getValue());
        working.setPlaybackStereoMode(String.valueOf(stereoModeCombo.getSelectedItem()));
        working.setPlaybackStereoSlider((Integer) stereoSlider.getValue());
    }

    private void saveAndClose() {
        collectWorkingFromControls();
        onSaved.accept(working.copy());
        saved = true;
        if (entitiesChanged) {
            onEntitiesChanged.run();
        }
        dispose();
    }

    public boolean entitiesChanged() {
        return entitiesChanged;
    }

    private JPanel buildAppearanceTab() {
        JPanel panel = formPanel();
        GridBagConstraints c = formConstraints();

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Theme"), c);
        c.gridx = 1;
        themeCombo.setToolTipText("Flat Dark / Flat Light — same themes as Maestro and ABC Player.");
        panel.add(themeCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Font size"), c);
        c.gridx = 1;
        fontSizeCombo.setToolTipText("Font sizes match Maestro and ABC Player.");
        panel.add(fontSizeCombo, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        panel.add(new JLabel("Theme and font size apply when you save."), c);

        c.gridy = 3;
        JButton resetGeometry = new JButton("Reset window geometry");
        resetGeometry.addActionListener(e -> {
            working.clearWindowGeometry();
            onResetWindowGeometry.run();
            JOptionPane.showMessageDialog(this, "Window geometry will reset on next save/restart.");
        });
        panel.add(resetGeometry, c);

        c.gridy = 4;
        JButton resetAll = new JButton("Reset all preferences");
        resetAll.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Clear all preferences to defaults?",
                    "Reset preferences",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                Preferences blank = new Preferences();
                working.setTheme(blank.theme());
                working.setBaseFontSize(blank.baseFontSize());
                working.setDefaultStatusId(null);
                working.setLotroRoot("");
                working.setSetExportDir("");
                working.setDefaultFilters(DefaultFilters.builtins());
                working.clearWindowGeometry();
                working.setSplitterState(null);
                working.setPlaybackSoundfontPath("");
                working.setPlaybackVolume(100.0);
                working.setPlaybackTempo(1.0);
                working.setPlaybackStereoMode("maestro");
                working.setPlaybackStereoSlider(0);
                working.setSetPlayRelays(List.of());
                working.setSetPlaySelectedRelayId(null);
                working.extras().clear();
                loadWorkingIntoControls();
            }
        });
        panel.add(resetAll, c);
        return panel;
    }

    private JPanel buildDefaultFiltersTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Defaults applied when Library Reset Filters is clicked."), BorderLayout.NORTH);
        panel.add(defaultFiltersPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFolderRulesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel roots = formPanel();
        GridBagConstraints c = formConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        roots.add(new JLabel(
                "LOTRO root contains Music (library) and PluginData (songbook accounts). "
                        + "Detected under your Documents library when possible."), c);

        c.gridy = 1;
        c.gridwidth = 1;
        roots.add(new JLabel("LOTRO root"), c);
        c.gridx = 1;
        roots.add(lotroRootField, c);
        c.gridx = 2;
        JButton browseRoot = new JButton("Browse…");
        browseRoot.addActionListener(e -> browseLotroRoot());
        roots.add(browseRoot, c);

        c.gridx = 0;
        c.gridy = 2;
        roots.add(new JLabel("Set export dir"), c);
        c.gridx = 1;
        setExportDirField.setToolTipText(
                "Under the Music folder when possible; stored relative to Music.");
        roots.add(setExportDirField, c);
        c.gridx = 2;
        JPanel exportButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton browseExport = new JButton("Browse…");
        JButton clearExport = new JButton("Clear");
        browseExport.addActionListener(e -> browseSetExportDir());
        clearExport.addActionListener(e -> setExportDirField.setText(""));
        exportButtons.add(browseExport);
        exportButtons.add(clearExport);
        roots.add(exportButtons, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        roots.add(new JLabel("Excluded directories (relative to Music; skipped by library scan)"), c);

        folderRulesModel = new DefaultTableModel(
                new Object[] {"Path", "Enabled", "Include in export"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? String.class : Boolean.class;
            }
        };
        refillFolderRulesTable();
        folderRulesTable = new JTable(folderRulesModel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        add.addActionListener(e -> addFolderRule());
        edit.addActionListener(e -> editFolderRule());
        delete.addActionListener(e -> deleteFolderRule());
        boolean enabled = settingsRepository != null;
        add.setEnabled(enabled);
        edit.setEnabled(enabled);
        delete.setEnabled(enabled);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        if (!enabled) {
            buttons.add(new JLabel("Writable database required for folder-rule edits."));
        }

        panel.add(roots, BorderLayout.NORTH);
        panel.add(new JScrollPane(folderRulesTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Default status"));
        top.add(defaultStatusCombo);

        statusesModel = new DefaultTableModel(new Object[] {"Name", "Color", "Sort"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        refillStatusesTable();
        statusesTable = new JTable(statusesModel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        JButton moveUp = new JButton("Move up");
        JButton moveDown = new JButton("Move down");
        add.addActionListener(e -> addStatus());
        edit.addActionListener(e -> editStatus());
        delete.addActionListener(e -> deleteStatus());
        moveUp.addActionListener(e -> moveStatus(-1));
        moveDown.addActionListener(e -> moveStatus(1));
        boolean enabled = settingsRepository != null;
        add.setEnabled(enabled);
        edit.setEnabled(enabled);
        delete.setEnabled(enabled);
        moveUp.setEnabled(enabled);
        moveDown.setEnabled(enabled);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(moveUp);
        buttons.add(moveDown);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(statusesTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAccountTargetsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        accountTargetsModel = new DefaultTableModel(
                new Object[] {"Account", "PluginData path", "Enabled"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 2 ? Boolean.class : String.class;
            }
        };
        refillAccountTargetsTable();
        accountTargetsTable = new JTable(accountTargetsModel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scan = new JButton("Scan Account Targets");
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        scan.addActionListener(e -> scanAccountTargets());
        add.addActionListener(e -> addAccountTarget());
        edit.addActionListener(e -> editAccountTarget());
        delete.addActionListener(e -> deleteAccountTarget());
        boolean enabled = settingsRepository != null;
        scan.setEnabled(enabled);
        add.setEnabled(enabled);
        edit.setEnabled(enabled);
        delete.setEnabled(enabled);
        buttons.add(scan);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);

        panel.add(new JScrollPane(accountTargetsTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPlaybackTab() {
        JPanel panel = formPanel();
        GridBagConstraints c = formConstraints();
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Soundfont path"), c);
        c.gridx = 1;
        panel.add(soundfontField, c);
        c.gridx = 2;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (file != null) {
                    soundfontField.setText(file.getAbsolutePath());
                }
            }
        });
        panel.add(browse, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Volume (0–100)"), c);
        c.gridx = 1;
        panel.add(volumeSpinner, c);

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Tempo (0.5–2.0)"), c);
        c.gridx = 1;
        panel.add(tempoSpinner, c);

        c.gridx = 0;
        c.gridy = 3;
        panel.add(new JLabel("Stereo mode"), c);
        c.gridx = 1;
        panel.add(stereoModeCombo, c);

        c.gridx = 0;
        c.gridy = 4;
        panel.add(new JLabel("Stereo width (0–100)"), c);
        c.gridx = 1;
        panel.add(stereoSlider, c);

        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 3;
        panel.add(new JLabel("Saved to preferences; playback engine not wired yet."), c);
        return panel;
    }

    private JPanel buildSetPlaybackTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Set Play relays and deploy wizard are not available in this milestone."),
                BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Id", "Name", "URL"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (var relay : working.setPlayRelays()) {
            model.addRow(new Object[] {
                    String.valueOf(relay.getOrDefault("id", "")),
                    String.valueOf(relay.getOrDefault("name", "")),
                    String.valueOf(relay.getOrDefault("url", ""))
            });
        }
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        panel.add(stubBar("Add / edit relays / Deploy (not available yet)"), BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static GridBagConstraints formConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static JPanel stubBar(String message) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton delete = new JButton("Delete");
        add.setEnabled(false);
        edit.setEnabled(false);
        delete.setEnabled(false);
        add.setToolTipText(message);
        edit.setToolTipText(message);
        delete.setToolTipText(message);
        bar.add(add);
        bar.add(edit);
        bar.add(delete);
        bar.add(new JLabel(message));
        return bar;
    }

    private void refillFolderRulesTable() {
        folderRulesModel.setRowCount(0);
        for (FolderRuleInfo rule : folderRules) {
            folderRulesModel.addRow(new Object[] {rule.path(), rule.enabled(), rule.includeInExport()});
        }
    }

    private void refillStatusesTable() {
        statusesModel.setRowCount(0);
        for (StatusInfo status : statuses) {
            statusesModel.addRow(new Object[] {status.name(), status.color(), status.sortOrder()});
        }
        refreshDefaultStatusCombo();
    }

    private void refillAccountTargetsTable() {
        accountTargetsModel.setRowCount(0);
        for (AccountTargetInfo target : accountTargets) {
            accountTargetsModel.addRow(new Object[] {
                    target.accountName(), target.pluginDataPath(), target.enabled()});
        }
    }

    private void refreshDefaultStatusCombo() {
        Long selectedId = null;
        StatusInfo selected = (StatusInfo) defaultStatusCombo.getSelectedItem();
        if (selected != null) {
            selectedId = selected.id();
        } else if (working.defaultStatusId() != null) {
            selectedId = working.defaultStatusId();
        }
        defaultStatusCombo.removeAllItems();
        defaultStatusCombo.addItem(null);
        for (StatusInfo status : statuses) {
            defaultStatusCombo.addItem(status);
        }
        if (selectedId != null) {
            for (int i = 0; i < defaultStatusCombo.getItemCount(); i++) {
                StatusInfo item = defaultStatusCombo.getItemAt(i);
                if (item != null && item.id() == selectedId) {
                    defaultStatusCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        defaultStatusCombo.setSelectedIndex(0);
    }

    private void markEntitiesChanged() {
        entitiesChanged = true;
    }

    private void reloadSettingsEntities() throws LibraryException {
        statuses = new ArrayList<>(settingsRepository.listStatuses());
        folderRules = new ArrayList<>(settingsRepository.listFolderRules());
        accountTargets = new ArrayList<>(settingsRepository.listAccountTargets());
        refillStatusesTable();
        refillFolderRulesTable();
        refillAccountTargetsTable();
        markEntitiesChanged();
    }

    private void addFolderRule() {
        if (settingsRepository == null) {
            return;
        }
        FolderRuleEdit edit = promptFolderRule(null);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.addFolderRule(edit.path(), edit.enabled(), edit.includeInExport());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void editFolderRule() {
        int row = folderRulesTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        FolderRuleInfo current = folderRules.get(row);
        FolderRuleEdit edit = promptFolderRule(current);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.updateFolderRule(
                    current.id(), edit.path(), edit.enabled(), edit.includeInExport());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void deleteFolderRule() {
        int row = folderRulesTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        FolderRuleInfo current = folderRules.get(row);
        int confirm = JOptionPane.showConfirmDialog(
                this, "Delete folder rule \"" + current.path() + "\"?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            settingsRepository.deleteFolderRule(current.id());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private FolderRuleEdit promptFolderRule(FolderRuleInfo existing) {
        String initialPath = existing == null ? "" : existing.path();
        String lotro = currentLotroRoot();
        if (existing != null && !initialPath.isBlank()) {
            String resolved = LotroPaths.resolveMusicPath(initialPath, lotro);
            if (!resolved.isBlank()) {
                initialPath = resolved;
            }
        }
        JTextField path = new JTextField(initialPath, 28);
        path.setToolTipText("Path relative to Music folder (e.g. OldSongs or Backup/2023)");
        JCheckBox enabled = new JCheckBox("Enabled", existing == null || existing.enabled());
        JCheckBox include = new JCheckBox(
                "Include in export", existing != null && existing.includeInExport());
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            Optional<Path> music = LotroPaths.musicRoot(currentLotroRoot());
            File start = music.map(Path::toFile).filter(File::isDirectory).orElse(null);
            File chosen = chooseDirectoryFile(
                    "Select folder to exclude (under Music)", start);
            if (chosen != null) {
                path.setText(chosen.getAbsolutePath());
            }
        });
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = formConstraints();
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Path"), c);
        c.gridx = 1;
        form.add(path, c);
        c.gridx = 2;
        form.add(browse, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 3;
        form.add(enabled, c);
        c.gridy = 2;
        form.add(include, c);
        int result = JOptionPane.showConfirmDialog(
                this, form, existing == null ? "Add folder rule" : "Edit folder rule",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || path.getText().isBlank()) {
            return null;
        }
        String stored = LotroPaths.toMusicRelative(path.getText().trim(), currentLotroRoot());
        return new FolderRuleEdit(stored, enabled.isSelected(), include.isSelected());
    }

    private void addStatus() {
        if (settingsRepository == null) {
            return;
        }
        StatusEdit edit = promptStatus(null);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.addStatus(edit.name(), edit.color(), statuses.size());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void editStatus() {
        int row = statusesTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        StatusInfo current = statuses.get(row);
        StatusEdit edit = promptStatus(current);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.updateStatus(current.id(), edit.name(), edit.color(), current.sortOrder());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void deleteStatus() {
        int row = statusesTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        StatusInfo current = statuses.get(row);
        int confirm = JOptionPane.showConfirmDialog(
                this, "Delete status \"" + current.name() + "\"?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            settingsRepository.deleteStatus(current.id());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void moveStatus(int delta) {
        int row = statusesTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        int target = row + delta;
        if (target < 0 || target >= statuses.size()) {
            return;
        }
        List<Long> order = new ArrayList<>();
        for (StatusInfo status : statuses) {
            order.add(status.id());
        }
        Long moved = order.remove(row);
        order.add(target, moved);
        try {
            settingsRepository.reorderStatuses(order);
            reloadSettingsEntities();
            statusesTable.getSelectionModel().setSelectionInterval(target, target);
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private StatusEdit promptStatus(StatusInfo existing) {
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 20);
        JTextField color = new JTextField(existing == null ? "#808080" : existing.color(), 10);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = formConstraints();
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Name"), c);
        c.gridx = 1;
        form.add(name, c);
        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("Color"), c);
        c.gridx = 1;
        form.add(color, c);
        int result = JOptionPane.showConfirmDialog(
                this, form, existing == null ? "Add status" : "Edit status",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || name.getText().isBlank()) {
            return null;
        }
        return new StatusEdit(name.getText().trim(), color.getText().trim());
    }

    private void addAccountTarget() {
        if (settingsRepository == null) {
            return;
        }
        AccountTargetEdit edit = promptAccountTarget(null);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.addAccountTarget(edit.accountName(), edit.pluginDataPath(), edit.enabled());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void editAccountTarget() {
        int row = accountTargetsTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        AccountTargetInfo current = accountTargets.get(row);
        AccountTargetEdit edit = promptAccountTarget(current);
        if (edit == null) {
            return;
        }
        try {
            settingsRepository.updateAccountTarget(
                    current.id(), edit.accountName(), edit.pluginDataPath(), edit.enabled());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private void deleteAccountTarget() {
        int row = accountTargetsTable.getSelectedRow();
        if (row < 0 || settingsRepository == null) {
            return;
        }
        AccountTargetInfo current = accountTargets.get(row);
        int confirm = JOptionPane.showConfirmDialog(
                this, "Delete account target \"" + current.accountName() + "\"?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            settingsRepository.deleteAccountTarget(current.id());
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
        }
    }

    private AccountTargetEdit promptAccountTarget(AccountTargetInfo existing) {
        JTextField account = new JTextField(existing == null ? "" : existing.accountName(), 20);
        JTextField path = new JTextField(existing == null ? "" : existing.pluginDataPath(), 28);
        JCheckBox enabled = new JCheckBox("Enabled", existing == null || existing.enabled());
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            Optional<Path> pluginData = LotroPaths.pluginDataRoot(currentLotroRoot());
            File start = pluginData.map(Path::toFile).filter(File::isDirectory).orElse(null);
            File chosen = chooseDirectoryFile("Select PluginData folder", start);
            if (chosen != null) {
                path.setText(chosen.getAbsolutePath());
            }
        });
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = formConstraints();
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Account"), c);
        c.gridx = 1;
        form.add(account, c);
        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("PluginData path"), c);
        c.gridx = 1;
        form.add(path, c);
        c.gridx = 2;
        form.add(browse, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        form.add(enabled, c);
        int result = JOptionPane.showConfirmDialog(
                this, form, existing == null ? "Add account target" : "Edit account target",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION
                || account.getText().isBlank()
                || path.getText().isBlank()) {
            return null;
        }
        return new AccountTargetEdit(
                account.getText().trim(), path.getText().trim(), enabled.isSelected());
    }

    private void scanAccountTargets() {
        if (settingsRepository == null) {
            return;
        }
        String lotroRoot = currentLotroRoot();
        if (lotroRoot.isBlank()) {
            LotroPaths.ensureDefaultLotroRoot(working);
            lotroRoot = working.lotroRoot();
            if (!lotroRoot.isBlank()) {
                lotroRootField.setText(lotroRoot);
            }
        }
        if (lotroRoot.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "LOTRO root directory is not set. Configure it in the Folder rules tab.",
                    "Scan Account Targets",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Optional<Path> pluginData = LotroPaths.pluginDataRoot(lotroRoot);
        if (pluginData.isEmpty() || !Files.isDirectory(pluginData.get())) {
            JOptionPane.showMessageDialog(
                    this,
                    "PluginData folder not found.",
                    "Scan Account Targets",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Set<String> existing = new HashSet<>();
        for (AccountTargetInfo target : accountTargets) {
            existing.add(target.accountName().toLowerCase(Locale.ROOT));
            existing.add(target.pluginDataPath().toLowerCase(Locale.ROOT));
        }
        int added = 0;
        try {
            for (LotroPaths.DiscoveredAccount discovered : LotroPaths.discoverAccountTargets(lotroRoot)) {
                String accountKey = discovered.accountName().toLowerCase(Locale.ROOT);
                String pathKey = discovered.pluginDataPath().toLowerCase(Locale.ROOT);
                if (existing.contains(accountKey) || existing.contains(pathKey)) {
                    continue;
                }
                settingsRepository.addAccountTarget(
                        discovered.accountName(), discovered.pluginDataPath(), true);
                existing.add(accountKey);
                existing.add(pathKey);
                added++;
            }
            reloadSettingsEntities();
        } catch (LibraryException ex) {
            showError(ex);
            return;
        }
        if (added > 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Found " + added + " new account(s).",
                    "Scan Account Targets",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "No new account targets found.",
                    "Scan Account Targets",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showError(LibraryException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Database error", JOptionPane.ERROR_MESSAGE);
    }

    private record FolderRuleEdit(String path, boolean enabled, boolean includeInExport) {
    }

    private record StatusEdit(String name, String color) {
    }

    private record AccountTargetEdit(String accountName, String pluginDataPath, boolean enabled) {
    }

    private String currentLotroRoot() {
        String fromField = lotroRootField.getText();
        if (fromField != null && !fromField.isBlank()) {
            return fromField.trim();
        }
        return working.lotroRoot() == null ? "" : working.lotroRoot();
    }

    private void browseLotroRoot() {
        String current = currentLotroRoot();
        if (current.isBlank()) {
            current = LotroPaths.defaultLotroRoot().map(Path::toString).orElse("");
        }
        File start = null;
        if (!current.isBlank()) {
            File currentFile = new File(current);
            start = currentFile.isDirectory() ? currentFile : currentFile.getParentFile();
        }
        if (start == null) {
            start = LotroPaths.documentsLibraryPath().map(Path::toFile).orElse(null);
        }
        File chosen = chooseDirectoryFile(
                "Select Lord of the Rings Online directory", start);
        if (chosen != null) {
            lotroRootField.setText(chosen.getAbsolutePath());
            working.setLotroRoot(chosen.getAbsolutePath());
        }
    }

    private void browseSetExportDir() {
        String lotro = currentLotroRoot();
        Optional<Path> music = LotroPaths.musicRoot(lotro);
        String currentStored = setExportDirField.getText();
        String currentResolved = LotroPaths.resolveMusicPath(currentStored, lotro);
        File start = null;
        if (!currentResolved.isBlank()) {
            File currentFile = new File(currentResolved);
            if (currentFile.isDirectory()) {
                start = currentFile;
            }
        }
        if (start == null && music.isPresent() && Files.isDirectory(music.get())) {
            start = music.get().toFile();
        }
        File chosen = chooseDirectoryFile(
                "Select Set Export directory (under Music folder)", start);
        if (chosen != null) {
            String relative = LotroPaths.toMusicRelative(chosen.getAbsolutePath(), lotro);
            setExportDirField.setText(relative);
        }
    }

    private File chooseDirectoryFile(String title, File startDirectory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (startDirectory != null && startDirectory.isDirectory()) {
            chooser.setCurrentDirectory(startDirectory);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    /**
     * Compact default-filters editor shared by Settings.
     */
    static final class DefaultFiltersPanel extends JPanel {

        private final JComboBox<String> inSet = new JComboBox<>(new String[] {"Either", "Yes", "No"});
        private final JSpinner ratingFrom = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
        private final JSpinner ratingTo = new JSpinner(new SpinnerNumberModel(5, 0, 5, 1));
        private final JSpinner partsMin = new JSpinner(new SpinnerNumberModel(1, 1, 24, 1));
        private final JSpinner partsMax = new JSpinner(new SpinnerNumberModel(24, 1, 24, 1));
        private final JCheckBox durationMinNone = new JCheckBox("Duration min none", true);
        private final JCheckBox durationMaxNone = new JCheckBox("Duration max none", true);
        private final JSpinner durationMinSec = DurationSpinners.create(0, 0, 7200, 1);
        private final JSpinner durationMaxSec = DurationSpinners.create(1200, 0, 7200, 1);
        private final JComboBox<String> lastPlayedMode = new JComboBox<>(new String[] {"time", "date"});
        private final JSpinner lastFromAgo = new JSpinner(new SpinnerNumberModel(0, 0, 31_536_000, 3600));
        private final JTextField lastToAgo = new JTextField(8);
        private final JTextField lastFromIso = new JTextField(14);
        private final JTextField lastToIso = new JTextField(14);
        private final JTextField statusIdsField = new JTextField(20);

        DefaultFiltersPanel() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            GridBagConstraints c = formConstraints();
            int y = 0;
            addRow(c, y++, "In set", inSet);
            addRow(c, y++, "Rating from–to", row(ratingFrom, new JLabel("–"), ratingTo));
            addRow(c, y++, "Parts from–to", row(partsMin, new JLabel("–"), partsMax));
            addRow(c, y++, "Duration", row(durationMinSec, durationMinNone, durationMaxSec, durationMaxNone));
            addRow(c, y++, "Last played mode", lastPlayedMode);
            addRow(c, y++, "Last played from ago (s)", lastFromAgo);
            addRow(c, y++, "Last played to ago (blank = none)", lastToAgo);
            addRow(c, y++, "Last played from ISO", lastFromIso);
            addRow(c, y++, "Last played to ISO", lastToIso);
            addRow(c, y, "Status ids (comma-separated)", statusIdsField);

            SpinnerMouseWheel.install(ratingFrom);
            SpinnerMouseWheel.install(ratingTo);
            SpinnerMouseWheel.install(partsMin);
            SpinnerMouseWheel.install(partsMax);
            SpinnerMouseWheel.install(lastFromAgo);
        }

        void setFilters(DefaultFilters filters) {
            DefaultFilters source = filters == null ? DefaultFilters.builtins() : filters;
            inSet.setSelectedIndex(
                    "yes".equals(source.inSet()) ? 1 : "no".equals(source.inSet()) ? 2 : 0);
            ratingFrom.setValue(source.ratingFrom());
            ratingTo.setValue(source.ratingTo());
            partsMin.setValue(source.partsMin());
            partsMax.setValue(source.partsMax());
            durationMinNone.setSelected(source.durationMinNone());
            durationMaxNone.setSelected(source.durationMaxNone());
            durationMinSec.setValue(source.durationMinSec());
            durationMaxSec.setValue(source.durationMaxSec());
            lastPlayedMode.setSelectedItem(source.lastPlayedMode());
            lastFromAgo.setValue(source.lastPlayedFromSecondsAgo() == null ? 0 : source.lastPlayedFromSecondsAgo());
            lastToAgo.setText(source.lastPlayedToSecondsAgo() == null
                    ? ""
                    : String.valueOf(source.lastPlayedToSecondsAgo()));
            lastFromIso.setText(source.lastPlayedFromIso() == null ? "" : source.lastPlayedFromIso());
            lastToIso.setText(source.lastPlayedToIso() == null ? "" : source.lastPlayedToIso());
            statusIdsField.setText(joinIds(source.statusIds()));
        }

        DefaultFilters toFilters() {
            DefaultFilters filters = DefaultFilters.builtins();
            int inSetIndex = inSet.getSelectedIndex();
            filters.setInSet(inSetIndex == 1 ? "yes" : inSetIndex == 2 ? "no" : null);
            filters.setRatingFrom((Integer) ratingFrom.getValue());
            filters.setRatingTo((Integer) ratingTo.getValue());
            filters.setPartsMin((Integer) partsMin.getValue());
            filters.setPartsMax((Integer) partsMax.getValue());
            filters.setDurationMinNone(durationMinNone.isSelected());
            filters.setDurationMaxNone(durationMaxNone.isSelected());
            filters.setDurationMinSec((Integer) durationMinSec.getValue());
            filters.setDurationMaxSec((Integer) durationMaxSec.getValue());
            filters.setLastPlayedMode(String.valueOf(lastPlayedMode.getSelectedItem()));
            filters.setLastPlayedFromSecondsAgo((Integer) lastFromAgo.getValue());
            String toAgoText = lastToAgo.getText().trim();
            if (toAgoText.isEmpty()) {
                filters.setLastPlayedToSecondsAgo(null);
            } else {
                try {
                    filters.setLastPlayedToSecondsAgo(Integer.parseInt(toAgoText));
                } catch (NumberFormatException ex) {
                    filters.setLastPlayedToSecondsAgo(null);
                }
            }
            filters.setLastPlayedFromIso(lastFromIso.getText());
            filters.setLastPlayedToIso(lastToIso.getText());
            filters.setStatusIds(parseIds(statusIdsField.getText()));
            return filters;
        }

        private void addRow(GridBagConstraints c, int y, String label, java.awt.Component component) {
            c.gridx = 0;
            c.gridy = y;
            c.gridwidth = 1;
            add(new JLabel(label), c);
            c.gridx = 1;
            add(component, c);
        }

        private static JPanel row(java.awt.Component... components) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            for (java.awt.Component component : components) {
                panel.add(component);
            }
            return panel;
        }

        private static String joinIds(List<Long> ids) {
            if (ids == null || ids.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(ids.get(i));
            }
            return builder.toString();
        }

        private static List<Long> parseIds(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (String part : text.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                    // skip invalid tokens
                }
            }
            return ids;
        }
    }
}
