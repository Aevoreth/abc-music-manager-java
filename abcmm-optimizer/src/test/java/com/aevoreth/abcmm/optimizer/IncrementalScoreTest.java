package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IncrementalScoreTest {

    @Test
    void editingTwoSongsMatchesTheFullScorerIncludingFractionalWeights() {
        OptimizationProblem problem = alternatingSongs();
        OptimizationRules rules = fractionalRules();
        CandidateState state = repaired(problem, rules);
        ScoreBreakdown before = state.breakdown();
        int untouched = state.transitionChanges[1];

        SearchMoves.applyForcedPair(state, 0, 0, 1);
        SearchMoves.applyForcedPair(state, 3, 0, 1);

        assertEquals(untouched, state.transitionChanges[1]);
        assertFaithful(problem, rules, state);
        assertTrue(state.weightedScore != before.weightedScore() || state.changedAssignments != before.changedAssignments());
    }

    @Test
    void aSingleSongEditMatchesEveryBreakdownField() {
        OptimizationProblem problem = alternatingSongs();
        OptimizationRules rules = fractionalRules();
        CandidateState state = repaired(problem, rules);
        SearchMoves.applyForcedPair(state, 2, 0, 1);
        assertFaithful(problem, rules, state);
    }

    private static void assertFaithful(OptimizationProblem problem, OptimizationRules rules, CandidateState state) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, state.toAssignment(), rules);
        ScoreOutcome.Accepted accepted = assertInstanceOf(ScoreOutcome.Accepted.class, outcome);
        ScoreBreakdown full = accepted.breakdown();
        ScoreBreakdown incremental = state.breakdown();
        assertEquals(full, incremental);
        assertEquals(Double.doubleToLongBits(full.weightedScore()), Double.doubleToLongBits(incremental.weightedScore()));
        assertEquals(full.changesPerTransition(), incremental.changesPerTransition());
        assertEquals(full.histogram(), incremental.histogram());
        assertEquals(0, AssignmentValidator.validate(problem, state.toAssignment()).size());
    }

    private static CandidateState repaired(OptimizationProblem problem, OptimizationRules rules) {
        AssignmentRepair.Result result = AssignmentRepair.repair(ProblemIndex.build(problem, rules));
        assertTrue(result.feasible());
        return result.state();
    }

    private static OptimizationRules fractionalRules() {
        return new OptimizationRules(
                1.5d,
                0.5d,
                2.0d,
                0.25d,
                1,
                1.0d,
                Map.of(10L, 1.25d),
                List.of(new InstrumentAffinity(10L, 1L, 3.5d)),
                List.of(new PartPreference(8L, 1, 11L, 0.5d)),
                Map.of(8L, 1.75d),
                Map.of(new OccurrencePart(5L, 1), 0.5d));
    }

    private static OptimizationProblem alternatingSongs() {
        List<SongOccurrence> occurrences = new ArrayList<>();
        List<BaselineAssignment> baseline = new ArrayList<>();
        long[] items = {5L, 6L, 7L, 8L, 9L};
        for (int song = 0; song < items.length; song++) {
            long instrument = song % 2 == 0 ? 1L : 2L;
            long other = instrument == 1L ? 2L : 1L;
            occurrences.add(new SongOccurrence(
                    items[song],
                    100L + song,
                    song,
                    List.of(new RequiredPart(1, instrument), new RequiredPart(2, other))));
            baseline.add(new BaselineAssignment(items[song], 10L, 1, instrument));
            baseline.add(new BaselineAssignment(items[song], 11L, 2, other));
        }
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "Lute");
        names.put(2L, "Harp");
        return new OptimizationProblem(
                1,
                false,
                1,
                List.of(new Performer(10L, java.util.Set.of(1L, 2L)), new Performer(11L, java.util.Set.of(1L, 2L))),
                occurrences,
                baseline,
                List.of(),
                PhysicalInstrumentEquivalence.equivalentIds(names));
    }
}
