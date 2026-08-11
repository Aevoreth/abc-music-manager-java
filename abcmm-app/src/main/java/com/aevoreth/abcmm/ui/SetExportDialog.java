package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.export.LayoutExportOrder;
import com.aevoreth.abcmm.domain.export.SetExportException;
import com.aevoreth.abcmm.domain.export.SetExportItemInfo;
import com.aevoreth.abcmm.domain.export.SetExportService;
import com.aevoreth.abcmm.domain.export.SetExportSettings;
import com.aevoreth.abcmm.domain.export.SetFilenameTemplate;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.prefs.PreferencesException;
import com.aevoreth.abcmm.domain.prefs.PreferencesStore;
import com.aevoreth.abcmm.domain.prefs.SetExportPreferences;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Modal dialog for exporting a setlist to folder and/or zip (Python {@code SetExportDialog}).
 */
public final class SetExportDialog extends JDialog {

    private static final String FILENAME_VARS_HELP = """
            $FileName — Original filename without .abc
            $SongIndex — 1-based position in setlist (e.g. 001)
            $PartCount — Number of parts
            $SongComposer — Composers (C: field)
            $SongTranscriber — Transcriber (Z: field)
            $SongLength — Duration in mm_ss format (for exported filenames)
            $SongTitle — Title (T: field)
            """;

    private static final String PART_VARS_HELP = """
            $FileName — Original filename without .abc
            $SongIndex — 1-based position in setlist (e.g. 001)
            $PartCount — Number of parts
            $SongComposer — Composers (C: field)
            $SongTranscriber — Transcriber (Z: field)
            $SongLength — Duration as m:ss (minutes not zero-padded, seconds two digits, e.g. 2:05)
            $SongTitle — Title (T: field)
            $PartInstrument — Made-for instrument (from %%made-for)
            $PartName — Unmodified %%part-name comment value
            $PartTitle — Original part T: line (first T: in that X: block)
            $PartNumber — Part number (X: value)
            $PlayerAssignment — Player assigned to this part in the setlist band layout
            $Numeration — 1, 2, … when multiple parts share the same %%part-name; empty if unique
            """;

    private final SetlistInfo setlist;
    private final Preferences preferences;
    private final PreferencesStore preferencesStore;
    private final SetlistRepository setlistRepository;
    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final PlayerRepository playerRepository;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel dirLabel = new JLabel();
    private final JTextField setNameField = new JTextField();
    private final JCheckBox renameCheck = new JCheckBox("Rename ABC files using pattern");
    private final JCheckBox renamePartsCheck = new JCheckBox("Rename parts in exported ABC using pattern");
    private final JCheckBox folderCheck = new JCheckBox("Export as folder");
    private final JCheckBox zipCheck = new JCheckBox("Export as zip");
    private final JCheckBox csvCheck = new JCheckBox("Export CSV part sheet");
    private final JCheckBox abcpCheck = new JCheckBox("Export ABCP playlist (relative paths for ABC Player)");
    private final JCheckBox composerCheck = new JCheckBox("Include composer in CSV");

    private final JComboBox<String> whitespaceCombo = new JComboBox<>(SetFilenameTemplate.SPACE_REPLACE_LABELS);
    private final JTextField patternField = new JTextField();
    private final JLabel exampleLabel = new JLabel();

    private final JComboBox<String> partWhitespaceCombo = new JComboBox<>(SetFilenameTemplate.SPACE_REPLACE_LABELS);
    private final JTextField partPatternField = new JTextField();
    private final JLabel partExampleLabel = new JLabel();

    private final JRadioButton visibleColRadio = new JRadioButton("Use visible table columns");
    private final JRadioButton customColRadio = new JRadioButton("Use custom columns");
    private final JPanel customColsGroup = new JPanel();
    private final Map<String, JCheckBox> csvColChecks = new LinkedHashMap<>();
    private final JComboBox<String> partColCombo = new JComboBox<>(new String[] {
            "Use Part Names", "Use Instrument Names"
    });

