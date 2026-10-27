package com.aevoreth.abcmm.ui;

import java.awt.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.*;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.optimizer.*;

/** Stages occurrence-specific exceptions until the settings window is saved. */
final class SongExceptionsForm extends JPanel {
    private record Song(long id, String title) { public String toString() { return title; } }
    private record Character(long id, String name) { public String toString() { return name; } }
    private final OptimizationProfileRepository profiles;
    private final OptimizationProblem problem;
    private final JComboBox<Song> songs = new JComboBox<>();
    private final JTextField songKeep = new JTextField(6);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Part", "Assignment", "Character", "Preference strength", "Part preservation override"}, 0) {
        public boolean isCellEditable(int row, int col) {
            return col == 1 || col == 4 || col == 2 && !"Automatic".equals(getValueAt(row, 1)) || col == 3 && "Prefer character".equals(getValueAt(row, 1));
        }
    };
    private final JTable table = OptimizerTooltips.table(model,
            "The ABC part number for this song occurrence.",
            "Automatic uses band rules. Prefer character is a soft preference. Pin to character requires that assignment.",
            "The preferred or pinned character for this part. Instrument ownership still applies.",
            "Soft cost for giving this part to another character. Replaces band role and placement penalties for this part.",
            "Overrides this part's preservation cost. Blank inherits the song multiplier, then 1; zero removes the preservation cost.");
    private final Map<Long, Character> characters = new LinkedHashMap<>();
    private final List<PartPreferenceWeight> preferences = new ArrayList<>();
    private final List<ItemKeepWeight> items = new ArrayList<>();
    private final List<PartKeepWeight> parts = new ArrayList<>();
    private final List<AssignmentPin> pins = new ArrayList<>();
    private final Set<Long> layoutPlayers = new HashSet<>();
    private final JLabel layoutWarning = new JLabel();
    private final JButton clearOffLayout = new JButton("Clear rules for characters outside this layout");
    private Long selected;
    private boolean switching;

    SongExceptionsForm(OptimizationProfileRepository profiles, OptimizationProblem problem,
            List<SetlistItemInfo> titles, Map<Long, String> names) throws LibraryException {
        super(new BorderLayout(8, 8)); this.profiles = profiles; this.problem = problem;
        var existing = profiles.listOccurrencePreferences(problem.setlistId());
        preferences.addAll(existing.partPreferences()); items.addAll(existing.itemKeepWeights()); parts.addAll(existing.partKeepWeights());
        pins.addAll(profiles.listPins(problem.setlistId()));
        problem.performers().forEach(p -> layoutPlayers.add(p.playerId()));
        problem.performers().forEach(p -> characters.put(p.playerId(), new Character(p.playerId(), names.getOrDefault(p.playerId(), "Character " + p.playerId()))));
        preferences.forEach(p -> characters.putIfAbsent(p.playerId(), new Character(p.playerId(), names.getOrDefault(p.playerId(), "Character " + p.playerId()) + " (off layout)")));
        pins.forEach(p -> characters.putIfAbsent(p.playerId(), new Character(p.playerId(), names.getOrDefault(p.playerId(), "Character " + p.playerId()) + " (off layout)")));
        for (var occurrence : problem.occurrences()) {
            String title = titles.stream().filter(t -> t.id() == occurrence.setlistItemId()).map(SetlistItemInfo::songTitle).findFirst().orElse("Song");
            songs.addItem(new Song(occurrence.setlistItemId(), (occurrence.position() + 1) + ". " + title));
        }
        songs.setToolTipText("Choose a song occurrence. Repeated appearances of the same song have independent exceptions.");
        clearOffLayout.setToolTipText("Stage removal of preferences and pins naming characters outside this band. Save commits the cleanup; Cancel discards it.");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(new JLabel("Song occurrence")); top.add(songs);
        JLabel label = new JLabel("Song preservation multiplier"); label.setLabelFor(songKeep); top.add(label); top.add(songKeep);
        label.setToolTipText("Multiply the cost of changing assignments in this song. Blank inherits 1; zero removes this preservation cost.");
        songKeep.setToolTipText("Blank inherits 1. Zero removes this preservation cost. Pins stay fixed.");
        JPanel header = new JPanel(new BorderLayout()); header.add(top, BorderLayout.NORTH);
        JPanel warnings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        warnings.add(layoutWarning); warnings.add(clearOffLayout);
        header.add(warnings, BorderLayout.CENTER);
        clearOffLayout.addActionListener(e -> {
            try { clearOffLayoutRules(); }
            catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Song exceptions", JOptionPane.WARNING_MESSAGE);
            }
        });
        header.add(new JLabel("Blank overrides inherit: part → song → 1. Repeated songs have separate exceptions."), BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Automatic", "Prefer character", "Pin to character"})));
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(characters.values().toArray(Character[]::new))));
        table.setRowHeight(26); table.putClientProperty("terminateEditOnFocusLost", true);
        TableColumn keepColumn = table.getColumnModel().getColumn(4); table.removeColumn(keepColumn);
        JCheckBox advanced = new JCheckBox("Advanced part preservation overrides");
        advanced.setToolTipText("Show preservation overrides for individual parts; blank values inherit this song's multiplier.");
        advanced.addActionListener(e -> { if (advanced.isSelected()) table.addColumn(keepColumn); else table.removeColumn(keepColumn); });
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader());
        add(scroll, BorderLayout.CENTER); add(advanced, BorderLayout.SOUTH);
        songs.addActionListener(e -> {
            if (switching) return;
            try { remember(); load(); }
            catch (IllegalArgumentException ex) {
                switching = true;
                for (int i = 0; i < songs.getItemCount(); i++) if (Objects.equals(songs.getItemAt(i).id(), selected)) songs.setSelectedIndex(i);
                switching = false;
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Song exceptions", JOptionPane.WARNING_MESSAGE);
            }
        });
        load();
        updateLayoutWarning();
        if (problem.locked()) {
            songKeep.setEditable(false); table.setEnabled(false); advanced.setEnabled(false);
            header.add(new JLabel("This set is locked. Unlock it to edit song exceptions."), BorderLayout.CENTER);
        }
    }
    private void load() {
        Song song = (Song) songs.getSelectedItem(); selected = song == null ? null : song.id(); model.setRowCount(0);
        songKeep.setText(""); if (selected == null) return;
        items.stream().filter(w -> w.setlistItemId() == selected).findFirst().ifPresent(w -> songKeep.setText(Double.toString(w.multiplier())));
        var occurrence = problem.occurrences().stream().filter(o -> o.setlistItemId() == selected).findFirst().orElseThrow();
        for (var part : occurrence.parts()) {
            var preference = preferences.stream().filter(w -> w.setlistItemId() == selected && w.partNumber() == part.partNumber()).findFirst().orElse(null);
            var pin = pins.stream().filter(w -> w.setlistItemId() == selected && w.partNumber() == part.partNumber()).findFirst().orElse(null);
            var keep = parts.stream().filter(w -> w.setlistItemId() == selected && w.partNumber() == part.partNumber()).findFirst().orElse(null);
            Long player = null;
            if (pin != null) player = pin.playerId();
            else if (preference != null) player = preference.playerId();
            if (player == null) for (var baseline : problem.baseline())
                if (baseline.setlistItemId() == selected && Objects.equals(baseline.partNumber(), part.partNumber())) { player = baseline.playerId(); break; }
            model.addRow(new Object[]{part.partNumber(), pin != null ? "Pin to character" : preference != null ? "Prefer character" : "Automatic",
                    player == null ? null : characters.get(player), preference == null ? "1" : Double.toString(preference.penalty()), keep == null ? "" : Double.toString(keep.multiplier())});
        }
    }

    /** Explicitly stages removal of references that cannot be satisfied by this band. */
    void clearOffLayoutRules() {
        if (problem.locked()) return;
        remember();
        preferences.removeIf(p -> !layoutPlayers.contains(p.playerId()));
        pins.removeIf(p -> !layoutPlayers.contains(p.playerId()));
        load();
        updateLayoutWarning();
    }

    private void updateLayoutWarning() {
        long count = preferences.stream().filter(p -> !layoutPlayers.contains(p.playerId())).count()
                + pins.stream().filter(p -> !layoutPlayers.contains(p.playerId())).count();
        layoutWarning.setText(count == 0 ? "" : count + " rules refer to characters outside this band layout and block search.");
        layoutWarning.setVisible(count > 0);
        clearOffLayout.setVisible(count > 0);
        clearOffLayout.setEnabled(!problem.locked());
    }
    void selectPart(long itemId, Integer part) {
        for (int i = 0; i < songs.getItemCount(); i++) if (songs.getItemAt(i).id() == itemId) songs.setSelectedIndex(i);
        if (part != null) for (int row = 0; row < model.getRowCount(); row++)
            if (((Number) model.getValueAt(row, 0)).intValue() == part) table.setRowSelectionInterval(row, row);
    }

    private static Double optional(Object value) {
        String text = value == null ? "" : value.toString().strip(); if (text.isEmpty()) return null;
        try { double n = Double.parseDouble(text); if (Double.isFinite(n) && n >= 0) return n; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Strengths and preservation multipliers must be finite, nonnegative numbers.");
    }
    private void remember() {
        if (selected == null || problem.locked()) return;
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) throw new IllegalArgumentException("Finish editing the selected part.");
        Double itemKeep = optional(songKeep.getText());
        List<PartPreferenceWeight> nextPreferences = new ArrayList<>(); List<AssignmentPin> nextPins = new ArrayList<>(); List<PartKeepWeight> nextParts = new ArrayList<>();
        var occurrence = problem.occurrences().stream().filter(o -> o.setlistItemId() == selected).findFirst().orElseThrow();
        for (int row = 0; row < model.getRowCount(); row++) {
            int part = ((Number) model.getValueAt(row, 0)).intValue(); String mode = (String) model.getValueAt(row, 1);
            Character character = (Character) model.getValueAt(row, 2);
            Double keep = optional(model.getValueAt(row, 4)); if (keep != null) nextParts.add(new PartKeepWeight(selected, part, keep));
            if ("Automatic".equals(mode)) continue;
            if (character == null) throw new IllegalArgumentException("Choose a character for part " + part + ".");
            if ("Prefer character".equals(mode)) {
                Double strength = optional(model.getValueAt(row, 3));
                if (strength == null) throw new IllegalArgumentException("Enter a preference strength for part " + part + ".");
                nextPreferences.add(new PartPreferenceWeight(selected, part, character.id(), strength));
            } else {
                Long instrument = occurrence.parts().stream().filter(p -> p.partNumber() == part).findFirst().orElseThrow().instrumentId();
                boolean owned = instrument != null && problem.performers().stream().anyMatch(p -> p.playerId() == character.id() && PhysicalInstrumentEquivalence.owns(p.ownedInstrumentIds(), instrument, problem.equivalentInstrumentIds()));
                boolean unchanged = pins.stream().anyMatch(p -> p.setlistItemId() == selected && p.partNumber() == part && p.playerId() == character.id());
                if (!owned && !unchanged) throw new IllegalArgumentException(character.name() + " does not own the instrument for part " + part + ".");
                nextPins.add(new AssignmentPin(selected, part, character.id()));
                // Keep legacy combinations on an unchanged pin, but an edited pin replaces
                // the combined assignment setting, including its hidden soft preference.
                if (unchanged) preferences.stream().filter(p -> p.setlistItemId() == selected && p.partNumber() == part).forEach(nextPreferences::add);
            }
        }
        for (AssignmentPin pin : nextPins) {
            boolean duplicate = nextPins.stream().filter(p -> p.playerId() == pin.playerId()).count() > 1;
            boolean changed = nextPins.stream().filter(p -> p.playerId() == pin.playerId()).anyMatch(p -> !pins.contains(p));
            if (duplicate && changed) throw new IllegalArgumentException(characters.get(pin.playerId()).name() + " cannot be pinned to two parts of the same song.");
        }
        Set<Integer> visibleParts = new HashSet<>(); occurrence.parts().forEach(p -> visibleParts.add(p.partNumber()));
        preferences.removeIf(p -> p.setlistItemId() == selected && visibleParts.contains(p.partNumber())); preferences.addAll(nextPreferences);
        pins.removeIf(p -> p.setlistItemId() == selected && visibleParts.contains(p.partNumber())); pins.addAll(nextPins);
        parts.removeIf(p -> p.setlistItemId() == selected && visibleParts.contains(p.partNumber())); parts.addAll(nextParts);
        items.removeIf(p -> p.setlistItemId() == selected); if (itemKeep != null) items.add(new ItemKeepWeight(selected, itemKeep));
    }
    void validateEdits() { remember(); }
    void save() throws LibraryException {
        remember(); if (problem.locked()) return;
        profiles.replaceOccurrencePreferences(problem.setlistId(), new OccurrencePreferences(preferences, items, parts));
        profiles.replacePins(problem.setlistId(), pins);
    }
}
