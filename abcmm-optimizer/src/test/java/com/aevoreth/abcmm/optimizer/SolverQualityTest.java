package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import com.aevoreth.abcmm.domain.optimizer.InstrumentPlacementRule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

class SolverQualityTest {
    private OptimizationRules rules() {
        return new OptimizationRules(
                1.5,
                .5,
                .75,
                .25,
                1,
                1.25,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(new InstrumentPlacementRule(1, List.of(10L), List.of(12L), .3)));
    }

    @Test
    void fixedWorkSearchIsRepeatableAndNeverWorseThanTheOriginalBaseline() {
        var problem = BenchmarkFixture.problem();
        var rules = rules();
        var baseline =
                assertInstanceOf(
                        ScoreOutcome.Accepted.class,
                        AssignmentScorer.score(problem, Assignment.fromBaseline(problem), rules));
        for (long seed : new long[] {0, 1, 42}) {
            var first =
                    assertInstanceOf(
                            SearchOutcome.Solved.class,
                            AssignmentSolver.solve(
                                    problem, rules, SearchBudget.attempts(seed, 10_000)));
            var second =
                    assertInstanceOf(
                            SearchOutcome.Solved.class,
                            AssignmentSolver.solve(
                                    problem, rules, SearchBudget.attempts(seed, 10_000)));
            assertEquals(StopReason.ATTEMPTS, first.reason());
            assertEquals(10_000, first.evaluatedMoves());
            assertEquals(first.assignment(), second.assignment());
            assertEquals(first.breakdown(), second.breakdown());
            assertTrue(first.breakdown().weightedScore() <= baseline.breakdown().weightedScore());
            assertTrue(AssignmentValidator.validate(problem, first.assignment(), rules).isEmpty());
        }
    }

    @Test
    void randomizedMoveAndRollbackSequencesMatchEveryFullScoreField() {
        var problem = BenchmarkFixture.problem();
        var rules = rules();
        var state = AssignmentRepair.repair(ProblemIndex.build(problem, rules)).state();
        Random random = new Random(2026);
        for (int attempt = 0; attempt < 1000; attempt++) {
            int song = random.nextInt(state.index.songs),
                    a = random.nextInt(4),
                    b = random.nextInt(4),
                    c = random.nextInt(4);
            switch (attempt % 4) {
                case 0 -> {
                    if (SearchMoves.pairLegal(state, song, a, b))
                        SearchMoves.applyForcedPair(state, song, a, b);
                }
                case 1 -> SearchMoves.commitPair(state, song, a, b);
                case 2 -> SearchMoves.commitCycle(state, song, a, b, c, random.nextInt(2));
                case 3 -> SearchMoves.commitPairWindow(state, 0, state.index.songs, a, b);
            }
            var full =
                    assertInstanceOf(
                            ScoreOutcome.Accepted.class,
                            AssignmentScorer.score(problem, state.toAssignment(), rules));
            assertEquals(full.breakdown(), state.breakdown(), "attempt " + attempt);
        }
    }

    @Test
    void thePublicFixtureMatchesAnExhaustivelyComputedOptimum() {
        var problem = BenchmarkFixture.problem();
        var rules =
                new OptimizationRules(
                        1, 1, 0, 0, 1, 1, Map.of(), List.of(), List.of(), Map.of(), Map.of());
        double optimum = enumerate(problem, rules, 0, new ArrayList<>());
        assertTrue(Double.isFinite(optimum));
        for (long seed : new long[] {0, 1, 42}) {
            var solved =
                    assertInstanceOf(
                            SearchOutcome.Solved.class,
                            AssignmentSolver.solve(
                                    problem, rules, SearchBudget.attempts(seed, 20_000)));
            assertEquals(optimum, solved.breakdown().weightedScore());
        }
    }

    @Test
    void independentMatchingEscapesAFourPlayerCycleWithNoLegalSmallSwap() {
        List<Performer> players = new ArrayList<>();
        List<RequiredPart> parts = new ArrayList<>();
        List<BaselineAssignment> baseline = new ArrayList<>();
        List<InstrumentAffinity> affinities = new ArrayList<>();
        Map<Long, java.util.Set<Long>> instruments = new java.util.LinkedHashMap<>();
        for (int p = 0; p < 4; p++) {
            long own = p + 1, next = (p + 1) % 4 + 1;
            players.add(new Performer(10 + p, java.util.Set.of(own, next)));
            parts.add(new RequiredPart(p + 1, own));
            baseline.add(new BaselineAssignment(100, 10 + p, p + 1, own));
            affinities.add(new InstrumentAffinity(10 + p, next, 1));
            instruments.put(own, java.util.Set.of(own));
        }
        var problem =
                new OptimizationProblem(
                        1,
                        false,
                        1,
                        players,
                        List.of(new SongOccurrence(100, 1, 0, parts)),
                        baseline,
                        List.of(),
                        instruments);
        var rules =
                new OptimizationRules(
                        0, 0, 1, 0, 2, 1, Map.of(), affinities, List.of(), Map.of(), Map.of());
        var state = AssignmentRepair.repair(ProblemIndex.build(problem, rules)).state();
        for (int a = 0; a < 4; a++)
            for (int b = a + 1; b < 4; b++) assertFalse(SearchMoves.pairLegal(state, 0, a, b));
        var solved =
                assertInstanceOf(
                        SearchOutcome.Solved.class,
                        AssignmentSolver.solve(problem, rules, SearchBudget.attempts(42, 10_000)));
        assertEquals(0, solved.breakdown().weightedScore());
    }

