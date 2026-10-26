package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.PartKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;

class OptimizationRulesMapperTest {

    @Test
    void mapsScopedPreferencesWithoutAddingPins() {
        BandOptimizationProfile profile = new BandOptimizationProfile(
                1,
                4.0d,
                0.0d,
                2.0d,
                3.0d,
                2,
                1.5d,
                List.of(new PlayerSwitchWeight(7, 4.0d)),
                List.of(new InstrumentAffinityWeight(7, 3, 2.5d)));
        OccurrencePreferences preferences = new OccurrencePreferences(
                List.of(new PartPreferenceWeight(11, 2, 7, 6.0d)),
                List.of(new ItemKeepWeight(11, 0.5d)),
                List.of(new PartKeepWeight(11, 2, 8.0d)));

        OptimizationRules rules = OptimizationRulesMapper.toRules(profile, preferences);

        assertEquals(new OptimizationRules(
                4.0d,
                0.0d,
                2.0d,
                3.0d,
                2,
                1.5d,
                Map.of(7L, 4.0d),
                List.of(new InstrumentAffinity(7, 3, 2.5d)),
                List.of(new PartPreference(11, 2, 7, 6.0d)),
                Map.of(11L, 0.5d),
                Map.of(new OccurrencePart(11, 2), 8.0d)), rules);
        assertEquals(1.0d, OptimizationRules.defaults().smoothWeight());
        assertNotEquals(OptimizationRules.defaults(), OptimizationRulesMapper.toRules(
                BandOptimizationProfile.defaults(), OccurrencePreferences.empty()));
    }
}
