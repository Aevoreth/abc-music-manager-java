package com.aevoreth.abcmm.optimizer;

import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable soft objective for one scoring run. Weights and penalties are raw
 * units, not fractions of a hidden scale, and each weight may be zero.
 *
 * <p>The weighted score is
 * {@code smooth * overload + total * switchBurden + roles * roleDeviation + keep * keepDeviation}.
 * Overload is {@code Σ max(0, C_t - target)^2}. One smooth unit is one transition
 * at {@code target + 1}. One total unit is one penalized switch by a player whose
 * multiplier is 1. One role unit is a miss penalty of 1. One keep unit is one
 * changed player slot at multiplier 1.
 *
 * <p>Equal default weights are not percentages. Overload is squared, so a spike
 * grows faster than the switch count. {@code worstTransition} is not weighted.
 *
 * <p>Precedence, most specific first, and never stacked on the same slot:
 * a per-player switch multiplier replaces the band default; an occurrence part
 * preference replaces that slot's instrument affinity; a part keep multiplier
 * replaces the item multiplier, which replaces 1. Hard pins are not weights.
 * Two part preferences for the same occurrence part, or two affinities for the
 * same player and instrument, are rejected here. Alias instruments and unknown
 * ids are rejected when the rules are applied to a problem.
 */
public record OptimizationRules(
        double smoothWeight,
        double totalWeight,
        double roleWeight,
        double keepWeight,
        int targetChanges,
        double defaultSwitchMultiplier,
        Map<Long, Double> playerSwitchMultiplier,
        List<InstrumentAffinity> instrumentAffinities,
        List<PartPreference> partPreferences,
        Map<Long, Double> itemKeepMultiplier,
        Map<OccurrencePart, Double> partKeepMultiplier,
        List<InstrumentPlacementRule> instrumentPlacements) {

    public OptimizationRules(
            double smoothWeight,
            double totalWeight,
            double roleWeight,
            double keepWeight,
            int targetChanges,
            double defaultSwitchMultiplier,
            Map<Long, Double> playerSwitchMultiplier,
            List<InstrumentAffinity> instrumentAffinities,
            List<PartPreference> partPreferences,
            Map<Long, Double> itemKeepMultiplier,
            Map<OccurrencePart, Double> partKeepMultiplier) {
        this(
                smoothWeight,
                totalWeight,
                roleWeight,
                keepWeight,
                targetChanges,
                defaultSwitchMultiplier,
                playerSwitchMultiplier,
                instrumentAffinities,
                partPreferences,
                itemKeepMultiplier,
                partKeepMultiplier,
                List.of());
    }

    public OptimizationRules {
        requireWeight(smoothWeight, "smoothWeight");
        requireWeight(totalWeight, "totalWeight");
        requireWeight(roleWeight, "roleWeight");
        requireWeight(keepWeight, "keepWeight");
        if (targetChanges < 0) {
            throw new IllegalArgumentException("targetChanges");
        }
        requireWeight(defaultSwitchMultiplier, "defaultSwitchMultiplier");
        playerSwitchMultiplier = copyWeights(playerSwitchMultiplier, "playerSwitchMultiplier");
        instrumentAffinities = List.copyOf(instrumentAffinities);
        partPreferences = List.copyOf(partPreferences);
        itemKeepMultiplier = copyWeights(itemKeepMultiplier, "itemKeepMultiplier");
        partKeepMultiplier = copyPartWeights(partKeepMultiplier);
        rejectDuplicateAffinities(instrumentAffinities);
        rejectDuplicatePreferences(partPreferences);
        instrumentPlacements =
                instrumentPlacements.stream()
                        .sorted(
                                java.util.Comparator.comparingLong(
                                        InstrumentPlacementRule::instrumentId))
                        .toList();
        Set<Long> placementIds = new HashSet<>();
        for (InstrumentPlacementRule placement : instrumentPlacements) {
            if (!placementIds.add(placement.instrumentId()))
                throw new IllegalArgumentException("Duplicate instrument placement.");
        }
    }

    /** Smooth, total, role, and keep weights of 1, target 2, and no scoped preferences. */
    public static OptimizationRules defaults() {
        return new OptimizationRules(
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                2,
                1.0d,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of());
    }

    private static void requireWeight(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name);
        }
    }

    private static Map<Long, Double> copyWeights(Map<Long, Double> source, String name) {
        Objects.requireNonNull(source, name);
        Map<Long, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0 || !finiteNonNegative(entry.getValue())) {
                throw new IllegalArgumentException(name);
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    private static Map<OccurrencePart, Double> copyPartWeights(Map<OccurrencePart, Double> source) {
        Objects.requireNonNull(source, "partKeepMultiplier");
        Map<OccurrencePart, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<OccurrencePart, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || !finiteNonNegative(entry.getValue())) {
                throw new IllegalArgumentException("partKeepMultiplier");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    private static boolean finiteNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0d;
    }

    private static void rejectDuplicateAffinities(List<InstrumentAffinity> affinities) {
        Set<String> seen = new HashSet<>();
        for (InstrumentAffinity affinity : affinities) {
            if (!seen.add(affinity.playerId() + ":" + affinity.instrumentId())) {
                throw new IllegalArgumentException("instrumentAffinities");
            }
        }
    }

    private static void rejectDuplicatePreferences(List<PartPreference> preferences) {
        Set<String> seen = new HashSet<>();
        for (PartPreference preference : preferences) {
            if (!seen.add(preference.setlistItemId() + ":" + preference.partNumber())) {
                throw new IllegalArgumentException("partPreferences");
            }
        }
    }
}