    @Test
    void syntheticFixtureIsRepeatableFeasibleAndKeepsExtraPlayersIdle() {
        var fixture = BenchmarkFixture.synthetic(34, 18, 2026);
        assertEquals(34, fixture.occurrences().size());
        assertEquals(18, fixture.playerCount());
        assertEquals(12, fixture.occurrences().get(0).parts().size());
        assertEquals(
                Assignment.fromBaseline(fixture),
                Assignment.fromBaseline(BenchmarkFixture.synthetic(34, 18, 2026)));
        assertTrue(
                AssignmentValidator.validate(fixture, Assignment.fromBaseline(fixture)).isEmpty());
        assertTrue(
                fixture.baseline().stream()
                        .filter(row -> row.playerId() >= 22)
                        .allMatch(row -> row.partNumber() == null));
    }

    @Test
    void randomizedStartingMatchingsPreservePinsRequirementsAndEveryScoreField() {
        var fixture = BenchmarkFixture.problem();
        var problem =
                new OptimizationProblem(
                        fixture.setlistId(),
                        fixture.locked(),
                        fixture.bandLayoutId(),
                        fixture.performers(),
                        fixture.occurrences(),
                        fixture.baseline(),
                        List.of(new Pin(101, 1, 10)),
                        fixture.equivalentInstrumentIds());
        var base = rules();
        var rules =
                new OptimizationRules(
                        base.smoothWeight(),
                        base.totalWeight(),
                        base.roleWeight(),
                        base.keepWeight(),
                        base.targetChanges(),
                        base.defaultSwitchMultiplier(),
                        base.playerSwitchMultiplier(),
                        List.of(new InstrumentAffinity(13, 3, 0, true)),
                        base.partPreferences(),
                        base.itemKeepMultiplier(),
                        base.partKeepMultiplier(),
                        base.instrumentPlacements());
        var index = ProblemIndex.build(problem, rules);
        var random = new Random(2026);
        for (int attempt = 0; attempt < 100; attempt++) {
            var repaired = AssignmentRepair.repair(index, random, () -> false);
            assertTrue(repaired.feasible());
            var full =
                    assertInstanceOf(
                            ScoreOutcome.Accepted.class,
                            AssignmentScorer.score(
                                    problem, repaired.state().toAssignment(), rules));
            assertEquals(full.breakdown(), repaired.state().breakdown());
            assertEquals(1, repaired.state().toAssignment().slot(101, 10).partNumber());
        }
    }

    @Test
    void syntheticFixtureCanRunThroughTheBenchmarkCliWithoutPrivateData() {
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        int exit =
                com.aevoreth.abcmm.optimizer.cli.BenchmarkCli.run(
                        new String[] {
                            "--fixture",
                            "synthetic",
                            "--rosters",
                            "12",
                            "--seeds",
                            "0",
                            "--budget-seconds",
                            "0"
                        },
                        new java.io.PrintStream(output),
                        new java.io.PrintStream(errors));
        assertEquals(0, exit, errors.toString());
        assertTrue(output.toString().contains("source: synthetic-fixture"));
        assertTrue(output.toString().contains("heuristic_validated: true"));
        assertTrue(output.toString().contains("optimality: not_claimed"));
    }

    private double enumerate(
            OptimizationProblem problem,
            OptimizationRules rules,
            int song,
            List<AssignedSlot> slots) {
        if (song == problem.occurrences().size()) {
            var score = AssignmentScorer.score(problem, new Assignment(slots), rules);
            return score instanceof ScoreOutcome.Accepted accepted
                    ? accepted.breakdown().weightedScore()
                    : Double.POSITIVE_INFINITY;
        }
        List<List<AssignedSlot>> choices = new ArrayList<>();
        songChoices(
                problem,
                problem.occurrences().get(song),
                0,
                new boolean[problem.playerCount()],
                new ArrayList<>(),
                choices);
        double best = Double.POSITIVE_INFINITY;
        for (List<AssignedSlot> choice : choices) {
            List<AssignedSlot> next = new ArrayList<>(slots);
            next.addAll(choice);
            best = Math.min(best, enumerate(problem, rules, song + 1, next));
        }
        return best;
    }

    private void songChoices(
            OptimizationProblem problem,
            SongOccurrence song,
            int part,
            boolean[] used,
            List<AssignedSlot> slots,
            List<List<AssignedSlot>> choices) {
        if (part == song.parts().size()) {
            List<AssignedSlot> complete = new ArrayList<>(slots);
            for (int p = 0; p < used.length; p++)
                if (!used[p])
                    complete.add(
                            new AssignedSlot(
                                    song.setlistItemId(),
                                    problem.performers().get(p).playerId(),
                                    null,
                                    null));
            choices.add(complete);
            return;
        }
        var required = song.parts().get(part);
        for (int p = 0; p < used.length; p++) {
            var player = problem.performers().get(p);
            if (used[p]
                    || !PhysicalInstrumentEquivalence.owns(
                            player.ownedInstrumentIds(),
                            required.instrumentId(),
                            problem.equivalentInstrumentIds())) continue;
            used[p] = true;
            slots.add(
                    new AssignedSlot(
                            song.setlistItemId(),
                            player.playerId(),
                            required.partNumber(),
                            required.instrumentId()));
            songChoices(problem, song, part + 1, used, slots, choices);
            slots.remove(slots.size() - 1);
            used[p] = false;
        }
    }
}
