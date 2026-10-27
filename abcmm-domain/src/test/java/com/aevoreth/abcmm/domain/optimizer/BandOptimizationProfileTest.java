package com.aevoreth.abcmm.domain.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class BandOptimizationProfileTest {

    @Test
    void defaultsEmphasizeSmoothTransitions() {
        BandOptimizationProfile defaults = BandOptimizationProfile.defaults();
        assertEquals(1, defaults.settingsVersion());
        assertEquals(4.0d, defaults.smoothWeight());
        assertEquals(1.0d, defaults.totalWeight());
        assertEquals(1.0d, defaults.roleWeight());
        assertEquals(1.0d, defaults.keepWeight());
        assertEquals(2, defaults.targetChanges());
        assertEquals(1.0d, defaults.defaultSwitchMultiplier());
        assertEquals(List.of(), defaults.playerSwitchWeights());
        assertEquals(List.of(), defaults.instrumentAffinities());
        assertEquals(SearchBudgetPreset.NORMAL, defaults.searchBudget());
    }

    @Test
    void zeroWeightsAreAllowedAndNegativeWeightsAreRejected() {
        BandOptimizationProfile zeros = new BandOptimizationProfile(
                1, 0.0d, 0.0d, 0.0d, 0.0d, 0, 0.0d, List.of(), List.of(), SearchBudgetPreset.NORMAL);
        assertEquals(0.0d, zeros.smoothWeight());
        assertThrows(IllegalArgumentException.class, () -> new BandOptimizationProfile(
                1, -1.0d, 1.0d, 1.0d, 1.0d, 2, 1.0d, List.of(), List.of(), SearchBudgetPreset.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new BandOptimizationProfile(
                99, 4.0d, 1.0d, 1.0d, 1.0d, 2, 1.0d, List.of(), List.of(), SearchBudgetPreset.NORMAL));
    }

    @Test
    void duplicatePlayerRowsAreRejectedAndListsAreSorted() {
        assertThrows(IllegalArgumentException.class, () -> new BandOptimizationProfile(
                1,
                4.0d,
                1.0d,
                1.0d,
                1.0d,
                2,
                1.0d,
                List.of(new PlayerSwitchWeight(2, 1.0d), new PlayerSwitchWeight(2, 3.0d)),
                List.of(),
                SearchBudgetPreset.NORMAL));
        BandOptimizationProfile profile = new BandOptimizationProfile(
                1,
                4.0d,
                1.0d,
                1.0d,
                1.0d,
                2,
                1.0d,
                List.of(new PlayerSwitchWeight(8, 2.0d), new PlayerSwitchWeight(3, 0.0d)),
                List.of(
                        new InstrumentAffinityWeight(4, 9, 1.0d),
                        new InstrumentAffinityWeight(4, 2, 5.0d)),
                SearchBudgetPreset.QUICK);
        assertEquals(3L, profile.playerSwitchWeights().get(0).playerId());
        assertEquals(2L, profile.instrumentAffinities().get(0).instrumentId());
        assertThrows(IllegalArgumentException.class, () -> new OccurrencePreferences(
                List.of(
                        new PartPreferenceWeight(1, 1, 1, 1.0d),
                        new PartPreferenceWeight(1, 1, 2, 2.0d)),
                List.of(),
                List.of()));
    }
}
