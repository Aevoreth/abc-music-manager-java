package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;
import com.aevoreth.abcmm.domain.optimizer.OptimizationProfileRepository;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;

/**
 * Shared band goals, advanced switch tuning, and instrument rules.
 * Preferences do not change {@code PlayerInstrument}.
 */
public final class BandOptimizerForm extends JPanel {

    static final String UNIT_EXPLANATION =
            "One smooth unit is one transition at the target plus one. One total unit is one"
                + " instrument switch. One instrument preference unit is a penalty of 1. One keep unit is"
                + " one changed assignment. Zero turns that goal off. A preference does not change"
                + " which instruments a player owns. Use Optimizer help for examples and instrument"
                + " rules for back-row preferences.";

    private final OptimizationProfileRepository profiles;
    private final PlayerRepository players;
    private final long bandId;
    private final JSpinner smooth = weightSpinner(4);
    private final JSpinner total = weightSpinner(1);
    private final JSpinner role = weightSpinner(1);
    private final JSpinner keep = weightSpinner(1);
    private final JSpinner target = new JSpinner(new SpinnerNumberModel(2, 0, 100, 1));
    private final JSpinner defaultSwitch = weightSpinner(1);
    private InstrumentRulesForm rulesEditor;
    private final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
    private final List<PlayerRuleRow> rows = new ArrayList<>();
    private final List<InstrumentPlacementRule> placements = new ArrayList<>();

