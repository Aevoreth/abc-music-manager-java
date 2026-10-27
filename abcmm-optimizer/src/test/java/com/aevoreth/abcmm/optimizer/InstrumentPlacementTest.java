package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class InstrumentPlacementTest {
    private OptimizationProblem problem(boolean backOwns, boolean pinFront) {
        return new OptimizationProblem(
                1,
                false,
                1,
                List.of(
                        new Performer(10, Set.of(1L)),
                        new Performer(11, backOwns ? Set.of(1L) : Set.of())),
                List.of(new SongOccurrence(100, 1, 0, List.of(new RequiredPart(1, 1L)))),
                List.of(
                        new BaselineAssignment(100, 10, 1, 1L),
                        new BaselineAssignment(100, 11, null, null)),
                pinFront ? List.of(new Pin(100, 1, 10)) : List.of(),
                Map.of(1L, Set.of(1L, 2L), 2L, Set.of(1L, 2L)));
    }

    private OptimizationRules rules(double roleWeight, List<PartPreference> preferences) {
        return new OptimizationRules(
                0,
                0,
                roleWeight,
                0,
                2,
                1,
                Map.of(),
                List.of(),
                preferences,
                Map.of(),
                Map.of(),
                List.of(new InstrumentPlacementRule(2, List.of(11L), List.of(10L), 10)));
    }

    @Test
    void avoidsFrontRowWithoutDoubleChargingAndRespectsAliases() {
        var problem = problem(true, false);
        var rules = rules(1, List.of());
        var baseline =
                assertInstanceOf(
                        ScoreOutcome.Accepted.class,
                        AssignmentScorer.score(problem, Assignment.fromBaseline(problem), rules));
        assertEquals(10, baseline.breakdown().roleDeviation());
        var solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(problem, rules, SearchBudget.attempts(42, 1000)));
        assertEquals(1, solved.assignment().slot(100, 11).partNumber());
        assertNull(solved.assignment().slot(100, 10).partNumber());
        assertEquals(0, solved.breakdown().weightedScore());
    }

    @Test
    void avoidanceRemainsSoftWhenOnlyAnAvoidedCharacterOwnsTheInstrument() {
        var problem = problem(false, false);
        var solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(
                                problem, rules(1, List.of()), SearchBudget.attempts(0, 1000)));
        assertEquals(1, solved.assignment().slot(100, 10).partNumber());
        assertEquals(10, solved.breakdown().weightedScore());
        assertTrue(
                AssignmentValidator.validate(problem, solved.assignment(), rules(1, List.of()))
                        .isEmpty());
    }

    @Test
    void pinsFixTheAssignmentAndZeroRoleWeightDisablesAvoidance() {
        var pinned = problem(true, true);
        var solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(
                                pinned, rules(1, List.of()), SearchBudget.attempts(0, 1000)));
        assertEquals(1, solved.assignment().slot(100, 10).partNumber());
        var unpinned = problem(true, false);
        solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(
                                unpinned, rules(0, List.of()), SearchBudget.attempts(0, 1000)));
        assertEquals(Assignment.fromBaseline(unpinned), solved.assignment());
    }

    @Test
    void anOccurrencePreferenceOverridesTheBandPlacementPenalty() {
        var problem = problem(true, false);
        var rules = rules(1, List.of(new PartPreference(100, 1, 10, 25)));
        var solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(problem, rules, SearchBudget.attempts(0, 1000)));
        assertEquals(1, solved.assignment().slot(100, 10).partNumber());
        assertEquals(0, solved.breakdown().roleDeviation());
        assertTrue(OptimizationRulesJson.toJson(rules).contains("avoidedPlayerIds"));
    }
}
