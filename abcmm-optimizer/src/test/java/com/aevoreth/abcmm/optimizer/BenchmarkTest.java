package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.optimizer.cli.BenchmarkCli;
import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;

class BenchmarkTest {

    @TempDir
    Path tempDir;

    @Test
    void expansionKeepsTheBaselineIdleAndPrefersLibraryPlayers() {
        OptimizationProblem problem = BenchmarkFixture.problem();
        ScoreBreakdown before = score(problem);
        RosterExpander.RosterExpansion synthetic = RosterExpander.expand(problem, problem.playerCount() + 2, List.of());
        assertEquals(RosterExpander.SYNTHETIC_EXTRAS, synthetic.tag());
        assertEquals(0, synthetic.libraryExtras());
        assertEquals(2, synthetic.syntheticExtras());
        assertEquals(before.totalChanges(), score(synthetic.problem()).totalChanges());
        assertEquals(before.histogram(), score(synthetic.problem()).histogram());
        int instruments = problem.instrumentCount();
        for (Performer performer : synthetic.problem().performers()) {
            if (performer.playerId() < RosterExpander.SYNTHETIC_ID_START) {
                continue;
            }
            assertFalse(performer.ownedInstrumentIds().isEmpty());
            assertTrue(performer.ownedInstrumentIds().size() < instruments);
        }
        for (BaselineAssignment row : synthetic.problem().baseline()) {
            if (row.playerId() >= RosterExpander.SYNTHETIC_ID_START) {
                assertNull(row.partNumber());
            }
        }

        Performer library = new Performer(50, Set.of(1L, 2L));
        RosterExpander.RosterExpansion mixed = RosterExpander.expand(
                problem, problem.playerCount() + 2, List.of(library));
        assertEquals(RosterExpander.MIXED_EXTRAS, mixed.tag());
        assertEquals(1, mixed.libraryExtras());
        assertEquals(1, mixed.syntheticExtras());
        assertEquals(50L, mixed.problem().performers().get(problem.playerCount()).playerId());
        assertTrue(mixed.problem().performers().get(problem.playerCount() + 1).playerId()
                >= RosterExpander.SYNTHETIC_ID_START);

        RosterExpander.RosterExpansion onlyLibrary = RosterExpander.expand(
                problem, problem.playerCount() + 1, List.of(library));
        assertEquals(RosterExpander.LIBRARY_EXTRAS, onlyLibrary.tag());
        assertEquals(0, onlyLibrary.syntheticExtras());
        assertEquals(RosterExpander.LAYOUT, RosterExpander.expand(problem, problem.playerCount(), List.of(library)).tag());
        assertThrows(IllegalArgumentException.class, () -> RosterExpander.expand(problem, problem.playerCount() - 1, List.of()));
    }

    @Test
    void greedyIsFeasibleAndRepeatable() {
        OptimizationProblem problem = BenchmarkFixture.problem();
        GreedyBaseline.Result first = GreedyBaseline.assign(problem);
        GreedyBaseline.Result second = GreedyBaseline.assign(problem);
        GreedyBaseline.Result.Assigned assigned = assertInstanceOf(GreedyBaseline.Result.Assigned.class, first);
        GreedyBaseline.Result.Assigned again = assertInstanceOf(GreedyBaseline.Result.Assigned.class, second);
        assertEquals(assigned.assignment(), again.assignment());
        assertEquals(0, AssignmentValidator.validate(problem, assigned.assignment()).size());
    }

    @Test
    void variantsPinTheBaselineAndRejectAnUnownedPin() {
        OptimizationProblem problem = BenchmarkFixture.problem();
        BenchmarkScenarios.Scenario pinned = BenchmarkScenarios.all(problem).get(3);
        assertEquals(BenchmarkScenarios.PINNED, pinned.name());
        assertEquals(2, pinned.problem().pins().size());
        BenchmarkScenarios.Scenario restrictive = BenchmarkScenarios.all(problem).get(4);
        assertEquals("restrictive_ownership=reduced", restrictive.note());
        assertEquals(0, AssignmentValidator.validate(
                restrictive.problem(), Assignment.fromBaseline(restrictive.problem())).size());
        BenchmarkScenarios.Scenario infeasible = BenchmarkScenarios.all(problem).get(5);
        assertFalse(infeasible.search());
        SearchOutcome outcome = AssignmentSolver.solve(
                infeasible.problem(),
                infeasible.rules(),
                SearchBudget.of(0, Duration.ZERO));
        assertInstanceOf(SearchOutcome.Infeasible.class, outcome);
    }

