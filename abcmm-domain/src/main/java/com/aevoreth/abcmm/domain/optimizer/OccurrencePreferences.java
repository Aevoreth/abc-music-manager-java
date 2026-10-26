package com.aevoreth.abcmm.domain.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Soft preferences for the items of one setlist. These override band defaults
 * where the scoring rules say so, and they never become hard pins.
 *
 * <p>Lists are sorted by setlist item id, then part number.
 */
public record OccurrencePreferences(
        List<PartPreferenceWeight> partPreferences,
        List<ItemKeepWeight> itemKeepWeights,
        List<PartKeepWeight> partKeepWeights) {

    public OccurrencePreferences {
        partPreferences = copyPartPreferences(partPreferences);
        itemKeepWeights = copyItemKeeps(itemKeepWeights);
        partKeepWeights = copyPartKeeps(partKeepWeights);
    }

    public static OccurrencePreferences empty() {
        return new OccurrencePreferences(List.of(), List.of(), List.of());
    }

    private static List<PartPreferenceWeight> copyPartPreferences(List<PartPreferenceWeight> source) {
        Objects.requireNonNull(source, "partPreferences");
        List<PartPreferenceWeight> copy = new ArrayList<>(source);
        Set<String> seen = new HashSet<>();
        for (PartPreferenceWeight weight : copy) {
            if (weight == null || !seen.add(weight.setlistItemId() + ":" + weight.partNumber())) {
                throw new IllegalArgumentException("partPreferences");
            }
        }
        copy.sort(Comparator
                .comparingLong(PartPreferenceWeight::setlistItemId)
                .thenComparingInt(PartPreferenceWeight::partNumber));
        return List.copyOf(copy);
    }

    private static List<ItemKeepWeight> copyItemKeeps(List<ItemKeepWeight> source) {
        Objects.requireNonNull(source, "itemKeepWeights");
        List<ItemKeepWeight> copy = new ArrayList<>(source);
        Set<Long> seen = new HashSet<>();
        for (ItemKeepWeight weight : copy) {
            if (weight == null || !seen.add(weight.setlistItemId())) {
                throw new IllegalArgumentException("itemKeepWeights");
            }
        }
        copy.sort(Comparator.comparingLong(ItemKeepWeight::setlistItemId));
        return List.copyOf(copy);
    }

    private static List<PartKeepWeight> copyPartKeeps(List<PartKeepWeight> source) {
        Objects.requireNonNull(source, "partKeepWeights");
        List<PartKeepWeight> copy = new ArrayList<>(source);
        Set<String> seen = new HashSet<>();
        for (PartKeepWeight weight : copy) {
            if (weight == null || !seen.add(weight.setlistItemId() + ":" + weight.partNumber())) {
                throw new IllegalArgumentException("partKeepWeights");
            }
        }
        copy.sort(Comparator
                .comparingLong(PartKeepWeight::setlistItemId)
                .thenComparingInt(PartKeepWeight::partNumber));
        return List.copyOf(copy);
    }
}
