package com.aevoreth.abcmm.domain.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Saved soft objective for one band. Weights may be zero. Smooth is emphasized
 * by default and total stays on, so a flat overload still has a reason to
 * reduce switches.
 *
 * <p>Lists are sorted by id. A player switch weight replaces {@code defaultSwitchMultiplier} for
 * that player. Instrument affinity is replaced, not stacked, when an occurrence part preference
 * names that slot. Instrument-first placements charge a soft role penalty outside a preferred group
 * or for an avoided character. Older profiles default to no placements. Hard pins are stored per
 * setlist item, not on this profile. {@code has_proficiency} is not an input.
 *
 * <p>{@code settingsVersion} is the document version, not the SQLite schema version.
 * A missing {@code searchBudget} in an older document reads as {@link SearchBudgetPreset#NORMAL}.
 */
public record BandOptimizationProfile(
        int settingsVersion,
        double smoothWeight,
        double totalWeight,
        double roleWeight,
        double keepWeight,
        int targetChanges,
        double defaultSwitchMultiplier,
        List<PlayerSwitchWeight> playerSwitchWeights,
        List<InstrumentAffinityWeight> instrumentAffinities,
        SearchBudgetPreset searchBudget,
        List<InstrumentPlacementRule> instrumentPlacements) {

    public BandOptimizationProfile(
            int settingsVersion,
            double smoothWeight,
            double totalWeight,
            double roleWeight,
            double keepWeight,
            int targetChanges,
            double defaultSwitchMultiplier,
            List<PlayerSwitchWeight> playerSwitchWeights,
            List<InstrumentAffinityWeight> instrumentAffinities,
            SearchBudgetPreset searchBudget) {
        this(
                settingsVersion,
                smoothWeight,
                totalWeight,
                roleWeight,
                keepWeight,
                targetChanges,
                defaultSwitchMultiplier,
                playerSwitchWeights,
                instrumentAffinities,
                searchBudget,
                List.of());
    }

    public static final int CURRENT_SETTINGS_VERSION = 1;

    public BandOptimizationProfile {
        if (settingsVersion != CURRENT_SETTINGS_VERSION) {
            throw new IllegalArgumentException("settingsVersion");
        }
        OptimizationWeights.requireNonNegative(smoothWeight, "smoothWeight");
        OptimizationWeights.requireNonNegative(totalWeight, "totalWeight");
        OptimizationWeights.requireNonNegative(roleWeight, "roleWeight");
        OptimizationWeights.requireNonNegative(keepWeight, "keepWeight");
        if (targetChanges < 0) {
            throw new IllegalArgumentException("targetChanges");
        }
        OptimizationWeights.requireNonNegative(defaultSwitchMultiplier, "defaultSwitchMultiplier");
        playerSwitchWeights = copySwitches(playerSwitchWeights);
        instrumentAffinities = copyAffinities(instrumentAffinities);
        searchBudget = Objects.requireNonNull(searchBudget, "searchBudget");
        instrumentPlacements =
                instrumentPlacements.stream()
                        .sorted(
                                java.util.Comparator.comparingLong(
                                        InstrumentPlacementRule::instrumentId))
                        .toList();
        Set<Long> instruments = new HashSet<>();
        for (InstrumentPlacementRule placement : instrumentPlacements) {
            if (!instruments.add(placement.instrumentId()))
                throw new IllegalArgumentException("Duplicate instrument placement.");
        }
    }

    /**
     * Smooth 4, total 1, role 1, keep 1, target 2, switch multiplier 1,
     * Normal search budget, and no player-scoped rows. This is not the solver's
     * equal-weight default.
     */
    public static BandOptimizationProfile defaults() {
        return new BandOptimizationProfile(
                CURRENT_SETTINGS_VERSION,
                4.0d,
                1.0d,
                1.0d,
                1.0d,
                2,
                1.0d,
                List.of(),
                List.of(),
                SearchBudgetPreset.NORMAL);
    }

    private static List<PlayerSwitchWeight> copySwitches(List<PlayerSwitchWeight> source) {
        Objects.requireNonNull(source, "playerSwitchWeights");
        List<PlayerSwitchWeight> copy = new ArrayList<>(source);
        Set<Long> seen = new HashSet<>();
        for (PlayerSwitchWeight weight : copy) {
            if (weight == null || !seen.add(weight.playerId())) {
                throw new IllegalArgumentException("playerSwitchWeights");
            }
        }
        copy.sort(Comparator.comparingLong(PlayerSwitchWeight::playerId));
        return List.copyOf(copy);
    }

    private static List<InstrumentAffinityWeight> copyAffinities(List<InstrumentAffinityWeight> source) {
        Objects.requireNonNull(source, "instrumentAffinities");
        List<InstrumentAffinityWeight> copy = new ArrayList<>(source);
        Set<String> seen = new HashSet<>();
        for (InstrumentAffinityWeight weight : copy) {
            if (weight == null || !seen.add(weight.playerId() + ":" + weight.instrumentId())) {
                throw new IllegalArgumentException("instrumentAffinities");
            }
        }
        copy.sort(Comparator
                .comparingLong(InstrumentAffinityWeight::playerId)
                .thenComparingLong(InstrumentAffinityWeight::instrumentId));
        return List.copyOf(copy);
    }
}