    @Test
    void publicReportIsDeterministicAndOmitsNames() {
        String first = stable(report(BenchmarkFixture.problem(), List.of(), List.of(4, 6)));
        String second = stable(report(BenchmarkFixture.problem(), List.of(), List.of(4, 6)));
        assertEquals(first, second);
        assertTrue(first.contains("roster_tag: layout"));
        assertTrue(first.contains("roster_tag: synthetic-extras"));
        assertTrue(first.contains("checkpoint:"));
        assertTrue(first.contains("heuristic_evaluated_moves: 0"));
        assertTrue(first.contains("go_no_go: not_measured"));
        assertTrue(first.contains("database_unchanged: not_applicable"));
        assertTrue(first.contains("optimality: not_claimed"));
        assertFalse(first.contains("heuristic_valid: false"));
        assertFalse(first.contains("greedy_valid: false"));
        assertFalse(first.contains("Alice"));
        assertFalse(first.contains("player_id"));
        int from = 0;
        int infeasibleCells = 0;
        while ((from = first.indexOf("variant=infeasible", from)) >= 0) {
            int next = first.indexOf("\ncell:", from);
            String section = next < 0 ? first.substring(from) : first.substring(from, next);
            assertTrue(section.contains("infeasible_codes:"));
            assertTrue(section.contains("greedy: skipped"));
            assertFalse(section.contains("weighted_score"));
            infeasibleCells++;
            from = next < 0 ? first.length() : next;
        }
        assertEquals(2, infeasibleCells);
    }

    @Test
    void libraryExtrasComeFromPlayersOffTheLayout() throws Exception {
        Path database = tempDir.resolve("extras.sqlite");
        long setlistId;
        long cara;
        long dana;
        try (SyntheticLibrary library = SyntheticLibrary.create(database)) {
            dana = library.players.addPlayer("Dana", null, null);
            library.players.setPlayerInstrument(dana, library.lute, true, false, null);
            library.players.setPlayerInstrument(library.cara, library.fiddle, true, false, null);
            cara = library.cara;
            setlistId = library.feasibleId;
        }
        SqliteOptimizationLoader loader = new SqliteOptimizationLoader();
        LoadOutcome outcome = loader.load(new LoadRequest(database, setlistId, null, null, null));
        List<Performer> extras = loader.loadEligibleExtras(database, outcome.problem());
        assertEquals(List.of(cara), extras.stream().map(Performer::playerId).toList());
        assertFalse(extras.get(0).ownedInstrumentIds().isEmpty());
        assertTrue(outcome.problem().toString().isEmpty() || !outcome.problem().toString().contains("Dana"));
    }

    @Test
    void cliLeavesTheDatabaseUnchanged() throws Exception {
        Path database = tempDir.resolve("benchmark-cli.sqlite");
        long setlistId;
        try (SyntheticLibrary library = SyntheticLibrary.create(database)) {
            setlistId = library.feasibleId;
        }
        byte[] before = Files.readAllBytes(database);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = BenchmarkCli.run(
                new String[] {
                        "--database", database.toString(),
                        "--setlist", Long.toString(setlistId),
                        "--rosters", "2",
                        "--seeds", "0",
                        "--budget-seconds", "0",
                        "--checkpoints", "60",
                        "--cold-warm", "false"
                },
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        String out = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, code);
        assertTrue(out.contains("database_unchanged: true"));
        assertTrue(out.contains("source: database"));
        assertFalse(out.contains("Alice"));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).contains("Alice"));
        assertEquals(before.length, Files.readAllBytes(database).length);
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(database)));

        ByteArrayOutputStream rejected = new ByteArrayOutputStream();
        int below = BenchmarkCli.run(
                new String[] {"--fixture", "public", "--rosters", "2", "--budget-seconds", "0", "--cold-warm", "false"},
                new PrintStream(rejected, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertEquals(1, below);
        assertEquals("", rejected.toString(StandardCharsets.UTF_8));
    }

    @Test
    void changedDatabaseForcesNoGo() {
        String report = "database_unchanged: pending\ngo_no_go: not_measured\ngo_no_go_reason: not_reference\n";
        String checked = BenchmarkCli.applyDatabaseCheck(report, false);
        assertTrue(checked.contains("database_unchanged: false"));
        assertTrue(checked.contains("go_no_go: no-go"));
        assertTrue(checked.contains("go_no_go_reason: database_changed,not_reference"));
    }

    private static String report(OptimizationProblem problem, List<Performer> extras, List<Integer> rosters) {
        return BenchmarkRunner.run(new BenchmarkRunner.BenchmarkRequest(
                "public-fixture",
                problem,
                extras,
                rosters,
                List.of(0L),
                Duration.ZERO,
                List.of(60),
                false,
                Duration.ZERO,
                null));
    }

    private static String stable(String report) {
        return report.lines()
                .filter(line -> !line.startsWith("heuristic_elapsed_ms:"))
                .filter(line -> !line.startsWith("heuristic_best_ms:"))
                .collect(Collectors.joining("\n"));
    }

    private static ScoreBreakdown score(OptimizationProblem problem) {
        ScoreOutcome outcome = AssignmentScorer.score(
                problem,
                Assignment.fromBaseline(problem),
                OptimizationRules.defaults());
        return assertInstanceOf(ScoreOutcome.Accepted.class, outcome).breakdown();
    }
}
