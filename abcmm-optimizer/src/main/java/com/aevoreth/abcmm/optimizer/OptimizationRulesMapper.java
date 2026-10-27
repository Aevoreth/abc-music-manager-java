package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.PartKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;

/**
 * Builds {@link OptimizationRules} from a saved band profile and one setlist's
 * occurrence preferences. Hard pins are mapped separately by {@link #toPins}
 * and are not added to the soft rules.
 */
public final class OptimizationRulesMapper {

    private OptimizationRulesMapper() {
    }

    public static OptimizationRules toRules(
            BandOptimizationProfile profile,
            OccurrencePreferences preferences) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");
        Map<Long, Double> switches = new LinkedHashMap<>();
        for (PlayerSwitchWeight weight : profile.playerSwitchWeights()) {
            switches.put(weight.playerId(), weight.multiplier());
        }
        List<InstrumentAffinity> affinities = new ArrayList<>();
        for (InstrumentAffinityWeight weight : profile.instrumentAffinities()) {
            affinities.add(new InstrumentAffinity(
                    weight.playerId(),
                    weight.instrumentId(),
                    weight.missPenalty(),
                    weight.required()));
        }
        List<PartPreference> parts = new ArrayList<>();
        for (PartPreferenceWeight weight : preferences.partPreferences()) {
            parts.add(new PartPreference(
                    weight.setlistItemId(),
                    weight.partNumber(),
                    weight.playerId(),
                    weight.penalty()));
        }
        Map<Long, Double> itemKeep = new LinkedHashMap<>();
        for (ItemKeepWeight weight : preferences.itemKeepWeights()) {
            itemKeep.put(weight.setlistItemId(), weight.multiplier());
        }
        Map<OccurrencePart, Double> partKeep = new LinkedHashMap<>();
        for (PartKeepWeight weight : preferences.partKeepWeights()) {
            partKeep.put(new OccurrencePart(weight.setlistItemId(), weight.partNumber()), weight.multiplier());
        }
        return new OptimizationRules(
                profile.smoothWeight(),
                profile.totalWeight(),
                profile.roleWeight(),
                profile.keepWeight(),
                profile.targetChanges(),
                profile.defaultSwitchMultiplier(),
                switches,
                affinities,
                parts,
                itemKeep,
                partKeep);
    }

    public static List<Pin> toPins(List<AssignmentPin> pins) {
        Objects.requireNonNull(pins, "pins");
        List<Pin> copy = new ArrayList<>();
        for (AssignmentPin pin : pins) {
            copy.add(new Pin(pin.setlistItemId(), pin.partNumber(), pin.playerId()));
        }
        return List.copyOf(copy);
    }
}
