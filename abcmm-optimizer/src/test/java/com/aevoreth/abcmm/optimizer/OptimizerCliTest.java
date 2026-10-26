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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        assertTrue(out.contains("out_of_layout_assignment"));
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
