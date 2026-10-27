package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.optimizer.AssignmentOverride;

/**
 * Turns a solved assignment into the override rows an apply should write.
 * A layout player keeps no row when the solved part already matches the song
 * layout, including an explicit null on that layout. A different part, including
 * idle where the layout has a part, becomes one row. Players who are not on the
 * layout keep whatever override rows they already have, duplicates included.
 * Two occurrences of the same song are planned by setlist item id.
 */
public final class AssignmentOverridePlan {

    private AssignmentOverridePlan() {
    }

    /**
     * Plan from the layout rows captured on a successful load. Performers on that
     * problem are the layout players, in layout order.
     */
    public static List<AssignmentOverride> plan(LoadOutcome outcome, Assignment assignment) {
        Objects.requireNonNull(outcome, "outcome");
        OptimizationProblem problem = outcome.problem();
        if (problem == null) {
            throw new IllegalArgumentException("problem");
        }
        List<Long> players = new ArrayList<>();
        for (Performer performer : problem.performers()) {
            players.add(performer.playerId());
        }
        return plan(players, outcome.itemSources(), assignment);
    }

    public static List<AssignmentOverride> plan(
            List<Long> layoutPlayerIds,
            List<EffectiveAssignmentResolver.ItemSources> items,
            Assignment assignment) {
        Objects.requireNonNull(layoutPlayerIds, "layoutPlayerIds");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(assignment, "assignment");
        Set<Long> layoutPlayers = new LinkedHashSet<>(layoutPlayerIds);
        List<AssignmentOverride> planned = new ArrayList<>();
        for (EffectiveAssignmentResolver.ItemSources item : items) {
            Map<Long, Integer> layout = firstLayoutRow(item, layoutPlayers);
            for (long playerId : layoutPlayerIds) {
                AssignedSlot slot = assignment.slot(item.setlistItemId(), playerId);
                Integer desired = slot == null ? null : slot.partNumber();
                boolean layoutHasPlayer = layout.containsKey(playerId);
                Integer layoutPart = layoutHasPlayer ? layout.get(playerId) : null;
                boolean same = layoutHasPlayer ? Objects.equals(layoutPart, desired) : desired == null;
                if (!same) {
                    planned.add(new AssignmentOverride(item.setlistItemId(), playerId, desired));
                }
            }
            if (item.overrides() == null) {
                continue;
            }
            for (EffectiveAssignmentResolver.PlayerPart row : item.overrides()) {
                if (!layoutPlayers.contains(row.playerId())) {
                    planned.add(new AssignmentOverride(item.setlistItemId(), row.playerId(), row.partNumber()));
                }
            }
        }
        return List.copyOf(planned);
    }

    /**
     * First layout row wins, matching {@link EffectiveAssignmentResolver}. Later
     * rows for the same layout player are ignored. A null part is a real value.
     */
    private static Map<Long, Integer> firstLayoutRow(
            EffectiveAssignmentResolver.ItemSources item,
            Set<Long> layoutPlayers) {
        Map<Long, Integer> layout = new LinkedHashMap<>();
        if (item.layoutAssignments() == null) {
            return layout;
        }
        for (EffectiveAssignmentResolver.PlayerPart row : item.layoutAssignments()) {
            if (!layoutPlayers.contains(row.playerId()) || layout.containsKey(row.playerId())) {
                continue;
            }
            layout.put(row.playerId(), row.partNumber());
        }
        return layout;
    }
}
