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
import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;
import com.aevoreth.abcmm.domain.optimizer.OptimizationProfileRepository;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;

/**
 * Band optimizer weights, search budget, switch multipliers, and instrument preferences.
 * Preferences do not change {@code PlayerInstrument}.
 */
public final class BandOptimizerForm extends JPanel {

    static final String UNIT_EXPLANATION =
            "One smooth unit is one transition at the target plus one. One total unit is one"
                + " instrument switch. One role unit is a preference penalty of 1. One keep unit is"
                + " one changed assignment. Zero turns that goal off. A preference does not change"
                + " which instruments a player owns. Use Optimizer help for examples and Instrument"
                + " placement for back-row preferences.";

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
        note.setRows(5);
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
            byId.get(weight.playerId()).affinities.put(
                    weight.instrumentId(),
                    new AffinitySetting(weight.missPenalty(), weight.required()));
        }
        for (InstrumentPlacementRule placement : placements) {
            java.util.Set<Long> ids = new java.util.LinkedHashSet<>(placement.preferredPlayerIds());
            ids.addAll(placement.avoidedPlayerIds());
            for (Long id : ids)
                byId.computeIfAbsent(id, player -> row(player, lookupName(player), false));
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
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton placement = new JButton("Instrument placement…");
        placement.addActionListener(event -> editPlacements());
        JButton help = new JButton("Optimizer help…");
        help.addActionListener(event -> OptimizerHelpDialog.show(this));
        tools.add(placement);
        tools.add(help);
        north.add(tools, BorderLayout.SOUTH);
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
            for (Map.Entry<Long, AffinitySetting> affinity : row.affinities.entrySet()) {
                affinities.add(new InstrumentAffinityWeight(
                        row.playerId,
                        affinity.getKey(),
                        affinity.getValue().missPenalty(),
                        affinity.getValue().required()));
            }
        }
        SearchBudgetPreset preset = (SearchBudgetPreset) budget.getSelectedItem();
        if (preset == null) {
            preset = SearchBudgetPreset.NORMAL;
        }
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
        field.getAccessibleContext().setAccessibleName("Switch multiplier for " + shown);
        JLabel switchLabel = new JLabel("Switch");
        switchLabel.setLabelFor(field);
        JButton preferred = new JButton("Instrument preferences…");
        preferred.getAccessibleContext().setAccessibleName("Instrument preferences for " + shown);
        PlayerRuleRow row = new PlayerRuleRow(
                playerId, shown, onLayout, field, new LinkedHashMap<>(), new JPanel());
        preferred.addActionListener(e -> editAffinities(row));
        JPanel panel = row.panel;
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.add(new JLabel(shown));
        panel.add(switchLabel);
        panel.add(field);
        panel.add(preferred);
        panel.setMaximumSize(
                new java.awt.Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return row;
    }

    private void editPlacements() {
        try {
            Map<Long, String> instruments = new LinkedHashMap<>();
            for (var instrument : players.listInstruments())
                instruments.put(instrument.id(), displayInstrument(instrument.name()));
            Map<Long, String> names = new LinkedHashMap<>();
            for (PlayerRuleRow row : rows) names.put(row.playerId, row.name);
            InstrumentPlacementForm form =
                    new InstrumentPlacementForm(instruments, names, placements);
            if (JOptionPane.showConfirmDialog(
                            this,
                            form,
                            "Instrument placement",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.PLAIN_MESSAGE)
                    == JOptionPane.OK_OPTION) {
                placements.clear();
                placements.addAll(form.values());
            }
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(
                    this, ex.getMessage(), "Instrument placement", JOptionPane.ERROR_MESSAGE);
        }
    }

    private InstrumentPlacementRule placement(long instrumentId) {
        return placements.stream()
                .filter(rule -> rule.instrumentId() == instrumentId)
                .findFirst()
                .orElse(null);
    }

    private void setAvoidance(long playerId, long instrumentId, boolean avoided, double penalty) {
        InstrumentPlacementRule existing = placement(instrumentId);
        List<Long> preferred =
                new ArrayList<>(existing == null ? List.of() : existing.preferredPlayerIds());
        List<Long> avoid =
                new ArrayList<>(existing == null ? List.of() : existing.avoidedPlayerIds());
        avoid.remove(playerId);
        if (avoided) {
            avoid.add(playerId);
            preferred.remove(playerId);
        }
        placements.removeIf(rule -> rule.instrumentId() == instrumentId);
        if (!preferred.isEmpty() || !avoid.isEmpty())
            placements.add(
                    new InstrumentPlacementRule(
                            instrumentId,
                            preferred,
                            avoid,
                            avoided ? penalty : existing.missPenalty()));
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
            JOptionPane.showMessageDialog(
                    this, ex.getMessage(), "Instrument preferences", JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (InstrumentPlacementRule placement : placements) {
            if (placement.avoidedPlayerIds().contains(row.playerId)) {
                instruments.putIfAbsent(
                        placement.instrumentId(), instrumentName(placement.instrumentId()));
            }
        }
        for (Long instrumentId : row.affinities.keySet()) {
            instruments.computeIfAbsent(instrumentId, id -> instrumentName(id) + " (not owned)");
        }
        Map<Long, InstrumentAffinityWeight> affinities = new LinkedHashMap<>();
        row.affinities.forEach(
                (id, value) ->
                        affinities.put(
                                id,
                                new InstrumentAffinityWeight(
                                        row.playerId, id, value.missPenalty(), value.required())));
        Map<Long, Double> avoided = new LinkedHashMap<>();
        for (InstrumentPlacementRule placement : placements) {
            if (placement.avoidedPlayerIds().contains(row.playerId)) {
                avoided.put(placement.instrumentId(), placement.missPenalty());
            }
        }
        CharacterInstrumentForm form =
                new CharacterInstrumentForm(instruments, affinities, avoided);
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog =
                new JDialog(
                        owner,
                        "Instrument preferences — " + row.name,
                        Dialog.ModalityType.APPLICATION_MODAL);
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("OK");
        JButton help = new JButton("Optimizer help…");
        cancel.addActionListener(event -> dialog.dispose());
        help.addActionListener(event -> OptimizerHelpDialog.show(dialog));
        ok.addActionListener(
                event -> {
                    try {
                        List<InstrumentAffinityWeight> edited = form.affinities(row.playerId);
                        Map<Long, Double> nextAvoided = form.avoided();
                        row.affinities.clear();
                        for (Long id : instruments.keySet()) {
                            setAvoidance(
                                    row.playerId,
                                    id,
                                    nextAvoided.containsKey(id),
                                    nextAvoided.getOrDefault(id, 0.0));
                        }
                        for (InstrumentAffinityWeight affinity : edited) {
                            row.affinities.put(
                                    affinity.instrumentId(),
                                    new AffinitySetting(
                                            affinity.missPenalty(), affinity.required()));
                        }
                        dialog.dispose();
                    } catch (IllegalArgumentException ex) {
                        JOptionPane.showMessageDialog(
                                dialog,
                                ex.getMessage(),
                                "Instrument preferences",
                                JOptionPane.WARNING_MESSAGE);
                    }
                });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(help);
        buttons.add(cancel);
        buttons.add(ok);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
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

    private record AffinitySetting(double missPenalty, boolean required) {
    }

    private static final class PlayerRuleRow {
        private final long playerId;
        private final String name;
        private final boolean onLayout;
        private final JTextField switchField;
        private final Map<Long, AffinitySetting> affinities;
        private final JPanel panel;

        private PlayerRuleRow(
                long playerId,
                String name,
                boolean onLayout,
                JTextField switchField,
                Map<Long, AffinitySetting> affinities,
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
