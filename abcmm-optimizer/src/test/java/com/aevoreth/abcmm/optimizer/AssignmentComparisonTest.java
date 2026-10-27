package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.optimizer.AssignmentComparison.SlotChange;

class AssignmentComparisonTest {

    @Test
    void separatesRawSwitchCountsFromWeightedCost() {
        OptimizationProblem problem = problem();
        Assignment before = Assignment.fromBaseline(problem);
        Assignment after = new Assignment(List.of(
                new AssignedSlot(5, 10, 2, 2L),
                new AssignedSlot(5, 11, 1, 1L),
                new AssignedSlot(6, 10, 1, 1L),
                new AssignedSlot(6, 11, 2, 2L)));
        OptimizationRules rules = new OptimizationRules(
                1, 1, 1, 1, 1, 1, Map.of(), List.of(), List.of(), Map.of(), Map.of());

        AssignmentComparison comparison = AssignmentComparison.compare(problem, before, after, rules);

        assertEquals(0, comparison.before().totalChanges());
        assertEquals(0, comparison.before().worstTransition());
        assertEquals(0, AssignmentComparison.aboveTarget(comparison.before()));
        assertEquals(0, comparison.beforeSwitches().get(10L));
        assertEquals(0, comparison.beforeSwitches().get(11L));

        assertEquals(2, comparison.after().totalChanges());
        assertEquals(2, comparison.after().worstTransition());
        assertEquals(1, AssignmentComparison.aboveTarget(comparison.after()));
        assertEquals(Map.of(2, 1), comparison.after().histogram());
        assertEquals(1, comparison.afterSwitches().get(10L));
        assertEquals(1, comparison.afterSwitches().get(11L));
        assertEquals(1.0d, comparison.after().smoothContribution(), 0.0d);
        assertEquals(2.0d, comparison.after().totalContribution(), 0.0d);
        assertEquals(0.0d, comparison.after().roleContribution(), 0.0d);
        assertEquals(2.0d, comparison.after().keepContribution(), 0.0d);
        assertEquals(5.0d, comparison.after().weightedScore(), 0.0d);
        assertEquals(
                List.of(new SlotChange(5, 10, 1, 2), new SlotChange(5, 11, 2, 1)),
                comparison.changedSlots());
    }

    @Test
    void idleBetweenDifferentInstrumentsAddsNoSwitch() {
        OptimizationProblem problem = new OptimizationProblem(
                1,
                false,
                1,
                List.of(new Performer(10, Set.of(1L, 2L))),
                List.of(
                        new SongOccurrence(5, 100, 0, List.of(new RequiredPart(1, 1L))),
                        new SongOccurrence(6, 101, 1, List.of(new RequiredPart(1, 2L))),
                        new SongOccurrence(7, 102, 2, List.of(new RequiredPart(1, 2L)))),
                List.of(
                        new BaselineAssignment(5, 10, 1, 1L),
                        new BaselineAssignment(6, 10, null, null),
                        new BaselineAssignment(7, 10, 1, 2L)),
                List.of(),
                Map.of());

        Map<Long, Integer> switches = AssignmentScorer.switchesByPlayer(
                problem, Assignment.fromBaseline(problem));

        assertEquals(0, switches.get(10L));
    }

    @Test
    void rejectedBaselineStillListsChangedParts() {
        OptimizationProblem problem = new OptimizationProblem(
                1,
                false,
                1,
                List.of(new Performer(10, Set.of(2L))),
                List.of(new SongOccurrence(5, 100, 0, List.of(new RequiredPart(1, 1L)))),
                List.of(new BaselineAssignment(5, 10, 1, 1L)),
                List.of(),
                Map.of());
        Assignment after = new Assignment(List.of(new AssignedSlot(5, 10, null, null)));

        AssignmentComparison comparison = AssignmentComparison.compare(
                problem, Assignment.fromBaseline(problem), after, OptimizationRules.defaults());

        assertNull(comparison.before());
        assertEquals(List.of(new SlotChange(5, 10, 1, null)), comparison.changedSlots());
    }

    @Test
    void rulesJsonStoresWeightsTargetAndScopedPreferences() {
        OptimizationRules rules = new OptimizationRules(
                4, 1, 0, 2, 2, 1,
                Map.of(10L, 3.0d),
                List.of(new InstrumentAffinity(10, 1L, 1.5d)),
                List.of(new PartPreference(5, 1, 10, 2)),
                Map.of(5L, 4.0d),
                Map.of(new OccurrencePart(5, 1), 6.0d));

        String json = OptimizationRulesJson.toJson(rules);

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"smoothWeight\":4.0"));
        assertTrue(json.contains("\"targetChanges\":2"));
        assertTrue(json.contains("\"10\":3.0"));
        assertTrue(json.contains("\"instrumentId\":1"));
        assertTrue(json.contains("\"required\":false"));
        assertTrue(json.contains("\"penalty\":2.0"));
        assertTrue(json.contains("\"multiplier\":6.0"));
    }

    private static OptimizationProblem problem() {
        return new OptimizationProblem(
                1,
                false,
                1,
                List.of(new Performer(10, Set.of(1L, 2L)), new Performer(11, Set.of(1L, 2L))),
                List.of(
                        new SongOccurrence(5, 100, 0, List.of(new RequiredPart(1, 1L), new RequiredPart(2, 2L))),
                        new SongOccurrence(6, 101, 1, List.of(new RequiredPart(1, 1L), new RequiredPart(2, 2L)))),
                List.of(
                        new BaselineAssignment(5, 10, 1, 1L),
                        new BaselineAssignment(5, 11, 2, 2L),
                        new BaselineAssignment(6, 10, 1, 1L),
                        new BaselineAssignment(6, 11, 2, 2L)),
                List.of(),
                PhysicalInstrumentEquivalence.equivalentIds(Map.of(1L, "Fiddle", 2L, "Harp")));
    }
}