    public BandOptimizerForm(
            OptimizationProfileRepository profiles,
            BandRepository bands,
            PlayerRepository players,
            long bandId) throws LibraryException {
        super(new BorderLayout(8, 8));
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.players = Objects.requireNonNull(players, "players");
        this.bandId = bandId;
        BandOptimizationProfile profile = profiles.getBandProfile(bandId);
        placements.addAll(profile.instrumentPlacements());
        setWeight(smooth, profile.smoothWeight());
        setWeight(total, profile.totalWeight());
        setWeight(role, profile.roleWeight());
        setWeight(keep, profile.keepWeight());
        setTarget(profile.targetChanges());
        setWeight(defaultSwitch, profile.defaultSwitchMultiplier());
        JTextArea note = new JTextArea(UNIT_EXPLANATION);
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setRows(5);
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel weights = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;
        JPanel goals = new JPanel(new GridBagLayout());
        String[] captions = {"Avoid simultaneous instrument changes", "Reduce total instrument changes",
                "Respect instrument preferences", "Preserve current assignments"};
        String[] explanations = {
                "Discourage many characters switching at once. Changes above the target cost increasingly more.",
                "Discourage every instrument switch between adjacent songs. Idle on either side counts as no switch.",
                "Respect character preferences, placement, avoidance, and song part preferences. Pins and Always rules stay mandatory.",
                "Discourage changing existing assignments. This is a preference; use a pin to fix an assignment."
        };
        JSpinner[] spinners = {smooth, total, role, keep};
        for (int i = 0; i < captions.length; i++) {
            JSpinner spinner = spinners[i];
            javax.swing.JCheckBox enabled = new javax.swing.JCheckBox(captions[i], number(spinner) > 0);
            OptimizerTooltips.describe(enabled, explanations[i]);
            OptimizerTooltips.describe(spinner, explanations[i] + " Higher weights increase its importance relative to other goals; zero turns it off.");
            double[] remembered = {number(spinner) > 0 ? number(spinner) : i == 0 ? 4 : 1};
            enabled.addActionListener(e -> {
                if (!enabled.isSelected()) { remembered[0] = number(spinner); spinner.setValue(0.0); }
                else spinner.setValue(remembered[0]);
            });
            spinner.addChangeListener(e -> enabled.setSelected(number(spinner) > 0));
            gc.gridx = 0; gc.gridy = i; goals.add(enabled, gc);
            addWeight(weights, gc, i, captions[i] + " weight", spinner);
        }
        JPanel targetControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel targetLabel = new JLabel("Target maximum:"); targetLabel.setLabelFor(target);
        OptimizerTooltips.describe(target, "Preferred maximum characters switching at one transition. A soft target, not a hard limit.");
        targetLabel.setToolTipText(target.getToolTipText());
        targetControl.add(targetLabel); targetControl.add(target);
        gc.gridx = 1; gc.gridy = 0; goals.add(targetControl, gc);
        target.setEnabled(number(smooth) > 0);
        smooth.addChangeListener(e -> target.setEnabled(number(smooth) > 0));
        OptimizerTooltips.describe(defaultSwitch, "Default cost multiplier for a character's switches. A character override replaces this value; zero removes that switch cost.");
        addWeight(weights, gc, 4, "Default character switch multiplier", defaultSwitch);
        JPanel people = new JPanel();
        people.setLayout(new BoxLayout(people, BoxLayout.Y_AXIS));
        Map<Long, PlayerRuleRow> byId = new LinkedHashMap<>();
        for (BandLayoutSlotInfo slot : bands.listSlots(bands.getOrCreatePrimaryLayout(bandId).id()))
            byId.put(slot.playerId(), row(slot.playerId(), displayName(slot), true));
        for (PlayerSwitchWeight weight : profile.playerSwitchWeights()) {
            byId.computeIfAbsent(weight.playerId(), id -> row(id, lookupName(id), false));
            byId.get(weight.playerId()).switchField.setText(formatWeight(weight.multiplier()));
        }
        for (InstrumentAffinityWeight weight : profile.instrumentAffinities())
            byId.computeIfAbsent(weight.playerId(), id -> row(id, lookupName(id), false));
        for (InstrumentPlacementRule rule : placements) {
            for (Long id : rule.preferredPlayerIds()) byId.computeIfAbsent(id, key -> row(key, lookupName(key), false));
            for (Long id : rule.avoidedPlayerIds()) byId.computeIfAbsent(id, key -> row(key, lookupName(key), false));
        }
        rows.addAll(byId.values());
        rows.sort(Comparator.comparing((PlayerRuleRow r) -> !r.onLayout).thenComparing(r -> r.name));
        Map<Long, String> names = new LinkedHashMap<>();
        for (PlayerRuleRow row : rows) { names.put(row.playerId, row.name); people.add(row.panel); }
        Map<Long, String> instrumentNames = new LinkedHashMap<>();
        for (var instrument : players.listInstruments()) instrumentNames.put(instrument.id(), displayInstrument(instrument.name()));
        rulesEditor = new InstrumentRulesForm(instrumentNames, names, profile.instrumentAffinities(), placements);
        JPanel advanced = new JPanel(new BorderLayout());
        advanced.add(weights, BorderLayout.NORTH);
        advanced.add(people, BorderLayout.CENTER);
        advanced.add(note, BorderLayout.SOUTH);
        advanced.setVisible(false);
        javax.swing.JCheckBox showAdvanced = new javax.swing.JCheckBox("Advanced tuning");
        showAdvanced.setToolTipText("Show exact goal weights and band or character switch-cost multipliers.");
        showAdvanced.addActionListener(e -> { advanced.setVisible(showAdvanced.isSelected()); revalidate(); });
        JPanel goalTab = new JPanel(new BorderLayout(8, 8));
        JPanel basic = new JPanel(new BorderLayout());
        JPanel goalContent = new JPanel(new FlowLayout(FlowLayout.LEFT)); goalContent.add(goals);
        basic.add(goalContent, BorderLayout.CENTER); basic.add(showAdvanced, BorderLayout.SOUTH);
        goalTab.add(basic, BorderLayout.NORTH); goalTab.add(advanced, BorderLayout.CENTER);
        String bandName = bands.listBands().stream().filter(b -> b.id() == bandId).map(b -> b.name()).findFirst().orElse("Band " + bandId);
        add(new JLabel("Band defaults: " + bandName + " — shared by sets using this band"), BorderLayout.NORTH);
        tabs.addTab("Goals", new JScrollPane(goalTab));
        tabs.addTab("Character & instrument rules", rulesEditor);
        tabs.setToolTipTextAt(0, "Shared goals for every set using this band.");
        tabs.setToolTipTextAt(1, "Character roles and placement preferences for each instrument.");
        tabs.addTab("Song exceptions", new JLabel("Open these settings from a set to edit its song exceptions."));
        tabs.setToolTipTextAt(2, "Preferences, pins, and preservation overrides for individual song occurrences.");
        add(tabs, BorderLayout.CENTER);
    }

    void showExceptions() { tabs.setSelectedIndex(2); }

    void setExceptions(Component component) { tabs.setComponentAt(2, component); }

