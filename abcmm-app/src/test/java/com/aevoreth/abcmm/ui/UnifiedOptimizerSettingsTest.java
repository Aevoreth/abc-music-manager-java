package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aevoreth.abcmm.domain.optimizer.*;
import com.aevoreth.abcmm.optimizer.*;
import com.aevoreth.abcmm.storage.*;

class UnifiedOptimizerSettingsTest {
    @TempDir Path directory;

    @Test void ruleTooltipsFollowReorderedColumnsAndRemainAvailableWhileEditing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var form = new InstrumentRulesForm(Map.of(1L, "Fiddle"), Map.of(10L, "Ada"),
                    List.of(new InstrumentAffinityWeight(10, 1, 7, true)), List.of());
            var strength = find(form, JSpinner.class);
            var strengthEditor = (JSpinner.DefaultEditor) strength.getEditor();
            assertTrue(strengthEditor.getTextField().getToolTipText().contains("Shared cost"));
            JTable table = find(form, JTable.class);
            table.setSize(900, 200); table.doLayout();
            table.moveColumn(3, 1);
            var rect = table.getCellRect(0, 1, true);
            var event = new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_MOVED,
                    0, 0, rect.x + 2, rect.y + 2, 0, false);
            assertTrue(table.getToolTipText(event).contains("higher priority"));
            var header = table.getTableHeader();
            var headerEvent = new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_MOVED,
                    0, 0, rect.x + 2, 2, 0, false);
            assertTrue(header.getToolTipText(headerEvent).contains("higher priority"));
            assertTrue(table.editCellAt(0, 1));
            assertTrue(((JComponent) table.getEditorComponent()).getToolTipText().contains("higher priority"));
            table.getCellEditor().cancelCellEditing();
        });
    }

    @Test void rolesAndPlacementsRoundTripWithoutConflatingStrengthAndPriority() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var instruments = new LinkedHashMap<Long, String>(); instruments.put(1L, "Fiddle"); instruments.put(2L, "Harp");
            var names = new LinkedHashMap<Long, String>(); names.put(10L, "Ada"); names.put(11L, "Bea");
            var form = new InstrumentRulesForm(instruments, names,
                    List.of(new InstrumentAffinityWeight(10, 1, 7, true)),
                    List.of(new InstrumentPlacementRule(1, List.of(10L), List.of(11L), 13)));
            JTable table = find(form, JTable.class);
            assertEquals(7.0, table.getValueAt(0, 3));
            assertFalse(table.isCellEditable(0, 2)); assertTrue(table.isCellEditable(0, 3));
            table.setValueAt("Prefer", 0, 1); table.setValueAt(3.0, 0, 2);
            find(form, JComboBox.class).setSelectedIndex(1);
            assertEquals(List.of(new InstrumentAffinityWeight(10, 1, 3, false)), form.affinities());
            assertEquals(List.of(new InstrumentPlacementRule(1, List.of(10L), List.of(11L), 13)), form.placements());
            find(form, JComboBox.class).setSelectedIndex(0);
            assertEquals("Avoid", table.getValueAt(1, 4));
            table.setValueAt(-1.0, 0, 2);
            assertThrows(IllegalArgumentException.class, form::affinities);
        });
    }

    @Test void bandSettingsPreserveLegacyWeightsAndOfferOnlyOneRulesEditor() throws Exception {
        try (var db = SqliteDatabase.openOrCreate(directory.resolve("band.sqlite"))) {
            var bands = new SqliteBandRepository(db); var players = new SqlitePlayerRepository(db);
            var profiles = new SqliteOptimizationProfileRepository(db);
            long band = bands.addBand("Band", null), player = players.addPlayer("Ada", null, null);
            long instrument = players.listInstruments().getFirst().id();
            var profile = new BandOptimizationProfile(1, 150, 2, 0, 3, 4, 5,
                    List.of(new PlayerSwitchWeight(player, 9)), List.of(new InstrumentAffinityWeight(player, instrument, 6, true)),
                    SearchBudgetPreset.THOROUGH, List.of(new InstrumentPlacementRule(instrument, List.of(), List.of(player), 12)));
            profiles.saveBandProfile(band, profile);
            var form = new BandOptimizerForm(profiles, bands, players, band);
            assertEquals(3, find(form, JTabbedPane.class).getTabCount());
            assertEquals("Goals", find(form, JTabbedPane.class).getTitleAt(0));
            form.save();
            assertEquals(profile, profiles.getBandProfile(band));
            if (System.getProperty("optimizer.snapshot") != null) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    try {
                        var tabs = find(form, JTabbedPane.class);
                        snapshot(form, "goals");
                        tabs.setSelectedIndex(1); snapshot(form, "rules");
                    } catch (Exception ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }

    static void snapshot(JComponent component, String name) throws Exception {
        component.setSize(980, 560); layout(component);
        var image = new java.awt.image.BufferedImage(980, 560, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
        java.nio.file.Files.createDirectories(Path.of("target", "optimizer-preview"));
        javax.imageio.ImageIO.write(image, "png", Path.of("target", "optimizer-preview", name + ".png").toFile());
    }
    private static void layout(Container component) {
        component.doLayout(); for (Component child : component.getComponents()) if (child instanceof Container c) layout(c);
    }

    static <T> T find(Component root, Class<T> type) {
        T result = findOrNull(root, type); if (result == null) throw new AssertionError("Missing " + type); return result;
    }
    private static <T> T findOrNull(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container c) for (Component child : c.getComponents()) {
            T found = findOrNull(child, type); if (found != null) return found;
        }
        return null;
    }
}
