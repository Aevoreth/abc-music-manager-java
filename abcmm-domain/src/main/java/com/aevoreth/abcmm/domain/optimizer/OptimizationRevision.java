package com.aevoreth.abcmm.domain.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical snapshot of one setlist at solve time. Lists are copied and sorted
 * so two snapshots of the same rows compare equal. Override equality keeps
 * duplicate rows and keeps an explicit null part distinct from a missing row.
 */
public record OptimizationRevision(
        Long bandLayoutId,
        List<OptimizationStructureItem> items,
        List<AssignmentPin> pins,
        List<AssignmentOverride> overrides) {

    public OptimizationRevision {
        if (bandLayoutId != null && bandLayoutId <= 0) {
            throw new IllegalArgumentException("bandLayoutId");
        }
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(overrides, "overrides");
        ArrayList<OptimizationStructureItem> itemCopy = new ArrayList<>(items);
        itemCopy.sort(Comparator.comparingInt(OptimizationStructureItem::position)
                .thenComparingLong(OptimizationStructureItem::id));
        items = List.copyOf(itemCopy);
        ArrayList<AssignmentPin> pinCopy = new ArrayList<>(pins);
        pinCopy.sort(Comparator.comparingLong(AssignmentPin::setlistItemId)
                .thenComparingInt(AssignmentPin::partNumber)
                .thenComparingLong(AssignmentPin::playerId));
        pins = List.copyOf(pinCopy);
        ArrayList<AssignmentOverride> overrideCopy = new ArrayList<>(overrides);
        overrideCopy.sort(Comparator.comparingLong(AssignmentOverride::setlistItemId)
                .thenComparingLong(AssignmentOverride::playerId)
                .thenComparing(AssignmentOverride::partNumber, Comparator.nullsFirst(Integer::compareTo)));
        overrides = List.copyOf(overrideCopy);
    }
}
