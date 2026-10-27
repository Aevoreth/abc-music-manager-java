package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.optimizer.cli.OptimizerCli;
import com.aevoreth.abcmm.storage.SqliteDatabase;

class OptimizerCliTest {

    @TempDir
    Path tempDir;

    @Test
    void cliReportsIdsOnlyAndDoesNotWriteTheDatabase() throws Exception {
        Path database = tempDir.resolve("cli-setlist.sqlite");
        long setlistId;
        try (SyntheticLibrary library = SyntheticLibrary.create(database)) {
            setlistId = library.feasibleId;
        }
        byte[] before = Files.readAllBytes(database);
        var filesBefore = listNames(tempDir);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = OptimizerCli.run(
                new String[] {
                        "--database", database.toString(),
                        "--setlist", Long.toString(setlistId),
                        "--seed", "42",
                        "--budget-seconds", "10",
                        "--players", "2"
                },
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        String out = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, code);
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
        assertTrue(out.contains("setlist_id: " + setlistId));
        assertTrue(out.contains("locked: true"));
        assertTrue(out.contains("items: 2"));
        assertTrue(out.contains("parts: 4"));
        assertTrue(out.contains("players: 2"));
        assertTrue(out.contains("transitions: 1"));
        assertTrue(out.contains("seed: 42"));
        assertTrue(out.contains("budget_seconds: 10"));
        assertTrue(out.contains("requested_players: 2"));
        assertTrue(out.contains("integrity: ok"));
        assertTrue(out.contains("baseline: ok"));
        assertTrue(out.contains("changes: 2"));
        assertTrue(out.contains("worst_transition: 2"));
        assertTrue(out.contains("overload: 0"));
        assertTrue(out.contains("weighted_score: 2.0"));
        assertTrue(out.contains("histogram: 2=1"));
        assertTrue(out.contains("transition: 0 changes=2"));
        assertTrue(out.contains("out_of_layout_assignment"));
        assertFalse(out.contains("proposal_stop:"));
        assertFalse(out.contains("Alice"));
        assertFalse(out.contains("Fixture Tune"));
        assertFalse(out.contains("Feasible Set"));
        assertArrayEquals(before, Files.readAllBytes(database));
        var filesAfter = listNames(tempDir);
        assertEquals(filesBefore, filesAfter);

        try (SqliteDatabase readOnly = SqliteDatabase.openReadOnly(database)) {
            assertThrows(SQLException.class, () -> {
                try (Statement statement = readOnly.connection().createStatement()) {
                    statement.executeUpdate("UPDATE Setlist SET name = 'mutated'");
                }
            });
        }
        assertArrayEquals(before, Files.readAllBytes(database));
    }

    @Test
    void searchWithZeroBudgetAppendsTheBaselineProposal() throws Exception {
        Path database = tempDir.resolve("cli-search.sqlite");
        long setlistId;
        try (SyntheticLibrary library = SyntheticLibrary.create(database)) {
            setlistId = library.feasibleId;
        }
        byte[] before = Files.readAllBytes(database);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = OptimizerCli.run(
                new String[] {
                        "--database", database.toString(),
                        "--setlist", Long.toString(setlistId),
                        "--search",
                        "--budget-seconds", "0"
                },
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        String out = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, code);
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
        assertTrue(out.contains("baseline: ok"));
        assertTrue(out.contains("proposal_stop: budget"));
        assertTrue(out.contains("proposal_moves: 0"));
        assertEquals(2, count(out, "changes: 2"));
        assertEquals(2, count(out, "weighted_score: 2.0"));
        assertArrayEquals(before, Files.readAllBytes(database));
    }

    @Test
    void budgetFlagOverridesTheSavedPreset() {
        FeasibilityReport report = new FeasibilityReport(List.of());
        LoadOutcome flagged = new LoadOutcome(
                1, 0L, 0L, null, null, report, null, SearchBudgetPreset.THOROUGH);
        assertEquals(Duration.ZERO, OptimizerCli.searchBudget(flagged));
        LoadOutcome preset = new LoadOutcome(
                1, 0L, null, null, null, report, null, SearchBudgetPreset.QUICK);
        assertEquals(Duration.ofSeconds(2), OptimizerCli.searchBudget(preset));
        LoadOutcome missing = new LoadOutcome(1, null, null, null, null, report, null, null);
        assertEquals(Duration.ofSeconds(10), OptimizerCli.searchBudget(missing));
    }

    private static int count(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = text.indexOf(token, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + token.length();
        }
    }

    private static java.util.Set<String> listNames(Path directory) throws Exception {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    @Test
    void invalidArgumentsDoNotOpenAReport() throws Exception {
        Path database = tempDir.resolve("unused.sqlite");
        Files.writeString(database, "not a database");
        byte[] before = Files.readAllBytes(database);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = OptimizerCli.run(
                new String[] {"--database", database.toString(), "--seed", "-1", "--setlist", "1"},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        assertEquals(1, code);
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("invalid --seed"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("usage:"));
        assertArrayEquals(before, Files.readAllBytes(database));
    }
}
