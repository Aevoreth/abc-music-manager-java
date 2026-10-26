package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AssignmentSolverTest {

    @Test
    void repairFillsAGapAndKeepsALegalBaseline() {
        OptimizationProblem gap = problem(
                List.of(player(10, 1), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(play(5, 10, 1, 1L)),
                List.of());
        SearchOutcome.Solved filled = solved(gap, OptimizationRules.defaults(), Duration.ZERO);
        assertEquals(1, part(filled.assignment(), 5, 10));
        assertNull(part(filled.assignment(), 5, 11));
        assertEquals(0, AssignmentValidator.validate(gap, filled.assignment()).size());

        OptimizationProblem legal = problem(
                List.of(player(10, 1), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(play(5, 10, 1, 1L), idle(5, 11)),
                List.of());
        SearchOutcome.Solved kept = solved(legal, OptimizationRules.defaults(), Duration.ZERO);
        assertEquals(parts(Assignment.fromBaseline(legal)), parts(kept.assignment()));
    }

    @Test
    void repairHonorsPinsAndFailsWhenThePinnedPlayerDoesNotOwnTheInstrument() {
        OptimizationProblem pinned = problem(
                List.of(player(10, 1), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(idle(5, 10), idle(5, 11)),
                List.of(new Pin(5, 1, 10)));
        SearchOutcome.Solved solved = solved(pinned, OptimizationRules.defaults(), Duration.ZERO);
        assertEquals(1, part(solved.assignment(), 5, 10));

        OptimizationProblem unowned = problem(
                List.of(player(10, 1), player(11, 2)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(idle(5, 10), play(5, 11, 1, 1L)),
                List.of(new Pin(5, 1, 11)));
        SearchOutcome.Infeasible infeasible = assertInstanceOf(
                SearchOutcome.Infeasible.class,
                AssignmentSolver.solve(unowned, OptimizationRules.defaults(), SearchBudget.of(1, Duration.ZERO)));
        assertTrue(has(infeasible, "unowned_instrument"));
    }

    @Test
    void repairBacktracksWhenTheFirstOwnerIsNeededOnALaterPart() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1, 2), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L), new RequiredPart(2, 2L))),
                List.of(idle(5, 10), idle(5, 11)),
                List.of());
        SearchOutcome.Solved solved = solved(problem, OptimizationRules.defaults(), Duration.ZERO);
        assertEquals(2, part(solved.assignment(), 5, 10));
        assertEquals(1, part(solved.assignment(), 5, 11));
    }

    @Test
    void zeroBudgetCancelledAndExhaustedBudgetStayFeasible() {
        OptimizationProblem problem = swapFixture();
        OptimizationRules rules = switchOnly();
        ScoreBreakdown baseline = accepted(problem, rules);

        SearchOutcome.Solved zero = solved(problem, rules, Duration.ZERO);
        assertEquals(StopReason.BUDGET, zero.reason());
        assertEquals(0, zero.acceptedMoves());
        assertEquals(0, zero.evaluatedMoves());
        assertEquals(baseline, zero.breakdown());
        assertEquals(parts(Assignment.fromBaseline(problem)), parts(zero.assignment()));

        SearchOutcome.Solved cancelled = solved(
                problem,
                rules,
                new SearchBudget(3, Duration.ofSeconds(5), () -> true, null));
        assertEquals(StopReason.CANCELLED, cancelled.reason());
        assertEquals(0, cancelled.acceptedMoves());
        assertEquals(baseline.weightedScore(), cancelled.breakdown().weightedScore(), 0.0);
        assertEquals(0, AssignmentValidator.validate(problem, cancelled.assignment()).size());

        SearchOutcome.Solved exhausted = solved(problem, rules, Duration.ofNanos(1));
        assertEquals(StopReason.BUDGET, exhausted.reason());
        assertTrue(exhausted.breakdown().weightedScore() <= baseline.weightedScore());
        assertEquals(0, AssignmentValidator.validate(problem, exhausted.assignment()).size());
    }

    @Test
    void pairSwapAndIdleRotationImproveTheScore() {
        OptimizationRules rules = switchOnly();
        SearchOutcome.Solved swapped = solved(swapFixture(), rules, Duration.ofMillis(50));
        assertEquals(0, swapped.breakdown().totalChanges());
        assertTrue(swapped.breakdown().weightedScore() < accepted(swapFixture(), rules).weightedScore());

        SearchOutcome.Solved rotated = solved(idleFixture(), rules, Duration.ofMillis(50));
        assertEquals(0, rotated.breakdown().totalChanges());
        assertTrue(rotated.breakdown().weightedScore() < accepted(idleFixture(), rules).weightedScore());
    }

    @Test
    void coordinatedCycleEscapesAPairwiseLocalMinimum() {
        OptimizationProblem problem = cycleFixture();
        OptimizationRules rules = OptimizationRules.defaults();
        ScoreBreakdown baseline = accepted(problem, rules);
        assertEquals(4.0, baseline.weightedScore(), 0.0);
        assertEquals(3, baseline.totalChanges());

        CandidateState state = AssignmentRepair.repair(ProblemIndex.build(problem, rules)).state();
        for (int song = 0; song < 2; song++) {
            for (int first = 0; first < 3; first++) {
                for (int second = first + 1; second < 3; second++) {
                    assertFalse(SearchMoves.pairLegal(state, song, first, second));
                }
            }
        }
        assertTrue(SearchMoves.commitCycle(state, 0, 0, 1, 2, 0));
        assertEquals(0, state.totalChanges);
        assertEquals(3.0, state.weightedScore, 0.0);
        assertFaithful(problem, rules, state);

        SearchOutcome.Solved solved = solved(problem, rules, Duration.ofMillis(50));
        assertTrue(solved.breakdown().weightedScore() < 4.0);
        assertEquals(0, solved.breakdown().totalChanges());
        assertEquals(0, AssignmentValidator.validate(problem, solved.assignment()).size());

        SearchOutcome.Solved paused = solved(problem, rules, Duration.ZERO);
        assertEquals(4.0, paused.breakdown().weightedScore(), 0.0);
    }

    @Test
    void windowMoveImprovesAndAnIllegalWindowIsRefused() {
        OptimizationProblem problem = preferenceFixture();
        OptimizationRules rules = preferenceRules();
        CandidateState state = AssignmentRepair.repair(ProblemIndex.build(problem, rules)).state();
        double before = state.weightedScore;
        assertTrue(SearchMoves.commitPairWindow(state, 0, 2, 0, 1));
        assertTrue(state.weightedScore < before);
        assertFaithful(problem, rules, state);

        OptimizationProblem blocked = problem(
                List.of(player(10, 1, 2, 3), player(11, 1, 2)),
                List.of(
                        song(5, new RequiredPart(1, 1L), new RequiredPart(2, 2L)),
                        song(6, new RequiredPart(1, 1L), new RequiredPart(2, 3L))),
                List.of(
                        play(5, 10, 1, 1L), play(5, 11, 2, 2L),
                        play(6, 10, 2, 3L), play(6, 11, 1, 1L)),
                List.of());
        CandidateState stuck = AssignmentRepair.repair(ProblemIndex.build(blocked, OptimizationRules.defaults())).state();
        Map<String, Integer> parts = parts(stuck.toAssignment());
        double score = stuck.weightedScore;
        assertFalse(SearchMoves.commitPairWindow(stuck, 0, 2, 0, 1));
        assertEquals(parts, parts(stuck.toAssignment()));
        assertEquals(Double.doubleToLongBits(score), Double.doubleToLongBits(stuck.weightedScore));
    }

    @Test
    void pinsStayPutAndTheSameSeedRepeatsTheAssignment() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1, 2), player(11, 1, 2)),
                List.of(song(5, new RequiredPart(1, 1L)), song(6, new RequiredPart(1, 2L))),
                List.of(play(5, 10, 1, 1L), idle(5, 11), play(6, 10, 1, 2L), idle(6, 11)),
                List.of(new Pin(6, 1, 10)));
        OptimizationRules rules = switchOnly();
        SearchOutcome.Solved solved = solved(problem, rules, Duration.ofMillis(50));
        assertEquals(1, part(solved.assignment(), 6, 10));
        assertEquals(0, solved.breakdown().totalChanges());
        assertTrue(solved.breakdown().weightedScore() < accepted(problem, rules).weightedScore());

        SearchBudget budget = SearchBudget.of(4, Duration.ofMillis(80));
        Map<String, Integer> first = parts(solved(cycleFixture(), OptimizationRules.defaults(), budget).assignment());
        Map<String, Integer> second = parts(solved(cycleFixture(), OptimizationRules.defaults(), budget).assignment());
        assertEquals(first, second);
    }

    @Test
    void listenerSeesTheBestScoreAndCancellationStopsTheSearch() {
        AtomicReference<ScoreBreakdown> first = new AtomicReference<>();
        AtomicReference<ScoreBreakdown> last = new AtomicReference<>();
        AtomicBoolean cancel = new AtomicBoolean();
        SearchListener listener = (best, moves) -> {
            if (first.get() == null) {
                first.set(best);
            }
            last.set(best);
            if (moves > 0) {
                cancel.set(true);
            }
        };
        OptimizationProblem problem = cycleFixture();
        SearchOutcome.Solved solved = assertInstanceOf(SearchOutcome.Solved.class, AssignmentSolver.solve(
                problem,
                OptimizationRules.defaults(),
                new SearchBudget(1, Duration.ofSeconds(2), cancel::get, listener)));
        assertEquals(StopReason.CANCELLED, solved.reason());
        assertEquals(accepted(problem, OptimizationRules.defaults()), first.get());
        assertEquals(solved.breakdown(), last.get());
        assertTrue(solved.breakdown().weightedScore() < first.get().weightedScore());
    }

    @Test
    void positiveBudgetCountsRejectedCandidates() {
        SearchOutcome.Solved solved = solved(swapFixture(), switchOnly(), Duration.ofMillis(200));
        assertTrue(solved.evaluatedMoves() > 0);
        assertTrue(solved.evaluatedMoves() >= solved.acceptedMoves());
    }

    @Test
    void conflictingPinsAndBrokenRulesAreInfeasible() {
        OptimizationProblem conflict = problem(
                List.of(player(10, 1), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(play(5, 10, 1, 1L), idle(5, 11)),
                List.of(new Pin(5, 1, 10), new Pin(5, 1, 11)));
        SearchOutcome.Infeasible pins = assertInstanceOf(
                SearchOutcome.Infeasible.class,
                AssignmentSolver.solve(conflict, OptimizationRules.defaults(), SearchBudget.of(1, Duration.ZERO)));
        assertTrue(has(pins, "pin_conflict"));

        OptimizationProblem valid = problem(
                List.of(player(10, 1), player(11, 1)),
                List.of(song(5, new RequiredPart(1, 1L))),
                List.of(play(5, 10, 1, 1L), idle(5, 11)),
                List.of());
        OptimizationRules rules = new OptimizationRules(
                1, 1, 1, 1, 2, 1, Map.of(99L, 1.0d), List.of(), List.of(), Map.of(), Map.of());
        SearchOutcome.Infeasible unknown = assertInstanceOf(
                SearchOutcome.Infeasible.class,
                AssignmentSolver.solve(valid, rules, SearchBudget.of(1, Duration.ZERO)));
        assertTrue(has(unknown, "rule_unknown_player"));
    }

    private static SearchOutcome.Solved solved(OptimizationProblem problem, OptimizationRules rules, Duration budget) {
        return solved(problem, rules, SearchBudget.of(1, budget));
    }

    private static SearchOutcome.Solved solved(OptimizationProblem problem, OptimizationRules rules, SearchBudget budget) {
        SearchOutcome outcome = AssignmentSolver.solve(problem, rules, budget);
        SearchOutcome.Solved solved = assertInstanceOf(SearchOutcome.Solved.class, outcome, () -> String.valueOf(outcome));
        ScoreOutcome scored = AssignmentScorer.score(problem, solved.assignment(), rules);
        ScoreOutcome.Accepted accepted = assertInstanceOf(ScoreOutcome.Accepted.class, scored);
        assertEquals(accepted.breakdown(), solved.breakdown());
        assertEquals(0, AssignmentValidator.validate(problem, solved.assignment()).size());
        return solved;
    }

    private static void assertFaithful(OptimizationProblem problem, OptimizationRules rules, CandidateState state) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, state.toAssignment(), rules);
        assertEquals(assertInstanceOf(ScoreOutcome.Accepted.class, outcome).breakdown(), state.breakdown());
    }

    private static ScoreBreakdown accepted(OptimizationProblem problem, OptimizationRules rules) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, Assignment.fromBaseline(problem), rules);
        return assertInstanceOf(ScoreOutcome.Accepted.class, outcome).breakdown();
    }

    private static boolean has(SearchOutcome.Infeasible infeasible, String code) {
        return infeasible.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code));
    }

    private static OptimizationRules switchOnly() {
        return new OptimizationRules(0, 1, 0, 0, 2, 1, Map.of(), List.of(), List.of(), Map.of(), Map.of());
    }

    private static OptimizationProblem swapFixture() {
        return problem(
                List.of(player(10, 1, 2), player(11, 1, 2)),
                List.of(
                        song(5, new RequiredPart(1, 1L), new RequiredPart(2, 2L)),
                        song(6, new RequiredPart(1, 2L), new RequiredPart(2, 1L))),
                List.of(
                        play(5, 10, 1, 1L), play(5, 11, 2, 2L),
                        play(6, 10, 1, 2L), play(6, 11, 2, 1L)),
                List.of());
    }

    private static OptimizationProblem idleFixture() {
        return problem(
                List.of(player(10, 1, 2), player(11, 1, 2)),
                List.of(song(5, new RequiredPart(1, 1L)), song(6, new RequiredPart(1, 2L))),
                List.of(play(5, 10, 1, 1L), idle(5, 11), play(6, 10, 1, 2L), idle(6, 11)),
                List.of());
    }

    private static OptimizationProblem cycleFixture() {
        return problem(
                List.of(player(10, 1, 2), player(11, 2, 3), player(12, 1, 3)),
                List.of(
                        song(5, new RequiredPart(1, 1L), new RequiredPart(2, 2L), new RequiredPart(3, 3L)),
                        song(6, new RequiredPart(1, 1L), new RequiredPart(2, 2L), new RequiredPart(3, 3L))),
                List.of(
                        play(5, 10, 1, 1L), play(5, 11, 2, 2L), play(5, 12, 3, 3L),
                        play(6, 10, 2, 2L), play(6, 11, 3, 3L), play(6, 12, 1, 1L)),
                List.of());
    }

    private static OptimizationProblem preferenceFixture() {
        return problem(
                List.of(player(10, 1, 2), player(11, 1, 2)),
                List.of(
                        song(5, new RequiredPart(1, 1L), new RequiredPart(2, 2L)),
                        song(6, new RequiredPart(1, 1L), new RequiredPart(2, 2L))),
                List.of(
                        play(5, 10, 2, 2L), play(5, 11, 1, 1L),
                        play(6, 10, 2, 2L), play(6, 11, 1, 1L)),
                List.of());
    }

    private static OptimizationRules preferenceRules() {
        return new OptimizationRules(
                0, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(new InstrumentAffinity(10, 1L, 10), new InstrumentAffinity(11, 2L, 10)),
                List.of(),
                Map.of(),
                Map.of());
    }

    private static OptimizationProblem problem(
            List<Performer> performers,
            List<SongOccurrence> occurrences,
            List<BaselineAssignment> baseline,
            List<Pin> pins) {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "Lute");
        names.put(2L, "Harp");
        names.put(3L, "Flute");
        return new OptimizationProblem(1, false, 1, performers, occurrences, baseline, pins,
                PhysicalInstrumentEquivalence.equivalentIds(names));
    }

    private static Performer player(long id, long... owned) {
        return new Performer(id, java.util.Set.of(box(owned)));
    }

    private static Long[] box(long... values) {
        Long[] boxed = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return boxed;
    }

    private static SongOccurrence song(long item, RequiredPart... parts) {
        return new SongOccurrence(item, item, 0, List.of(parts));
    }

    private static BaselineAssignment play(long item, long player, int part, long instrument) {
        return new BaselineAssignment(item, player, part, instrument);
    }

    private static BaselineAssignment idle(long item, long player) {
        return new BaselineAssignment(item, player, null, null);
    }

    private static Integer part(Assignment assignment, long item, long player) {
        AssignedSlot slot = assignment.slot(item, player);
        return slot == null ? null : slot.partNumber();
    }

    private static Map<String, Integer> parts(Assignment assignment) {
        Map<String, Integer> map = new TreeMap<>();
        for (AssignedSlot slot : assignment.slots()) {
            map.put(slot.setlistItemId() + ":" + slot.playerId(), slot.partNumber());
        }
        return map;
    }
}