    public void save() throws LibraryException {
        for (JSpinner spinner : List.of(smooth, total, role, keep, target, defaultSwitch)) {
            try { spinner.commitEdit(); } catch (java.text.ParseException ex) { throw new IllegalArgumentException("Enter a valid goal weight or target."); }
        }
        List<PlayerSwitchWeight> switches = new ArrayList<>();
        for (PlayerRuleRow row : rows) {
            String text = row.switchField.getText() == null ? "" : row.switchField.getText().strip();
            if (!text.isEmpty()) {
                switches.add(new PlayerSwitchWeight(row.playerId, parseWeight(text, row.name)));
            }
        }
        SearchBudgetPreset preset = profiles.getBandProfile(bandId).searchBudget();
        List<InstrumentAffinityWeight> affinities = rulesEditor.affinities();
        placements.clear(); placements.addAll(rulesEditor.placements());
        profiles.saveBandProfile(
                bandId,
                new BandOptimizationProfile(
                        BandOptimizationProfile.CURRENT_SETTINGS_VERSION,
                        number(smooth),
                        number(total),
                        number(role),
                        number(keep),
                        ((Number) target.getValue()).intValue(),
                        number(defaultSwitch),
                        switches,
                        affinities,
                        preset,
                        placements));
    }

    static SpinnerNumberModel weightModel(double value) {
        double max = Math.max(100.0d, value);
        return new SpinnerNumberModel(value, 0.0d, max, 0.5d);
    }

    private PlayerRuleRow row(long playerId, String name, boolean onLayout) {
        String shown = onLayout ? name : name + " (not on this layout)";
        JTextField field = new JTextField(6);
        field.setToolTipText("Override this character's switch cost. Blank inherits the band default; zero removes their switch cost.");
        field.getAccessibleContext().setAccessibleName("Switch multiplier for " + shown);
        JLabel switchLabel = new JLabel("Switch multiplier (blank inherits band default)");
        switchLabel.setLabelFor(field);
        switchLabel.setToolTipText(field.getToolTipText());
        PlayerRuleRow row = new PlayerRuleRow(
                playerId, shown, onLayout, field, new JPanel());

        JPanel panel = row.panel;
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.add(new JLabel(shown));
        panel.add(switchLabel);
        panel.add(field);

        panel.setMaximumSize(
                new java.awt.Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return row;
    }

    private String lookupName(long playerId) {
        try {
            PlayerInfo player = players.getPlayer(playerId);
            if (player != null && player.name() != null && !player.name().isBlank()) {
                return player.name().strip();
            }
        } catch (LibraryException ex) {
            return "Player " + playerId;
        }
        return "Player " + playerId;
    }

    private static String displayInstrument(String name) {
        String shown = LotroInstrumentDefaults.uiName(name == null ? "" : name);
        return shown == null || shown.isBlank() ? "Instrument" : shown;
    }

    private static String displayName(BandLayoutSlotInfo slot) {
        if (slot.playerName() == null || slot.playerName().isBlank()) {
            return "Player " + slot.playerId();
        }
        return slot.playerName().strip();
    }

    private static void addWeight(
            JPanel panel, GridBagConstraints gc, int row, String caption, JSpinner spinner) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(caption);
        label.setLabelFor(spinner);
        label.setToolTipText(spinner.getToolTipText());
        panel.add(label, gc);
        gc.gridx = 1;
        panel.add(spinner, gc);
    }

    private static void addTarget(
            JPanel panel, GridBagConstraints gc, int row, String caption, JSpinner spinner) {
        addWeight(panel, gc, row, caption, spinner);
    }

    private static JSpinner weightSpinner(double value) {
        JSpinner spinner = new JSpinner(weightModel(value));
        SpinnerMouseWheel.install(spinner);
        return spinner;
    }

    private static void setWeight(JSpinner spinner, double value) {
        spinner.setModel(weightModel(value));
        SpinnerMouseWheel.install(spinner);
    }

    private void setTarget(int value) {
        int max = Math.max(100, value);
        target.setModel(new SpinnerNumberModel(value, 0, max, 1));
        SpinnerMouseWheel.install(target);
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static double parseWeight(String text, String player) {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value < 0.0d) {
                throw new IllegalArgumentException("Switch multiplier for " + player + " cannot be negative.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Switch multiplier for " + player + " must be a number.");
        }
    }

    private static String formatWeight(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static final class PlayerRuleRow {
        private final long playerId;
        private final String name;
        private final boolean onLayout;
        private final JTextField switchField;
        private final JPanel panel;

        private PlayerRuleRow(
                long playerId,
                String name,
                boolean onLayout,
                JTextField switchField,
                JPanel panel) {
            this.playerId = playerId;
            this.name = name;
            this.onLayout = onLayout;
            this.switchField = switchField;
            this.panel = panel;
        }
    }
}
