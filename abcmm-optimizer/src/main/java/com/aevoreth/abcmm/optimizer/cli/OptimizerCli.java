package com.aevoreth.abcmm.optimizer.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.optimizer.AssignmentSolver;
import com.aevoreth.abcmm.optimizer.Diagnostic;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.LoadRequest;
import com.aevoreth.abcmm.optimizer.ProblemReport;
import com.aevoreth.abcmm.optimizer.SearchBudget;
import com.aevoreth.abcmm.optimizer.SearchOutcome;
import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;

/**
 * Read-only snapshot CLI. Prints ids and diagnostics, not library names.
 */
public final class OptimizerCli {

    static final String USAGE = "usage: OptimizerCli --database <path> --setlist <id> "
            + "[--seed <n>] [--budget-seconds <n>] [--players <n>] [--search]";

    private OptimizerCli() {
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
        LoadOutcome outcome = new SqliteOptimizationLoader().load(parsed.request());
        String report = ProblemReport.format(outcome);
        if (!parsed.search()) {
            out.print(report);
            return outcome.report().exitCode();
        }
        if (outcome.problem() == null || !outcome.report().integrityOk()) {
            out.print(report);
            return 1;
        }
        long seed = outcome.seed() == null ? 0L : outcome.seed();
        SearchOutcome searched = AssignmentSolver.solve(
                outcome.problem(),
                outcome.rules(),
                SearchBudget.of(seed, searchBudget(outcome)));
        out.print(report);
        if (searched instanceof SearchOutcome.Solved solved) {
            out.println("proposal_stop: " + solved.reason().reportName());
            out.println("proposal_moves: " + solved.acceptedMoves());
            out.print(solved.breakdown().format());
            return 0;
        }
        out.println("proposal: infeasible");
        for (Diagnostic diagnostic : ((SearchOutcome.Infeasible) searched).diagnostics()) {
            out.println(diagnostic.format());
        }
        return 2;
    }

    private static Parsed parse(String[] args) {
        Path database = null;
        Long setlistId = null;
        Long seed = null;
        Long budgetSeconds = null;
        Integer players = null;
        boolean search = false;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                return Parsed.error("unknown argument: " + arg);
            }
            if (!seen.add(arg)) {
                return Parsed.error("duplicate argument: " + arg);
            }
            if (arg.equals("--search")) {
                search = true;
                continue;
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
                case "--seed" -> {
                    seed = parseNonNegativeLong(value);
                    if (seed == null) {
                        return Parsed.error("invalid --seed: must be a nonnegative integer");
                    }
                }
                case "--budget-seconds" -> {
                    budgetSeconds = parseNonNegativeLong(value);
                    if (budgetSeconds == null) {
                        return Parsed.error("invalid --budget-seconds: must be a nonnegative integer");
                    }
                }
                case "--players" -> {
                    players = parseNonNegativeInt(value);
                    if (players == null) {
                        return Parsed.error("invalid --players: must be a nonnegative integer");
                    }
                }
                default -> {
                    return Parsed.error("unknown argument: " + arg);
                }
            }
        }
        if (database == null) {
            return Parsed.error("missing --database");
        }
        if (setlistId == null) {
            return Parsed.error("missing --setlist");
        }
        return Parsed.ok(new LoadRequest(database, setlistId, seed, budgetSeconds, players), search);
    }

    /**
     * {@code --budget-seconds} wins. Otherwise the saved band preset is used.
     */
    public static Duration searchBudget(LoadOutcome outcome) {
        if (outcome.budgetSeconds() != null) {
            return Duration.ofSeconds(outcome.budgetSeconds());
        }
        SearchBudgetPreset preset = outcome.searchBudget() == null
                ? SearchBudgetPreset.NORMAL
                : outcome.searchBudget();
        return preset.budget();
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

    private static Integer parseNonNegativeInt(String value) {
        Long parsed = parseNonNegativeLong(value);
        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }
        return parsed.intValue();
    }

    private record Parsed(LoadRequest request, boolean search, String error) {
        private static Parsed ok(LoadRequest request, boolean search) {
            return new Parsed(request, search, null);
        }

        private static Parsed error(String error) {
            return new Parsed(null, false, error);
        }
    }
}