    private final DefaultTableModel csvRenameModel = new DefaultTableModel(new Object[] {"Find", "Replace"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JTable csvRenameTable = new JTable(csvRenameModel);

    private final DefaultListModel<PlayerOrderEntry> playerOrderModel = new DefaultListModel<>();
    private final JList<PlayerOrderEntry> playerList = new JList<>(playerOrderModel);
    private final JLabel playerOrderPlaceholder = new JLabel(
            "Assign a band layout to the setlist to configure player column order.");

    private final JLabel statusLabel = new JLabel(" ");
    private final JButton exportButton = new JButton("Export");
    private final JButton cancelButton = new JButton("Cancel");

    private String outputDir;
    private boolean exporting;

    public SetExportDialog(
            Window owner,
            SetlistInfo setlist,
            Preferences preferences,
            PreferencesStore preferencesStore,
            SetlistRepository setlistRepository,
            SongRepository songRepository,
            BandRepository bandRepository,
            PlayerRepository playerRepository) {
        super(owner, "Export Set", ModalityType.APPLICATION_MODAL);
        this.setlist = Objects.requireNonNull(setlist, "setlist");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.preferencesStore = Objects.requireNonNull(preferencesStore, "preferencesStore");
        this.setlistRepository = Objects.requireNonNull(setlistRepository, "setlistRepository");
        this.songRepository = Objects.requireNonNull(songRepository, "songRepository");
        this.bandRepository = Objects.requireNonNull(bandRepository, "bandRepository");
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");

        SetExportPreferences prefs = preferences.setExport();
        String defaultDir = resolveDefaultOutputDir(preferences);
        outputDir = prefs.outputDirectory() == null || prefs.outputDirectory().isBlank()
                ? defaultDir
                : prefs.outputDirectory();

        setMinimumSize(new Dimension(560, 520));
        setPreferredSize(new Dimension(640, 560));
        buildUi(prefs);
        pack();
        setLocationRelativeTo(owner);
    }

    public static void show(
            Window owner,
            SetlistInfo setlist,
            Preferences preferences,
            PreferencesStore preferencesStore,
            SetlistRepository setlistRepository,
            SongRepository songRepository,
            BandRepository bandRepository,
            PlayerRepository playerRepository) {
        SetExportDialog dialog = new SetExportDialog(
                owner,
                setlist,
                preferences,
                preferencesStore,
                setlistRepository,
                songRepository,
                bandRepository,
                playerRepository);
        dialog.setVisible(true);
    }

    private void buildUi(SetExportPreferences prefs) {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabs.addTab("Export Settings", buildSettingsTab(prefs));
        tabs.addTab("ABC File Renaming", buildFilenameTab(prefs));
        tabs.addTab("Part Renaming", buildPartRenameTab(prefs));
        tabs.addTab("CSV Part Sheet", buildCsvTab(prefs));
        tabs.addTab("CSV Part Renaming", buildCsvRenameTab(prefs));
        tabs.addTab("Player Column Order", buildPlayerOrderTab());

        root.add(tabs, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        south.add(statusLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancelButton.addActionListener(e -> {
            if (!exporting) {
                dispose();
            }
        });
        exportButton.addActionListener(e -> startExport());
        getRootPane().setDefaultButton(exportButton);
        buttons.add(cancelButton);
        buttons.add(exportButton);
        south.add(buttons, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        onCsvToggled(csvCheck.isSelected());
        onRenamePartsToggled(renamePartsCheck.isSelected());
        onColumnModeChanged();
        updatePlayerTabState();
        updateFilenameExample();
        updatePartExample();
    }

    private JPanel buildSettingsTab(SetExportPreferences prefs) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel dirRow = new JPanel(new BorderLayout(8, 0));
        dirRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dirRow.add(new JLabel("Output folder:"), BorderLayout.WEST);
        dirLabel.setText(outputDir == null || outputDir.isBlank() ? "(not set)" : outputDir);
        dirRow.add(dirLabel, BorderLayout.CENTER);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> browseOutput());
        dirRow.add(browse, BorderLayout.EAST);
        preventVerticalStretch(dirRow);
        panel.add(dirRow);
        panel.add(Box.createVerticalStrut(8));

        JLabel nameLabel = new JLabel("Set name:");
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);
        setNameField.setText(SetExportService.sanitizeForPath(
                setlist.name() == null || setlist.name().isBlank() ? "Untitled Set" : setlist.name()));
        setNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(setNameField);
        panel.add(setNameField);
        panel.add(Box.createVerticalStrut(8));

        renameCheck.setSelected(prefs.renameAbcFiles());
        renameCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(renameCheck);
        renamePartsCheck.setSelected(prefs.renameParts());
        renamePartsCheck.addActionListener(e -> onRenamePartsToggled(renamePartsCheck.isSelected()));
        renamePartsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(renamePartsCheck);
        folderCheck.setSelected(prefs.exportAsFolder());
        folderCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(folderCheck);
        zipCheck.setSelected(prefs.exportAsZip());
        zipCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(zipCheck);
        csvCheck.setSelected(prefs.exportCsvPartSheet());
        csvCheck.addActionListener(e -> onCsvToggled(csvCheck.isSelected()));
        csvCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(csvCheck);
        abcpCheck.setSelected(prefs.exportAbcpPlaylist());
        abcpCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(abcpCheck);
        composerCheck.setSelected(prefs.includeComposerInCsv());
        composerCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(composerCheck);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildFilenameTab(SetExportPreferences prefs) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel wsLabel = new JLabel("Replace spaces in variables with:");
        wsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(wsLabel);
        whitespaceCombo.setSelectedIndex(indexOfWhitespace(prefs.whitespaceReplace()));
        whitespaceCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(whitespaceCombo);
        whitespaceCombo.addActionListener(e -> {
            syncWhitespaceCombos(whitespaceCombo, partWhitespaceCombo);
            updateFilenameExample();
            updatePartExample();
        });
        panel.add(whitespaceCombo);
        panel.add(Box.createVerticalStrut(8));
        JLabel patternLabel = new JLabel("Pattern for new ABC filenames:");
        patternLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(patternLabel);
        patternField.setText(prefs.filenamePattern());
        patternField.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(patternField);
        patternField.getDocument().addDocumentListener(simpleDocListener(this::updateFilenameExample));
        panel.add(patternField);
        exampleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(exampleLabel);
        panel.add(Box.createVerticalStrut(8));
        JLabel varsTitle = new JLabel("Variables:");
        varsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(varsTitle);
        JLabel vars = new JLabel("<html>" + FILENAME_VARS_HELP.replace("\n", "<br>") + "</html>");
        vars.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(vars);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildPartRenameTab(SetExportPreferences prefs) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel wsLabel = new JLabel("Replace spaces in variables with:");
        wsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(wsLabel);
        partWhitespaceCombo.setSelectedIndex(indexOfWhitespace(prefs.whitespaceReplace()));
        partWhitespaceCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(partWhitespaceCombo);
        partWhitespaceCombo.addActionListener(e -> {
            syncWhitespaceCombos(partWhitespaceCombo, whitespaceCombo);
            updateFilenameExample();
            updatePartExample();
        });
        panel.add(partWhitespaceCombo);
        panel.add(Box.createVerticalStrut(8));
        JLabel patternLabel = new JLabel("Pattern for each part's T: line:");
        patternLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(patternLabel);
        partPatternField.setText(prefs.partNamePattern());
        partPatternField.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(partPatternField);
        partPatternField.getDocument().addDocumentListener(simpleDocListener(this::updatePartExample));
        panel.add(partPatternField);
        partExampleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(partExampleLabel);
        panel.add(Box.createVerticalStrut(8));
        JLabel varsTitle = new JLabel("Variables:");
        varsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(varsTitle);
        JLabel vars = new JLabel("<html>" + PART_VARS_HELP.replace("\n", "<br>") + "</html>");
        vars.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(vars);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildCsvTab(SetExportPreferences prefs) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ButtonGroup group = new ButtonGroup();
        visibleColRadio.setSelected(prefs.csvUseVisibleColumns());
        customColRadio.setSelected(!prefs.csvUseVisibleColumns());
        group.add(visibleColRadio);
        group.add(customColRadio);
        visibleColRadio.addActionListener(e -> onColumnModeChanged());
        customColRadio.addActionListener(e -> onColumnModeChanged());
        visibleColRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        customColRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(visibleColRadio);
        panel.add(customColRadio);

        customColsGroup.setLayout(new BoxLayout(customColsGroup, BoxLayout.Y_AXIS));
        customColsGroup.setBorder(BorderFactory.createTitledBorder("Select columns"));
        customColsGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        Map<String, Boolean> enabled = prefs.csvColumnsEnabled();
        for (String col : SetExportSettings.CSV_AVAILABLE_COLUMNS) {
            boolean checked = enabled.getOrDefault(col, SetExportSettings.CSV_DEFAULT_ENABLED.getOrDefault(col, false));
            JCheckBox cb = new JCheckBox(col, checked);
            csvColChecks.put(col, cb);
            customColsGroup.add(cb);
        }
        panel.add(customColsGroup);
        panel.add(Box.createVerticalStrut(8));
        JLabel partLabel = new JLabel("Part columns content (when no band layout):");
        partLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(partLabel);
        partColCombo.setSelectedIndex("instrument".equals(prefs.csvPartColumns()) ? 1 : 0);
        partColCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        preventVerticalStretch(partColCombo);
        panel.add(partColCombo);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildCsvRenameTab(SetExportPreferences prefs) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel help = new JLabel("<html>Optional find/replace pairs applied to part names in the CSV "
                + "(after part vs instrument choice). Rules run in order; each step replaces all "
                + "occurrences. Example: \"Basic Theorbo\" → \"Theorbo\".</html>");
        panel.add(help, BorderLayout.NORTH);
        csvRenameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        List<SetExportSettings.FindReplaceRule> rules = prefs.csvPartRenameRules();
        if (rules.isEmpty()) {
            csvRenameModel.addRow(new Object[] {"", ""});
        } else {
            for (SetExportSettings.FindReplaceRule rule : rules) {
                csvRenameModel.addRow(new Object[] {rule.find(), rule.replace()});
            }
        }
        panel.add(new JScrollPane(csvRenameTable), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add row");
        add.addActionListener(e -> csvRenameModel.addRow(new Object[] {"", ""}));
        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            int row = csvRenameTable.getSelectedRow();
            if (row >= 0) {
                csvRenameModel.removeRow(row);
            }
            if (csvRenameModel.getRowCount() == 0) {
                csvRenameModel.addRow(new Object[] {"", ""});
            }
        });
        buttons.add(add);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPlayerOrderTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Drag to reorder player columns for CSV export:"), BorderLayout.NORTH);
        playerList.setVisibleRowCount(12);
        playerList.setDragEnabled(true);
        playerList.setDropMode(javax.swing.DropMode.INSERT);
        playerList.setTransferHandler(new PlayerOrderTransferHandler());
        loadPlayerOrder();
        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(playerList), BorderLayout.CENTER);
        playerOrderPlaceholder.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        center.add(playerOrderPlaceholder, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void loadPlayerOrder() {
        playerOrderModel.clear();
        Long layoutId = setlist.bandLayoutId();
        if (layoutId == null) {
            return;
        }
        try {
            BandLayoutInfo layout = findLayout(layoutId);
            List<BandLayoutSlotInfo> slots = LayoutExportOrder.listSlotsForExport(
                    bandRepository.listSlots(layoutId),
                    LayoutExportOrder.parseExportColumnOrderJson(
                            layout == null ? null : layout.exportColumnOrderJson()));
            for (BandLayoutSlotInfo slot : slots) {
                String name = slot.playerName() == null || slot.playerName().isBlank()
                        ? "Player " + slot.playerId()
                        : slot.playerName();
                playerOrderModel.addElement(new PlayerOrderEntry(slot.playerId(), name));
            }
        } catch (LibraryException | SetExportException ex) {
            statusLabel.setText(ex.getMessage());
        }
    }

    private BandLayoutInfo findLayout(long bandLayoutId) throws LibraryException, SetExportException {
        for (var band : bandRepository.listBands()) {
            for (BandLayoutInfo layout : bandRepository.listLayouts(band.id())) {
                if (layout.id() == bandLayoutId) {
                    return layout;
                }
            }
        }
        throw new SetExportException("Band layout not found: " + bandLayoutId);
    }

    private void browseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path start = Paths.get(outputDir == null || outputDir.isBlank()
                ? System.getProperty("user.home")
                : outputDir);
        if (Files.isDirectory(start)) {
            chooser.setCurrentDirectory(start.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDir = chooser.getSelectedFile().getAbsolutePath();
            dirLabel.setText(outputDir);
        }
    }

    private void onCsvToggled(boolean checked) {
        composerCheck.setEnabled(checked && visibleColRadio.isSelected());
        tabs.setEnabledAt(3, checked);
        tabs.setEnabledAt(4, checked);
    }

    private void onRenamePartsToggled(boolean checked) {
        tabs.setEnabledAt(2, checked);
    }

    private void onColumnModeChanged() {
        boolean useVisible = visibleColRadio.isSelected();
        customColsGroup.setEnabled(!useVisible);
        for (Component c : customColsGroup.getComponents()) {
            c.setEnabled(!useVisible);
        }
        composerCheck.setEnabled(csvCheck.isSelected() && useVisible);
    }

    private void updatePlayerTabState() {
        boolean hasLayout = setlist.bandLayoutId() != null;
        tabs.setEnabledAt(5, hasLayout);
        playerList.setVisible(hasLayout);
        playerOrderPlaceholder.setVisible(!hasLayout);
    }

    private void updateFilenameExample() {
        String pattern = patternField.getText();
        if (pattern == null || pattern.isBlank()) {
            pattern = "$SongIndex_$FileName";
        }
        String example = SetFilenameTemplate.formatFilename(
                pattern,
                "my song.abc",
                2,
                "My Song",
                "Composer Name",
                "Transcriber",
                125,
                3,
                currentWhitespace(),
                true);
        exampleLabel.setText("Example: " + example);
    }

    private void updatePartExample() {
        String pattern = partPatternField.getText();
        if (pattern == null || pattern.isBlank()) {
            pattern = "$PartTitle";
        }
        String example = SetFilenameTemplate.formatPartName(
                pattern,
                "my song.abc",
                2,
                "My Song",
                "Composer Name",
                "Transcriber",
                125,
                3,
                "Lute",
                "Basic Flute",
                "Flute",
                "1",
                "Player One",
                "1",
                currentWhitespace(),
                true);
        partExampleLabel.setText("Example T: line: " + example);
    }

    private String currentWhitespace() {
        int idx = whitespaceCombo.getSelectedIndex();
        if (idx < 0 || idx >= SetFilenameTemplate.SPACE_REPLACE_CHARS.length) {
            return " ";
        }
        return SetFilenameTemplate.SPACE_REPLACE_CHARS[idx];
    }

    private SetExportSettings collectSettings() {
        SetExportSettings settings = new SetExportSettings();
        settings.setOutputDirectory(Paths.get(
                outputDir == null || outputDir.isBlank()
                        ? System.getProperty("user.home")
                        : outputDir));
        settings.setSetName(SetExportService.sanitizeForPath(
                setNameField.getText() == null || setNameField.getText().isBlank()
                        ? setlist.name()
                        : setNameField.getText()));
        settings.setExportAsFolder(folderCheck.isSelected());
        settings.setExportAsZip(zipCheck.isSelected());
        settings.setRenameAbcFiles(renameCheck.isSelected());
        settings.setFilenamePattern(patternField.getText());
        settings.setWhitespaceReplace(currentWhitespace());
        settings.setPartCountZeroPadded(true);
        settings.setExportCsvPartSheet(csvCheck.isSelected());
        settings.setExportAbcpPlaylist(abcpCheck.isSelected());
        settings.setIncludeComposerInCsv(composerCheck.isSelected());
        settings.setCsvUseVisibleColumns(visibleColRadio.isSelected());
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (Map.Entry<String, JCheckBox> e : csvColChecks.entrySet()) {
            enabled.put(e.getKey(), e.getValue().isSelected());
        }
        settings.setCsvColumnsEnabled(enabled);
        settings.setCsvPartColumns(partColCombo.getSelectedIndex() == 0 ? "part" : "instrument");
        settings.setRenameParts(renamePartsCheck.isSelected());
        settings.setPartNamePattern(partPatternField.getText());
        settings.setCsvPartRenameRules(collectCsvRenameRules());
        return settings;
    }

    private List<SetExportSettings.FindReplaceRule> collectCsvRenameRules() {
        if (csvRenameTable.isEditing()) {
            csvRenameTable.getCellEditor().stopCellEditing();
        }
        List<List<String>> raw = new ArrayList<>();
        for (int r = 0; r < csvRenameModel.getRowCount(); r++) {
            Object f = csvRenameModel.getValueAt(r, 0);
            Object repl = csvRenameModel.getValueAt(r, 1);
            raw.add(List.of(f == null ? "" : String.valueOf(f), repl == null ? "" : String.valueOf(repl)));
        }
        return com.aevoreth.abcmm.domain.export.CsvPartSheet.normalizeRenameRules(raw);
    }

    private List<Long> playerIdsInOrder() {
        if (setlist.bandLayoutId() == null || !playerList.isVisible()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < playerOrderModel.size(); i++) {
            ids.add(playerOrderModel.get(i).playerId());
        }
        return ids;
    }

    private void startExport() {
        if (exporting) {
            return;
        }
        if (!folderCheck.isSelected() && !zipCheck.isSelected()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select export as folder and/or zip.",
                    "Export Set",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetExportSettings settings = collectSettings();
        List<Long> playerOrder = playerIdsInOrder();

        exporting = true;
        exportButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Starting export...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<SetExportItemInfo> items = setlistRepository.listItemsForExport(setlist.id());
                SetExportService service = new SetExportService(
                        setlistRepository, songRepository, bandRepository, playerRepository);
                service.exportSet(
                        setlist.id(),
                        setlist.name(),
                        setlist.bandLayoutId(),
                        items,
                        settings,
                        playerOrder,
                        this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                exporting = false;
                exportButton.setEnabled(true);
                cancelButton.setEnabled(true);
                try {
                    get();
                    preferences.setExport().captureFrom(settings, settings.outputDirectory().toString());
                    try {
                        preferencesStore.save(preferences);
                    } catch (PreferencesException ex) {
                        // prefs save failure should not undo a successful export
                    }
                    statusLabel.setText("Export finished.");
                    JOptionPane.showMessageDialog(
                            SetExportDialog.this,
                            "Export finished.",
                            "Export Set",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    statusLabel.setText(message);
                    JOptionPane.showMessageDialog(
                            SetExportDialog.this,
                            message,
                            "Export Set",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    static String resolveDefaultOutputDir(Preferences preferences) {
        String setExport = preferences.setExportDir();
        if (setExport != null && !setExport.isBlank()) {
            String resolved = LotroPaths.resolveMusicPath(setExport, preferences.lotroRoot());
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return System.getProperty("user.home");
    }

    /** BoxLayout.Y_AXIS stretches components with large max height; keep form rows single-line tall. */
    private static void preventVerticalStretch(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        int height = Math.max(preferred.height, 24);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private static int indexOfWhitespace(String value) {
        for (int i = 0; i < SetFilenameTemplate.SPACE_REPLACE_CHARS.length; i++) {
            if (SetFilenameTemplate.SPACE_REPLACE_CHARS[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static void syncWhitespaceCombos(JComboBox<String> source, JComboBox<String> target) {
        if (target.getSelectedIndex() != source.getSelectedIndex()) {
            target.setSelectedIndex(source.getSelectedIndex());
        }
    }

    private static javax.swing.event.DocumentListener simpleDocListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }
        };
    }

    private record PlayerOrderEntry(long playerId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private final class PlayerOrderTransferHandler extends javax.swing.TransferHandler {
        private int fromIndex = -1;

        @Override
        public int getSourceActions(javax.swing.JComponent c) {
            return MOVE;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
            fromIndex = playerList.getSelectedIndex();
            if (fromIndex < 0) {
                return null;
            }
            return new java.awt.datatransfer.StringSelection(String.valueOf(fromIndex));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support) || fromIndex < 0) {
                return false;
            }
            JList.DropLocation drop = (JList.DropLocation) support.getDropLocation();
            int toIndex = drop.getIndex();
            if (toIndex < 0) {
                toIndex = playerOrderModel.size();
            }
            PlayerOrderEntry entry = playerOrderModel.remove(fromIndex);
            if (toIndex > fromIndex) {
                toIndex--;
            }
            toIndex = Math.max(0, Math.min(toIndex, playerOrderModel.size()));
            playerOrderModel.add(toIndex, entry);
            playerList.setSelectedIndex(toIndex);
            fromIndex = -1;
            return true;
        }
    }
}
