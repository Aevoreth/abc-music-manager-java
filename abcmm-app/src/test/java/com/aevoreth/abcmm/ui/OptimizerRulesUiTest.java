package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.storage.SqliteBandRepository;
import com.aevoreth.abcmm.storage.SqliteDatabase;
import com.aevoreth.abcmm.storage.SqliteOptimizationProfileRepository;
import com.aevoreth.abcmm.storage.SqlitePlayerRepository;

class OptimizerRulesUiTest {

    @TempDir
    Path tempDir;

    @Test
    void bandFormShowsUnitsAndDoesNotClampALoadedWeight() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("rules.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long playerId = players.addPlayer("Ada", null, null);
            bands.setSlot(layoutId, playerId, 0, 0, 7, 5);
            profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                    1, 150.0d, 1.0d, 1.0d, 1.0d, 2, 1.0d, List.of(), List.of(), SearchBudgetPreset.QUICK));

            BandOptimizerForm form = new BandOptimizerForm(profiles, bands, players, bandId);
            assertTrue(BandOptimizerForm.UNIT_EXPLANATION.contains(
                    "does not change which instruments a player owns"));
            SpinnerNumberModel model = BandOptimizerForm.weightModel(150.0d);
            assertEquals(150.0d, ((Number) model.getValue()).doubleValue());
            assertEquals(150.0d, ((Number) model.getMaximum()).doubleValue());

            List<Component> components = new ArrayList<>();
            collect(form, components);
            assertTrue(components.stream().anyMatch(component ->
                    component instanceof JTextArea area
                            && area.getText().contains("One smooth unit is one transition")));
            assertTrue(components.stream().anyMatch(component ->
                    component instanceof JLabel label && "Ada".equals(label.getText())));
            assertTrue(components.stream().anyMatch(component ->
                    component instanceof JSpinner spinner
                            && spinner.getValue() instanceof Number number
                            && number.doubleValue() == 150.0d));
            assertTrue(components.stream().anyMatch(component ->
                    component instanceof JLabel label && label.getLabelFor() instanceof JSpinner));
        }
    }

    @Test
    void assignmentCanvasBindsArrowsAndPinTextNamesTheConflict() {
        SetlistBandAssignmentPanel panel = new SetlistBandAssignmentPanel();
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        assertNotNull(panel.assignmentKeyMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)));

        String tooltip = PinMessages.tooltip(
                "Earlier part 2",
                true,
                List.of(PinMessages.conflict("Ada", "Bea")));
        assertTrue(tooltip.contains("PIN"));
        assertTrue(tooltip.contains("Pinned to Ada"));
        assertTrue(tooltip.contains("Current assignment is Bea"));
    }

    private static void collect(Component component, List<Component> components) {
        components.add(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, components);
            }
        }
    }
}
