package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.OptimizationProfileRepository;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;

/**
 * Band optimizer weights, search budget, switch multipliers, and instrument preferences.
 * Preferences do not change {@code PlayerInstrument}.
 */
public final class BandOptimizerForm extends JPanel {

    static final String UNIT_EXPLANATION =
            "One smooth unit is one transition at the target plus one. "
                    + "One total unit is one instrument switch. "
                    + "One role unit is a miss penalty of 1. "
                    + "One keep unit is one changed assignment. "
                    + "Zero turns that goal off. "
                    + "A preference does not change which instruments a player owns.";

    private final OptimizationProfileRepository profiles;
    private final PlayerRepository players;
    private final long bandId;
    private final JSpinner smooth = weightSpinner(4);
    private final JSpinner total = weightSpinner(1);
    private final JSpinner role = weightSpinner(1);
    private final JSpinner keep = weightSpinner(1);
    private final JSpinner target = new JSpinner(new SpinnerNumberModel(2, 0, 100, 1));
    private final JSpinner defaultSwitch = weightSpinner(1);
    private final JComboBox<SearchBudgetPreset> budget = new JComboBox<>(SearchBudgetPreset.values());
    private final List<PlayerRuleRow> rows = new ArrayList<>();

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
        setWeight(smooth, profile.smoothWeight());
        setWeight(total, profile.totalWeight());
        setWeight(role, profile.roleWeight());
        setWeight(keep, profile.keepWeight());
        setTarget(profile.targetChanges());
        setWeight(defaultSwitch, profile.defaultSwitchMultiplier());
        budget.setSelectedItem(profile.searchBudget());
        budget.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : budgetLabel(value));
            label.setOpaque(true);
            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });

        JTextArea note = new JTextArea(UNIT_EXPLANATION);
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel weights = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;
        addWeight(weights, gc, 0, "Smooth transitions", smooth);
        addWeight(weights, gc, 1, "Total switches", total);
        addWeight(weights, gc, 2, "Role preservation", role);
        addWeight(weights, gc, 3, "Existing assignments", keep);
        addTarget(weights, gc, 4, "Preferred max changes", target);
        addWeight(weights, gc, 5, "Default switch multiplier", defaultSwitch);
        gc.gridx = 0;
        gc.gridy = 6;
        JLabel budgetLabel = new JLabel("Search budget");
        budgetLabel.setLabelFor(budget);
        weights.add(budgetLabel, gc);
        gc.gridx = 1;
        weights.add(budget, gc);

        JPanel people = new JPanel();
        people.setLayout(new BoxLayout(people, BoxLayout.Y_AXIS));
        people.setBorder(BorderFactory.createTitledBorder("Performers"));
        Map<Long, PlayerRuleRow> byId = new LinkedHashMap<>();
        for (BandLayoutSlotInfo slot : bands.listSlots(bands.getOrCreatePrimaryLayout(bandId).id())) {
            byId.put(slot.playerId(), row(slot.playerId(), displayName(slot), true));
        }
        for (PlayerSwitchWeight weight : profile.playerSwitchWeights()) {
            byId.computeIfAbsent(weight.playerId(), id -> row(id, lookupName(id), false));
            byId.get(weight.playerId()).switchField.setText(formatWeight(weight.multiplier()));
        }
        for (InstrumentAffinityWeight weight : profile.instrumentAffinities()) {
            byId.computeIfAbsent(weight.playerId(), id -> row(id, lookupName(id), false));
            byId.get(weight.playerId()).affinities.put(weight.instrumentId(), weight.missPenalty());
        }
        rows.addAll(byId.values());
        rows.sort(Comparator
                .comparing((PlayerRuleRow row) -> !row.onLayout)
                .thenComparing(row -> row.name, String.CASE_INSENSITIVE_ORDER));
        if (rows.isEmpty()) {
            people.add(new JLabel("This band layout has no players yet."));
        }
        for (PlayerRuleRow row : rows) {
            people.add(row.panel);
        }

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(note, BorderLayout.NORTH);
        north.add(weights, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(people);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public static void show(
            Component parent,
            OptimizationProfileRepository profiles,
            BandRepository bands,
            PlayerRepository players,
            long bandId) throws LibraryException {
        BandOptimizerForm form = new BandOptimizerForm(profiles, bands, players, bandId);
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Optimizer rules", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            try {
                form.save();
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Optimizer rules",
                        JOptionPane.WARNING_MESSAGE);
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Optimizer rules",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    public void save() throws LibraryException {
        List<PlayerSwitchWeight> switches = new ArrayList<>();
        List<InstrumentAffinityWeight> affinities = new ArrayList<>();
        for (PlayerRuleRow row : rows) {
            String text = row.switchField.getText() == null ? "" : row.switchField.getText().strip();
            if (!text.isEmpty()) {
                switches.add(new PlayerSwitchWeight(row.playerId, parseWeight(text, row.name)));
            }
            for (Map.Entry<Long, Double> affinity : row.affinities.entrySet()) {
                affinities.add(new InstrumentAffinityWeight(row.playerId, affinity.getKey(), affinity.getValue()));
            }
        }
        SearchBudgetPreset preset = (SearchBudgetPreset) budget.getSelectedItem();
        if (preset == null) {
            preset = SearchBudgetPreset.NORMAL;
        }
        profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                BandOptimizationProfile.CURRENT_SETTINGS_VERSION,
                number(smooth),
                number(total),
                number(role),
                number(keep),
                ((Number) target.getValue()).intValue(),
                number(defaultSwitch),
                switches,
                affinities,
                preset));
    }

    static SpinnerNumberModel weightModel(double value) {
        double max = Math.max(100.0d, value);
        return new SpinnerNumberModel(value, 0.0d, max, 0.5d);
    }

    private PlayerRuleRow row(long playerId, String name, boolean onLayout) {
        String shown = onLayout ? name : name + " (not on this layout)";
        JTextField field = new JTextField(6);
        field.getAccessibleContext().setAccessibleName("Switch multiplier for " + shown);
        JLabel switchLabel = new JLabel("Switch");
        switchLabel.setLabelFor(field);
        JButton preferred = new JButton("Preferred instruments…");
        preferred.getAccessibleContext().setAccessibleName("Preferred instruments for " + shown);
        PlayerRuleRow row = new PlayerRuleRow(playerId, shown, onLayout, field, new LinkedHashMap<>(), new JPanel());
        preferred.addActionListener(e -> editAffinities(row));
        JPanel panel = row.panel;
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.add(new JLabel(shown));
        panel.add(switchLabel);
        panel.add(field);
        panel.add(preferred);
        return row;
    }

    private void editAffinities(PlayerRuleRow row) {
        Map<Long, String> instruments = new LinkedHashMap<>();
        try {
            for (PlayerInstrumentInfo info : players.listPlayerInstruments(row.playerId)) {
                if (info.hasInstrument()) {
                    instruments.put(info.instrumentId(), displayInstrument(info.instrumentName()));
                }
            }
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Preferred instruments",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (Long instrumentId : row.affinities.keySet()) {
            instruments.computeIfAbsent(instrumentId, id -> instrumentName(id) + " (not owned)");
        }
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JTextArea note = new JTextArea(
                "A preference does not change which instruments this player owns.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        body.add(note);
        if (instruments.isEmpty()) {
            body.add(new JLabel("This player does not own any instruments."));
        }
        Map<Long, JCheckBox> checks = new LinkedHashMap<>();
        Map<Long, JSpinner> penalties = new LinkedHashMap<>();
        for (Map.Entry<Long, String> instrument : instruments.entrySet()) {
            JCheckBox check = new JCheckBox("Prefer " + instrument.getValue());
            Double current = row.affinities.get(instrument.getKey());
            check.setSelected(current != null);
            JSpinner penalty = new JSpinner(weightModel(current == null ? 1.0d : current));
            JLabel penaltyLabel = new JLabel("Miss penalty");
            penaltyLabel.setLabelFor(penalty);
            check.addActionListener(e -> penalty.setEnabled(check.isSelected()));
            penalty.setEnabled(check.isSelected());
            JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            line.add(check);
            line.add(penaltyLabel);
            line.add(penalty);
            body.add(line);
            checks.put(instrument.getKey(), check);
            penalties.put(instrument.getKey(), penalty);
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Preferred instruments", Dialog.ModalityType.APPLICATION_MODAL);
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("OK");
        cancel.addActionListener(e -> dialog.dispose());
        ok.addActionListener(e -> {
            row.affinities.clear();
            for (Map.Entry<Long, JCheckBox> entry : checks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    row.affinities.put(entry.getKey(), ((Number) penalties.get(entry.getKey()).getValue()).doubleValue());
                }
            }
            dialog.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(body);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
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

    private String instrumentName(long instrumentId) {
        try {
            for (var instrument : players.listInstruments()) {
                if (instrument.id() == instrumentId) {
                    return displayInstrument(instrument.name());
                }
            }
        } catch (LibraryException ex) {
            return "Instrument " + instrumentId;
        }
        return "Instrument " + instrumentId;
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

    private static String budgetLabel(SearchBudgetPreset preset) {
        return switch (preset) {
            case QUICK -> "Quick (2 seconds)";
            case NORMAL -> "Normal (10 seconds)";
            case THOROUGH -> "Thorough (60 seconds)";
        };
    }

    private static final class PlayerRuleRow {
        private final long playerId;
        private final String name;
        private final boolean onLayout;
        private final JTextField switchField;
        private final Map<Long, Double> affinities;
        private final JPanel panel;

        private PlayerRuleRow(
                long playerId,
                String name,
                boolean onLayout,
                JTextField switchField,
                Map<Long, Double> affinities,
                JPanel panel) {
            this.playerId = playerId;
            this.name = name;
            this.onLayout = onLayout;
            this.switchField = switchField;
            this.affinities = affinities;
            this.panel = panel;
        }
    }
}
