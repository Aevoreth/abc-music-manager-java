package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.aevoreth.abcmm.domain.optimizer.*;
import com.aevoreth.abcmm.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

class InstrumentPlacementUiTest {
    @TempDir Path directory;

    @Test
    void instrumentEditorKeepsSeparateChoicesWhenChangingInstruments() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    var instruments = new LinkedHashMap<Long, String>();
                    instruments.put(1L, "Bardic fiddle");
                    instruments.put(2L, "Bassoon");
                    var players = new LinkedHashMap<Long, String>();
                    players.put(10L, "Back row");
                    players.put(11L, "Front row");
                    var form = new InstrumentPlacementForm(instruments, players, List.of());
                    var table = find(form, JTable.class);
                    table.setValueAt("Prefer", 0, 1);
                    table.setValueAt("Avoid", 1, 1);
                    var chooser = find(form, JComboBox.class);
                    chooser.setSelectedIndex(1);
                    table.setValueAt("Prefer", 0, 1);
                    var rules = form.values();
                    assertEquals(List.of(10L), rules.get(0).preferredPlayerIds());
                    assertEquals(List.of(11L), rules.get(0).avoidedPlayerIds());
                    assertEquals(10, rules.get(0).missPenalty());
                    assertEquals(2, rules.get(1).instrumentId());
                    chooser.setSelectedIndex(0);
                    assertEquals("Avoid", table.getValueAt(1, 1));
                });
    }

    @Test
    void savingBandWeightsPreservesPlacementRules() throws Exception {
        try (var db = SqliteDatabase.openOrCreate(directory.resolve("form.sqlite"))) {
            var bands = new SqliteBandRepository(db);
            var players = new SqlitePlayerRepository(db);
            var profiles = new SqliteOptimizationProfileRepository(db);
            long band = bands.addBand("Band", null),
                    back = players.addPlayer("Back", null, null),
                    front = players.addPlayer("Front", null, null);
            var placement =
                    new InstrumentPlacementRule(
                            players.listInstruments().get(0).id(),
                            List.of(back),
                            List.of(front),
                            10);
            profiles.saveBandProfile(
                    band,
                    new BandOptimizationProfile(
                            1,
                            4,
                            1,
                            1,
                            1,
                            2,
                            1,
                            List.of(),
                            List.of(),
                            SearchBudgetPreset.NORMAL,
                            List.of(placement)));
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            new BandOptimizerForm(profiles, bands, players, band).save();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
            assertEquals(List.of(placement), profiles.getBandProfile(band).instrumentPlacements());
        }
    }

    @Test
    void characterModesAreMutuallyExclusiveAndValidateNumbers() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    var form =
                            new CharacterInstrumentForm(
                                    Map.of(1L, "Bardic fiddle"),
                                    Map.of(1L, new InstrumentAffinityWeight(10, 1, 1, true)),
                                    Map.of());
                    var table = find(form, JTable.class);
                    table.setValueAt(CharacterInstrumentForm.AVOID, 0, 1);
                    assertTrue(form.affinities(10).isEmpty());
                    assertEquals(10.0, form.avoided().get(1L));
                    table.setValueAt(CharacterInstrumentForm.PREFER, 0, 1);
                    assertTrue(form.avoided().isEmpty());
                    assertFalse(form.affinities(10).get(0).required());
                    table.setValueAt(CharacterInstrumentForm.ALWAYS, 0, 1);
                    assertTrue(form.affinities(10).get(0).required());
                    table.setValueAt(-1.0, 0, 2);
                    assertThrows(IllegalArgumentException.class, form::avoided);
                });
    }

    private static <T> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findOrNull(child, type);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing " + type);
    }

    private static <T> T findOrNull(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container)
            for (Component child : container.getComponents()) {
                T found = findOrNull(child, type);
                if (found != null) return found;
            }
        return null;
    }
}
