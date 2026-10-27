package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.aevoreth.abcmm.domain.optimizer.*;
import com.aevoreth.abcmm.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import javax.swing.SwingUtilities;

class InstrumentPlacementUiTest {
    @TempDir Path directory;

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

}
