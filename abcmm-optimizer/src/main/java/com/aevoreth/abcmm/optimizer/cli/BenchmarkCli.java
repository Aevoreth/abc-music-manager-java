package com.aevoreth.abcmm.optimizer.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.optimizer.BenchmarkFixture;
import com.aevoreth.abcmm.optimizer.BenchmarkRunner;
import com.aevoreth.abcmm.optimizer.Diagnostic;
import com.aevoreth.abcmm.optimizer.DiagnosticSeverity;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.LoadRequest;
import com.aevoreth.abcmm.optimizer.OptimizationProblem;
import com.aevoreth.abcmm.optimizer.Performer;
import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;

/**
 * Read-only benchmark entry point. The report is sanitized; the database is not modified.
 */
public final class BenchmarkCli {

    static final String USAGE =
            "usage: BenchmarkCli (--database <path> --setlist <id> | --fixture public|synthetic)"
                + " [--rosters 12,16,18] [--seeds 0,1,42] [--budget-seconds <n>] [--checkpoints"
                + " 1,2,5,10,30,60] [--cold-warm true|false] [--warmup-seconds <n>] [--out <path>]";

    private static final List<Integer> DEFAULT_ROSTERS = List.of(12, 16, 18);
    private static final List<Long> DEFAULT_SEEDS = List.of(0L, 1L, 42L);
    private static final List<Integer> DEFAULT_CHECKPOINTS = List.of(1, 2, 5, 10, 30, 60);

    private BenchmarkCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Parsed parsed = parse(args);
        if (parsed.error() != null) {
            err.println(parsed.error());
            err.println(USAGE);
            return 1;
        }
        OptimizationProblem problem;
        List<Performer> extras = List.of();
        long size = -1;
        FileTime modified = null;
        if (parsed.fixture()) {
            problem =
                    parsed.synthetic()
                            ? BenchmarkFixture.synthetic(34, 12, 2026)
                            : BenchmarkFixture.problem();
        } else {
            try {
                size = Files.size(parsed.database());
                modified = Files.getLastModifiedTime(parsed.database());
            } catch (Exception ex) {
                err.println("diagnostic: INTEGRITY missing_database");
                return 1;
            }
            SqliteOptimizationLoader loader = new SqliteOptimizationLoader();
            LoadOutcome outcome = loader.load(new LoadRequest(parsed.database(), parsed.setlistId(), null, null, null));
            if (outcome.problem() == null || !outcome.report().integrityOk()) {
                for (Diagnostic diagnostic : outcome.report().diagnostics()) {
                    if (diagnostic.severity() == DiagnosticSeverity.INTEGRITY) {
                        err.println("diagnostic: INTEGRITY " + diagnostic.code());
                    }
                }
                return 1;
            }
            problem = outcome.problem();
            if (needsExtras(problem, parsed.rosters())) {
                try {
                    extras = loader.loadEligibleExtras(parsed.database(), problem);
                } catch (LibraryException ex) {
                    err.println("diagnostic: INTEGRITY database_error");
                    return 1;
                }
            }
        }
        for (int roster : parsed.rosters()) {
            if (roster < problem.playerCount()) {
                err.println("invalid roster: below the layout");
                return 1;
            }
        }
        String report =
                BenchmarkRunner.run(
                        new BenchmarkRunner.BenchmarkRequest(
                                parsed.fixture()
                                        ? (parsed.synthetic()
                                                ? "synthetic-fixture"
                                                : "public-fixture")
                                        : "database",
                                problem,
                                extras,
                                parsed.rosters(),
                                parsed.seeds(),
                                Duration.ofSeconds(parsed.budgetSeconds()),
                                parsed.checkpoints(),
                                parsed.coldWarm(),
                                Duration.ofSeconds(parsed.warmupSeconds()),
                                err));
        int exit = 0;
        if (!parsed.fixture()) {
            boolean unchanged = unchanged(parsed.database(), size, modified);
            report = applyDatabaseCheck(report, unchanged);
            if (!unchanged) {
                exit = 2;
            }
        }
        out.print(report);
        if (parsed.out() != null) {
            try {
                Files.writeString(parsed.out(), report, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                err.println("invalid --out");
                return 1;
            }
        }
        return exit;
    }

    public static String applyDatabaseCheck(String report, boolean unchanged) {
        String checked = report.replace(BenchmarkRunner.DATABASE_PENDING, "database_unchanged: " + unchanged);
        if (unchanged || !report.contains(BenchmarkRunner.DATABASE_PENDING)) {
            return checked;
        }
        checked = checked.replace("go_no_go: go", "go_no_go: no-go")
                .replace("go_no_go: not_measured", "go_no_go: no-go");
        return checked.replace("go_no_go_reason: ", "go_no_go_reason: database_changed,");
    }

