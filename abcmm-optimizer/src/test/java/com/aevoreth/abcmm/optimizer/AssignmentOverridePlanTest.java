package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.optimizer.AssignmentOverride;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.ItemSources;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.PlayerPart;

class AssignmentOverridePlanTest {

    @Test
    void keepsLayoutFallbackClearsWithNullAndCopiesOffLayoutRows() {
        List<Long> players = List.of(5L, 6L);
        ItemSources item = new ItemSources(
                10,
                List.of(new PlayerPart(7, 3), new PlayerPart(7, 3)),
                List.of(new PlayerPart(5, 1), new PlayerPart(5, 4), new PlayerPart(6, 2)));
        Assignment assignment = new Assignment(List.of(
                new AssignedSlot(10, 5, 1, null),
                new AssignedSlot(10, 6, null, null)));

        assertEquals(
                List.of(
                        new AssignmentOverride(10, 6, null),
                        new AssignmentOverride(10, 7, 3),
                        new AssignmentOverride(10, 7, 3)),
                AssignmentOverridePlan.plan(players, List.of(item), assignment));
    }

    @Test
    void firstNullLayoutRowStaysTheFallback() {
        ItemSources item = new ItemSources(
                10,
                List.of(),
                List.of(new PlayerPart(5, null), new PlayerPart(5, 4)));
        Assignment assignment = new Assignment(List.of(new AssignedSlot(10, 5, null, null)));

        assertEquals(List.of(), AssignmentOverridePlan.plan(List.of(5L), List.of(item), assignment));
    }

    @Test
    void duplicateItemsArePlannedSeparately() {
        ItemSources first = new ItemSources(10, List.of(), List.of(new PlayerPart(5, 1)));
        ItemSources second = new ItemSources(11, List.of(), List.of(new PlayerPart(5, 1)));
        Assignment assignment = new Assignment(List.of(
                new AssignedSlot(10, 5, 1, null),
                new AssignedSlot(11, 5, 2, null)));

        assertEquals(
                List.of(new AssignmentOverride(11, 5, 2)),
                AssignmentOverridePlan.plan(List.of(5L), List.of(first, second), assignment));
    }

    @Test
    void plansFromTheSourcesKeptOnTheLoad() {
        ItemSources item = new ItemSources(
                10,
                List.of(new PlayerPart(7, 3)),
                List.of(new PlayerPart(5, 1), new PlayerPart(6, 2)));
        OptimizationProblem problem = new OptimizationProblem(
                1,
                false,
                1,
                List.of(new Performer(5, Set.of(1L)), new Performer(6, Set.of(1L))),
                List.of(new SongOccurrence(10, 100, 0, List.of(new RequiredPart(1, 1L)))),
                List.of(),
                List.of(),
                Map.of());
        LoadOutcome outcome = new LoadOutcome(
                1,
                null,
                null,
                null,
                problem,
                new FeasibilityReport(List.of()),
                OptimizationRules.defaults(),
                SearchBudgetPreset.NORMAL,
                null,
                List.of(item));
        Assignment assignment = new Assignment(List.of(
                new AssignedSlot(10, 5, 1, null),
                new AssignedSlot(10, 6, null, null)));

        assertEquals(1, outcome.itemSources().size());
        assertEquals(
                List.of(
                        new AssignmentOverride(10, 6, null),
                        new AssignmentOverride(10, 7, 3)),
                AssignmentOverridePlan.plan(outcome, assignment));
    }
}
