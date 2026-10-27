package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.optimizer.OptimizationRevision;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.ItemSources;

/**
 * Read-only load result. {@code problem} is null when the database cannot yield a snapshot.
 * Saved rules and the search-budget preset are present whenever {@code problem} is.
 * {@code budgetSeconds} is the caller's override and stays null when the preset should be used.
 * {@code revision} is the setlist snapshot to pass to apply, and is null when the load failed.
 * {@code itemSources} holds the layout and override rows captured with that snapshot so a later
 * apply can plan override rows without reading the database again. It is empty when the load failed.
 */
public record LoadOutcome(
        long setlistId,
        Long seed,
        Long budgetSeconds,
        Integer requestedPlayers,
        OptimizationProblem problem,
        FeasibilityReport report,
        OptimizationRules rules,
        SearchBudgetPreset searchBudget,
        OptimizationRevision revision,
        List<ItemSources> itemSources) {

    public LoadOutcome {
        if (setlistId <= 0) {
            throw new IllegalArgumentException("setlistId");
        }
        if (report == null) {
            throw new IllegalArgumentException("report");
        }
        if (problem != null) {
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(searchBudget, "searchBudget");
        }
        if (itemSources == null) {
            itemSources = List.of();
        } else {
            List<ItemSources> copied = new ArrayList<>(itemSources.size());
            for (ItemSources source : itemSources) {
                Objects.requireNonNull(source, "itemSources");
                copied.add(new ItemSources(
                        source.setlistItemId(),
                        source.overrides() == null ? List.of() : List.copyOf(source.overrides()),
                        source.layoutAssignments() == null
                                ? List.of()
                                : List.copyOf(source.layoutAssignments())));
            }
            itemSources = List.copyOf(copied);
        }
    }
}