    private static boolean unchanged(Path database, long size, FileTime modified) {
        try {
            return Files.size(database) == size && Files.getLastModifiedTime(database).equals(modified);
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean needsExtras(OptimizationProblem problem, List<Integer> rosters) {
        for (int roster : rosters) {
            if (roster > problem.playerCount()) {
                return true;
            }
        }
        return false;
    }

    private static Parsed parse(String[] args) {
        Path database = null;
        Long setlistId = null;
        boolean fixture = false;
        boolean synthetic = false;
        List<Integer> rosters = null;
        List<Long> seeds = null;
        Long budgetSeconds = null;
        List<Integer> checkpoints = null;
        Boolean coldWarm = null;
        Long warmupSeconds = null;
        Path out = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                return Parsed.error("unknown argument: " + arg);
            }
            if (!seen.add(arg)) {
                return Parsed.error("duplicate argument: " + arg);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                return Parsed.error("missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--database" -> {
                    if (value.isBlank()) {
                        return Parsed.error("invalid --database");
                    }
                    database = Path.of(value);
                }
                case "--setlist" -> {
                    setlistId = parsePositiveLong(value);
                    if (setlistId == null) {
                        return Parsed.error("invalid --setlist: must be a positive integer");
                    }
                }
                case "--fixture" -> {
                    if (!value.equals("public") && !value.equals("synthetic")) {
                        return Parsed.error("invalid --fixture");
                    }
                    fixture = true;
                    synthetic = value.equals("synthetic");
                }
                case "--rosters" -> {
                    rosters = parsePositiveInts(value);
                    if (rosters == null) {
                        return Parsed.error("invalid --rosters");
                    }
                }
                case "--seeds" -> {
                    seeds = parseNonNegativeLongs(value);
                    if (seeds == null) {
                        return Parsed.error("invalid --seeds");
                    }
                }
                case "--budget-seconds" -> {
                    budgetSeconds = parseNonNegativeLong(value);
                    if (budgetSeconds == null) {
                        return Parsed.error("invalid --budget-seconds: must be a nonnegative integer");
                    }
                }
                case "--checkpoints" -> {
                    checkpoints = parseNonNegativeInts(value);
                    if (checkpoints == null) {
                        return Parsed.error("invalid --checkpoints");
                    }
                }
                case "--cold-warm" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        return Parsed.error("invalid --cold-warm");
                    }
                    coldWarm = Boolean.valueOf(value);
                }
                case "--warmup-seconds" -> {
                    warmupSeconds = parseNonNegativeLong(value);
                    if (warmupSeconds == null) {
                        return Parsed.error("invalid --warmup-seconds");
                    }
                }
                case "--out" -> {
                    if (value.isBlank()) {
                        return Parsed.error("invalid --out");
                    }
                    out = Path.of(value);
                }
                default -> {
                    return Parsed.error("unknown argument: " + arg);
                }
            }
        }
        if (fixture == (database != null)) {
            return Parsed.error("choose either --fixture public or --database and --setlist");
        }
        if (!fixture && setlistId == null) {
            return Parsed.error("missing --setlist");
        }
        if (fixture && setlistId != null) {
            return Parsed.error("invalid --setlist");
        }
        if (out != null && database != null
                && out.toAbsolutePath().normalize().equals(database.toAbsolutePath().normalize())) {
            return Parsed.error("invalid --out");
        }
        if (out != null) {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                return Parsed.error("invalid --out");
            }
        }
        if (rosters == null) {
            rosters = DEFAULT_ROSTERS;
        }
        if (seeds == null) {
            seeds = DEFAULT_SEEDS;
        }
        if (checkpoints == null) {
            checkpoints = DEFAULT_CHECKPOINTS;
        }
        if (budgetSeconds == null) {
            budgetSeconds = fixture ? 0L : 60L;
        }
        if (coldWarm == null) {
            coldWarm = !fixture && budgetSeconds > 0;
        }
        if (warmupSeconds == null) {
            warmupSeconds = coldWarm ? 2L : 0L;
        }
        return Parsed.ok(
                database,
                setlistId == null ? 0 : setlistId,
                fixture,
                synthetic,
                rosters,
                seeds,
                budgetSeconds,
                checkpoints,
                coldWarm,
                warmupSeconds,
                out);
    }

    private static List<Integer> parsePositiveInts(String value) {
        List<Integer> parsed = parseNonNegativeInts(value);
        if (parsed == null) {
            return null;
        }
        for (int item : parsed) {
            if (item <= 0) {
                return null;
            }
        }
        return parsed;
    }

    private static List<Integer> parseNonNegativeInts(String value) {
        List<Long> parsed = parseNonNegativeLongs(value);
        if (parsed == null) {
            return null;
        }
        List<Integer> values = new ArrayList<>();
        for (long item : parsed) {
            if (item > Integer.MAX_VALUE) {
                return null;
            }
            values.add((int) item);
        }
        return values;
    }

    private static List<Long> parseNonNegativeLongs(String value) {
        if (value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        List<Long> values = new ArrayList<>();
        for (String part : parts) {
            Long parsed = parseNonNegativeLong(part.trim());
            if (parsed == null) {
                return null;
            }
            values.add(parsed);
        }
        return values.isEmpty() ? null : values;
    }

    private static Long parsePositiveLong(String value) {
        Long parsed = parseNonNegativeLong(value);
        if (parsed == null || parsed == 0L) {
            return null;
        }
        return parsed;
    }

    private static Long parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Parsed(
            Path database,
            long setlistId,
            boolean fixture,
            boolean synthetic,
            List<Integer> rosters,
            List<Long> seeds,
            long budgetSeconds,
            List<Integer> checkpoints,
            boolean coldWarm,
            long warmupSeconds,
            Path out,
            String error) {

        private static Parsed ok(
                Path database,
                long setlistId,
                boolean fixture,
                boolean synthetic,
                List<Integer> rosters,
                List<Long> seeds,
                long budgetSeconds,
                List<Integer> checkpoints,
                boolean coldWarm,
                long warmupSeconds,
                Path out) {
            return new Parsed(
                    database,
                    setlistId,
                    fixture,
                    synthetic,
                    rosters,
                    seeds,
                    budgetSeconds,
                    checkpoints,
                    coldWarm,
                    warmupSeconds,
                    out,
                    null);
        }

        private static Parsed error(String error) {
            return new Parsed(
                    null, 0, false, false, List.of(), List.of(), 0, List.of(), false, 0, null,
                    error);
        }
    }
}
