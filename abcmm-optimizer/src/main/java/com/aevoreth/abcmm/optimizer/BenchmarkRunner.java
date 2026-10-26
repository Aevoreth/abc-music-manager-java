package com.aevoreth.abcmm.optimizer;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Read-only benchmark report. The text uses counts and ids already present on the
 * problem. It does not print names, and it does not claim an optimum.
 */
public final class BenchmarkRunner {

    public static final String DATABASE_PENDING = "database_unchanged: pending";

    private static final Map<Integer, Integer> REFERENCE_HISTOGRAM = Map.of(2, 2, 3, 4, 4, 9, 5, 12, 6, 6);

    private BenchmarkRunner() {
    }

    public static String run(BenchmarkRequest request) {
        Objects.requireNonNull(request, "request");
        StringBuilder text = new StringBuilder();
        appendEnvironment(text, request);
        OptimizationProblem layout = request.problem();
        ScoreOutcome layoutScore = AssignmentScorer.score(
                layout,
                Assignment.fromBaseline(layout),
                OptimizationRules.defaults());
        boolean referenceShape = referenceShape(layout);
        boolean referenceMatch = referenceShape && matchesReference(layoutScore);
        appendLayout(text, layout, layoutScore, referenceShape, referenceMatch);

        List<Observation> observations = new ArrayList<>();
        Flags flags = new Flags();
        if (request.coldWarm()) {
            progress(request.progress(), "running roster=" + layout.playerCount()
                    + " variant=" + BenchmarkScenarios.BALANCED + " seed=0 jvm=cold");
            runCell(text, observations, flags, request, expand(layout, layout.playerCount(), request.libraryExtras()),
                    BenchmarkScenarios.balanced(layout), 0L, "cold");
            warmup(request);
        }
        for (int roster : request.rosterSizes()) {
            RosterExpander.RosterExpansion expansion = expand(layout, roster, request.libraryExtras());
            for (BenchmarkScenarios.Scenario scenario : BenchmarkScenarios.all(expansion.problem())) {
                for (long seed : request.seeds()) {
                    progress(request.progress(), "running roster=" + roster
                            + " variant=" + scenario.name()
                            + " seed=" + seed
                            + " jvm=" + jvm(request));
                    runCell(text, observations, flags, request, expansion, scenario, seed, jvm(request));
                }
            }
        }
        appendSummaries(text, observations);
        appendVerdict(text, request, referenceShape, referenceMatch, observations, flags);
        return text.toString();
    }

    private static void runCell(
            StringBuilder text,
            List<Observation> observations,
            Flags flags,
            BenchmarkRequest request,
            RosterExpander.RosterExpansion expansion,
            BenchmarkScenarios.Scenario scenario,
            long seed,
            String jvm) {
        text.append('\n');
        line(text, "cell", "roster=" + expansion.problem().playerCount()
                + " variant=" + scenario.name()
                + " seed=" + seed
                + " jvm=" + jvm);
        line(text, "roster_tag", expansion.tag());
        line(text, "library_extras", Integer.toString(expansion.libraryExtras()));
        line(text, "synthetic_extras", Integer.toString(expansion.syntheticExtras()));
        if (!scenario.note().isBlank()) {
            line(text, "scenario_note", scenario.note());
        }
        if (!scenario.search()) {
            flags.sawInfeasible = true;
            SearchOutcome outcome = AssignmentSolver.solve(
                    scenario.problem(),
                    scenario.rules(),
                    SearchBudget.of(seed, Duration.ZERO));
            if (outcome instanceof SearchOutcome.Infeasible infeasible) {
                line(text, "heuristic", "infeasible");
                line(text, "infeasible_codes", codes(infeasible.diagnostics()));
                line(text, "greedy", "skipped");
            } else {
                flags.infeasibleAccepted = true;
                line(text, "heuristic", "feasible");
                line(text, "greedy", "skipped");
            }
            return;
        }

        Metrics baselineMetrics = appendMetrics(
                text,
                "baseline",
                scenario.problem(),
                scenario.rules(),
                Assignment.fromBaseline(scenario.problem()));
        GreedyBaseline.Result greedy = GreedyBaseline.assign(scenario.problem());
        Metrics greedyMetrics = null;
        if (greedy instanceof GreedyBaseline.Result.Assigned assigned) {
            greedyMetrics = appendMetrics(text, "greedy", scenario.problem(), scenario.rules(), assigned.assignment());
        } else {
            line(text, "greedy_valid", "false");
            line(text, "greedy_codes", codes(((GreedyBaseline.Result.Failed) greedy).diagnostics()));
        }

        long started = System.nanoTime();
        Trace trace = new Trace(started);
        SearchOutcome outcome = AssignmentSolver.solve(
                scenario.problem(),
                scenario.rules(),
                new SearchBudget(seed, request.budget(), null, trace));
        if (!(outcome instanceof SearchOutcome.Solved solved)) {
            flags.searchFailed = true;
            line(text, "heuristic_valid", "false");
            line(text, "heuristic_codes", codes(((SearchOutcome.Infeasible) outcome).diagnostics()));
            return;
        }
        List<Diagnostic> problems = AssignmentValidator.validate(scenario.problem(), solved.assignment());
        Metrics heuristicMetrics = appendHeuristic(text, scenario.problem(), solved, trace);
        boolean validated = problems.isEmpty() && heuristicMetrics != null;
        line(text, "heuristic_validated", Boolean.toString(validated));
        if (!problems.isEmpty()) {
            line(text, "heuristic_validator_codes", codes(problems));
        }
        if (!validated) {
            flags.assignmentInvalid = true;
        }
        appendCheckpoints(text, request, trace, solved);
        if (heuristicMetrics != null && baselineMetrics != null) {
            ScoreBreakdown at10 = atCheckpoint(trace.samples, solved, 10);
            observations.add(new Observation(
                    expansion.problem().playerCount(),
                    scenario.name(),
                    jvm,
                    heuristicMetrics.changes,
                    heuristicMetrics.worst,
                    heuristicMetrics.overload,
                    at10.worstTransition(),
                    at10.overload(),
                    baselineMetrics.changes,
                    baselineMetrics.worst,
                    baselineMetrics.overload,
                    greedyMetrics == null ? null : greedyMetrics.changes,
                    greedyMetrics == null ? null : greedyMetrics.worst,
                    greedyMetrics == null ? null : greedyMetrics.overload));
        }
    }

    private static Metrics appendHeuristic(
            StringBuilder text,
            OptimizationProblem problem,
            SearchOutcome.Solved solved,
            Trace trace) {
        line(text, "heuristic_stop", solved.reason().reportName());
        line(text, "heuristic_elapsed_ms", Long.toString(solved.elapsedNanos() / 1_000_000L));
        line(text, "heuristic_accepted_moves", Integer.toString(solved.acceptedMoves()));
        line(text, "heuristic_evaluated_moves", Long.toString(solved.evaluatedMoves()));
        line(text, "heuristic_moves_per_second", rate(solved.evaluatedMoves(), solved.elapsedNanos()));
        line(text, "heuristic_first_improvement_ms", firstImprovement(trace));
        line(text, "heuristic_best_ms", trace.samples.isEmpty()
                ? "none"
                : Long.toString(trace.samples.get(trace.samples.size() - 1).elapsedNanos() / 1_000_000L));
        return appendBreakdown(text, "heuristic", problem, solved.assignment(), solved.breakdown());
    }

    private static void appendCheckpoints(
            StringBuilder text,
            BenchmarkRequest request,
            Trace trace,
            SearchOutcome.Solved solved) {
        for (int seconds : request.checkpointSeconds()) {
            long limit = seconds * 1_000_000_000L;
            boolean stoppedBefore = solved.elapsedNanos() < limit;
            ScoreBreakdown breakdown = atCheckpoint(trace.samples, solved, seconds);
            text.append("checkpoint: seconds=").append(seconds)
                    .append(" stopped_before=").append(stoppedBefore)
                    .append(" changes=").append(breakdown.totalChanges())
                    .append(" worst=").append(breakdown.worstTransition())
                    .append(" overload=").append(breakdown.overload())
                    .append(" above_target=").append(aboveTarget(breakdown))
                    .append(" weighted_score=").append(breakdown.weightedScore())
                    .append('\n');
        }
    }

    private static ScoreBreakdown atCheckpoint(List<Sample> samples, SearchOutcome.Solved solved, int seconds) {
        long limit = seconds * 1_000_000_000L;
        if (solved.elapsedNanos() < limit) {
            return solved.breakdown();
        }
        ScoreBreakdown chosen = null;
        for (Sample sample : samples) {
            if (sample.elapsedNanos() <= limit) {
                chosen = sample.breakdown();
            }
        }
        return chosen == null ? solved.breakdown() : chosen;
    }

    private static Metrics appendMetrics(
            StringBuilder text,
            String prefix,
            OptimizationProblem problem,
            OptimizationRules rules,
            Assignment assignment) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, assignment, rules);
        if (!(outcome instanceof ScoreOutcome.Accepted accepted)) {
            line(text, prefix + "_valid", "false");
            line(text, prefix + "_codes", codes(((ScoreOutcome.Rejected) outcome).diagnostics()));
            return null;
        }
        return appendBreakdown(text, prefix, problem, assignment, accepted.breakdown());
    }

    private static Metrics appendBreakdown(
            StringBuilder text,
            String prefix,
            OptimizationProblem problem,
            Assignment assignment,
            ScoreBreakdown breakdown) {
        line(text, prefix + "_valid", "true");
        line(text, prefix + "_changes", Integer.toString(breakdown.totalChanges()));
        line(text, prefix + "_worst_transition", Integer.toString(breakdown.worstTransition()));
        line(text, prefix + "_overload", Integer.toString(breakdown.overload()));
        line(text, prefix + "_above_target", Integer.toString(aboveTarget(breakdown)));
        line(text, prefix + "_histogram", histogram(breakdown.histogram()));
        line(text, prefix + "_weighted_switch_burden", Double.toString(breakdown.weightedSwitchBurden()));
        line(text, prefix + "_role_deviation", Double.toString(breakdown.roleDeviation()));
        line(text, prefix + "_keep_deviation", Double.toString(breakdown.keepDeviation()));
        line(text, prefix + "_changed_assignments", Integer.toString(breakdown.changedAssignments()));
        line(text, prefix + "_smooth_contribution", Double.toString(breakdown.smoothContribution()));
        line(text, prefix + "_total_contribution", Double.toString(breakdown.totalContribution()));
        line(text, prefix + "_role_contribution", Double.toString(breakdown.roleContribution()));
        line(text, prefix + "_keep_contribution", Double.toString(breakdown.keepContribution()));
        line(text, prefix + "_weighted_score", Double.toString(breakdown.weightedScore()));
        if (problem != null && assignment != null) {
            int[] burdens = burdens(problem, assignment);
            line(text, prefix + "_burden_min", Integer.toString(burdens[0]));
            line(text, prefix + "_burden_median", formatMedian(medianValue(boxed(burdens))));
            line(text, prefix + "_burden_max", Integer.toString(burdens[burdens.length - 1]));
        }
        return new Metrics(breakdown.totalChanges(), breakdown.worstTransition(), breakdown.overload());
    }

    private static void warmup(BenchmarkRequest request) {
        if (request.warmup().isZero()) {
            return;
        }
        progress(request.progress(), "warmup seconds=" + request.warmup().toSeconds());
        AssignmentSolver.solve(
                BenchmarkFixture.problem(),
                OptimizationRules.defaults(),
                SearchBudget.of(0, request.warmup()));
    }

    private static void appendEnvironment(StringBuilder text, BenchmarkRequest request) {
        line(text, "benchmark", "setlist-optimizer-poc");
        line(text, "java_version", property("java.version"));
        line(text, "java_vm", property("java.vm.name"));
        line(text, "os_name", property("os.name"));
        line(text, "os_arch", property("os.arch"));
        line(text, "processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        String processor = System.getenv("PROCESSOR_IDENTIFIER");
        if (processor != null && !processor.isBlank()) {
            line(text, "processor", processor.replace('\n', ' ').replace('\r', ' '));
        }
        line(text, "source", request.source());
        line(text, "setlist_id", Long.toString(request.problem().setlistId()));
        line(text, "rosters", join(request.rosterSizes()));
        line(text, "seeds", joinLongs(request.seeds()));
        line(text, "budget_seconds", Long.toString(request.budget().toSeconds()));
        line(text, "checkpoints", join(request.checkpointSeconds()));
        line(text, "cold_warm", Boolean.toString(request.coldWarm()));
        line(text, "warmup_seconds", Long.toString(request.warmup().toSeconds()));
        line(text, "candidate_moves", "evaluated neighborhood attempts, including rejects");
        line(text, "optimality", "not_claimed");
    }

    private static void appendLayout(
            StringBuilder text,
            OptimizationProblem layout,
            ScoreOutcome layoutScore,
            boolean referenceShape,
            boolean referenceMatch) {
        line(text, "layout_players", Integer.toString(layout.playerCount()));
        line(text, "items", Integer.toString(layout.itemCount()));
        line(text, "parts", Integer.toString(layout.partCount()));
        line(text, "instruments", Integer.toString(layout.instrumentCount()));
        line(text, "transitions", Integer.toString(layout.transitionCount()));
        line(text, "reference_shape", Boolean.toString(referenceShape));
        if (!(layoutScore instanceof ScoreOutcome.Accepted accepted)) {
            line(text, "layout_baseline", "rejected");
            line(text, "layout_baseline_codes", codes(((ScoreOutcome.Rejected) layoutScore).diagnostics()));
            line(text, "reference_baseline", referenceShape ? "mismatch" : "not_reference");
            return;
        }
        ScoreBreakdown breakdown = accepted.breakdown();
        line(text, "layout_baseline", "accepted");
        line(text, "layout_baseline_changes", Integer.toString(breakdown.totalChanges()));
        line(text, "layout_baseline_worst", Integer.toString(breakdown.worstTransition()));
        line(text, "layout_baseline_histogram", histogram(breakdown.histogram()));
        line(text, "reference_baseline", !referenceShape ? "not_reference" : referenceMatch ? "match" : "mismatch");
        if (referenceShape) {
            line(text, "reference_expected_changes", "148");
            line(text, "reference_expected_worst", "6");
            line(text, "reference_expected_histogram", histogram(REFERENCE_HISTOGRAM));
        }
    }

    private static void appendSummaries(StringBuilder text, List<Observation> observations) {
        Map<String, List<Observation>> groups = new LinkedHashMap<>();
        for (Observation observation : observations) {
            groups.computeIfAbsent(observation.roster + "\0" + observation.variant + "\0" + observation.jvm,
                    ignored -> new ArrayList<>()).add(observation);
        }
        for (List<Observation> group : groups.values()) {
            Observation first = group.get(0);
            text.append('\n');
            line(text, "summary", "roster=" + first.roster + " variant=" + first.variant + " jvm=" + first.jvm);
            line(text, "summary_samples", Integer.toString(group.size()));
            appendSpread(text, "summary_changes", integers(group, value -> value.changes));
            appendSpread(text, "summary_worst", integers(group, value -> value.worst));
            appendSpread(text, "summary_overload", integers(group, value -> value.overload));
            line(text, "summary_checkpoint10_worst_median",
                    formatMedian(medianValue(integers(group, value -> value.at10Worst))));
            line(text, "summary_checkpoint10_overload_median",
                    formatMedian(medianValue(integers(group, value -> value.at10Overload))));
            line(text, "summary_baseline_changes", Integer.toString(first.baselineChanges));
            line(text, "summary_baseline_worst", Integer.toString(first.baselineWorst));
            line(text, "summary_baseline_overload", Integer.toString(first.baselineOverload));
            if (group.stream().anyMatch(observation -> observation.greedyWorst == null)) {
                line(text, "summary_greedy_changes", "none");
                line(text, "summary_greedy_worst", "none");
                line(text, "summary_greedy_overload", "none");
            } else {
                line(text, "summary_greedy_changes", formatMedian(medianValue(integers(group, value -> value.greedyChanges))));
                line(text, "summary_greedy_worst", formatMedian(medianValue(integers(group, value -> value.greedyWorst))));
                line(text, "summary_greedy_overload", formatMedian(medianValue(integers(group, value -> value.greedyOverload))));
            }
        }
    }

    private static void appendVerdict(
            StringBuilder text,
            BenchmarkRequest request,
            boolean referenceShape,
            boolean referenceMatch,
            List<Observation> observations,
            Flags flags) {
        text.append('\n');
        line(text, "database_unchanged", "database".equals(request.source()) ? "pending" : "not_applicable");
        if (!referenceShape) {
            line(text, "go_no_go", "not_measured");
            line(text, "go_no_go_reason", "not_reference");
            return;
        }
        List<String> reasons = new ArrayList<>();
        if (!referenceMatch) {
            reasons.add("reference_baseline_mismatch");
        }
        if (flags.searchFailed) {
            reasons.add("search_infeasible");
        }
        if (flags.assignmentInvalid) {
            reasons.add("assignment_invalid");
        }
        if (!flags.sawInfeasible || flags.infeasibleAccepted) {
            reasons.add("infeasible_variant_was_feasible");
        }
        List<Observation> smooth = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.roster == request.problem().playerCount()
                    && BenchmarkScenarios.SMOOTH_FIRST.equals(observation.variant)
                    && "warm".equals(observation.jvm)) {
                smooth.add(observation);
            }
        }
        if (request.budget().compareTo(Duration.ofSeconds(10)) < 0) {
            reasons.add("budget_below_10s");
        } else if (smooth.size() != request.seeds().size()) {
            reasons.add("warm_samples_missing");
        } else if (smooth.stream().anyMatch(observation -> observation.greedyWorst == null)) {
            reasons.add("greedy_infeasible");
        } else if (!improves(smooth)) {
            reasons.add("smooth_first_did_not_improve_by_10s");
        }
        line(text, "go_no_go", reasons.isEmpty() ? "go" : "no-go");
        line(text, "go_no_go_reason", reasons.isEmpty() ? "warm_median_smooth_first_improved_by_10s" : String.join(",", reasons));
    }

    private static boolean improves(List<Observation> smooth) {
        Observation baseline = smooth.get(0);
        double worst = medianValue(integers(smooth, value -> value.at10Worst));
        double overload = medianValue(integers(smooth, value -> value.at10Overload));
        double greedyWorst = medianValue(integers(smooth, value -> value.greedyWorst));
        double greedyOverload = medianValue(integers(smooth, value -> value.greedyOverload));
        boolean versusBaseline = worst < baseline.baselineWorst || overload < baseline.baselineOverload;
        boolean versusGreedy = worst < greedyWorst || overload < greedyOverload;
        return versusBaseline && versusGreedy;
    }

    private static boolean referenceShape(OptimizationProblem problem) {
        return problem.setlistId() == 24
                && problem.itemCount() == 34
                && problem.partCount() == 396
                && problem.playerCount() == 12
                && problem.transitionCount() == 33;
    }

    private static boolean matchesReference(ScoreOutcome outcome) {
        if (!(outcome instanceof ScoreOutcome.Accepted accepted)) {
            return false;
        }
        ScoreBreakdown breakdown = accepted.breakdown();
        return breakdown.totalChanges() == 148
                && breakdown.worstTransition() == 6
                && REFERENCE_HISTOGRAM.equals(breakdown.histogram());
    }

    private static RosterExpander.RosterExpansion expand(
            OptimizationProblem problem,
            int roster,
            List<Performer> extras) {
        return RosterExpander.expand(problem, roster, extras);
    }

    private static String jvm(BenchmarkRequest request) {
        return request.coldWarm() ? "warm" : "single";
    }

    private static void progress(PrintStream progress, String message) {
        if (progress != null) {
            progress.println(message);
            progress.flush();
        }
    }

    private static String firstImprovement(Trace trace) {
        if (trace.samples.size() < 2) {
            return "none";
        }
        return Long.toString(trace.samples.get(1).elapsedNanos() / 1_000_000L);
    }

    private static String rate(long moves, long elapsedNanos) {
        if (elapsedNanos <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", moves / (elapsedNanos / 1_000_000_000d));
    }

    private static int aboveTarget(ScoreBreakdown breakdown) {
        int count = 0;
        for (int changes : breakdown.changesPerTransition()) {
            if (changes > breakdown.target()) {
                count++;
            }
        }
        return count;
    }

    private static int[] burdens(OptimizationProblem problem, Assignment assignment) {
        int[] values = new int[problem.performers().size()];
        List<SongOccurrence> songs = problem.occurrences();
        for (int player = 0; player < problem.performers().size(); player++) {
            long playerId = problem.performers().get(player).playerId();
            int switches = 0;
            for (int song = 1; song < songs.size(); song++) {
                Long previous = played(songs.get(song - 1), assignment, playerId);
                Long current = played(songs.get(song), assignment, playerId);
                if (previous != null
                        && current != null
                        && !PhysicalInstrumentEquivalence.sameInstrument(
                                previous, current, problem.equivalentInstrumentIds())) {
                    switches++;
                }
            }
            values[player] = switches;
        }
        java.util.Arrays.sort(values);
        return values;
    }

    private static Long played(SongOccurrence occurrence, Assignment assignment, long playerId) {
        AssignedSlot slot = assignment.slot(occurrence.setlistItemId(), playerId);
        if (slot == null || slot.partNumber() == null) {
            return null;
        }
        for (RequiredPart part : occurrence.parts()) {
            if (part.partNumber() == slot.partNumber()) {
                return part.instrumentId();
            }
        }
        return null;
    }

    private static String histogram(Map<Integer, Integer> histogram) {
        if (histogram.isEmpty()) {
            return "none";
        }
        List<Integer> keys = new ArrayList<>(histogram.keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            Integer key = keys.get(i);
            text.append(key).append('=').append(histogram.get(key));
        }
        return text.toString();
    }

    private static String codes(List<Diagnostic> diagnostics) {
        TreeSet<String> codes = new TreeSet<>();
        for (Diagnostic diagnostic : diagnostics) {
            codes.add(diagnostic.code());
        }
        if (codes.isEmpty()) {
            return "none";
        }
        return String.join(",", codes);
    }

    private static List<Integer> integers(List<Observation> group, java.util.function.ToIntFunction<Observation> reader) {
        List<Integer> values = new ArrayList<>();
        for (Observation observation : group) {
            values.add(reader.applyAsInt(observation));
        }
        return values;
    }

    private static void appendSpread(StringBuilder text, String label, List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        line(text, label + "_min", Integer.toString(sorted.get(0)));
        line(text, label + "_median", formatMedian(medianValue(values)));
        line(text, label + "_max", Integer.toString(sorted.get(sorted.size() - 1)));
    }

    private static double medianValue(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (((long) sorted.get(size / 2 - 1)) + sorted.get(size / 2)) / 2.0d;
    }

    private static String formatMedian(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> boxed = new ArrayList<>();
        for (int value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null ? "" : value;
    }

    private static String join(List<Integer> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(values.get(i));
        }
        return text.toString();
    }

    private static String joinLongs(List<Long> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(values.get(i));
        }
        return text.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(label).append(": ").append(value).append('\n');
    }

    private static final class Trace implements SearchListener {
        private final long started;
        private final List<Sample> samples = new ArrayList<>();

        private Trace(long started) {
            this.started = started;
        }

        @Override
        public void onBest(ScoreBreakdown best, int acceptedMoves) {
            long elapsed = System.nanoTime() - started;
            samples.add(new Sample(Math.max(0, elapsed), best));
        }
    }

    private record Sample(long elapsedNanos, ScoreBreakdown breakdown) {
    }

    private record Metrics(int changes, int worst, int overload) {
    }

    private static final class Flags {
        private boolean searchFailed;
        private boolean assignmentInvalid;
        private boolean infeasibleAccepted;
        private boolean sawInfeasible;
    }

    private record Observation(
            int roster,
            String variant,
            String jvm,
            int changes,
            int worst,
            int overload,
            int at10Worst,
            int at10Overload,
            int baselineChanges,
            int baselineWorst,
            int baselineOverload,
            Integer greedyChanges,
            Integer greedyWorst,
            Integer greedyOverload) {
    }

    public record BenchmarkRequest(
            String source,
            OptimizationProblem problem,
            List<Performer> libraryExtras,
            List<Integer> rosterSizes,
            List<Long> seeds,
            Duration budget,
            List<Integer> checkpointSeconds,
            boolean coldWarm,
            Duration warmup,
            PrintStream progress) {

        public BenchmarkRequest {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(problem, "problem");
            libraryExtras = List.copyOf(Objects.requireNonNull(libraryExtras, "libraryExtras"));
            rosterSizes = List.copyOf(Objects.requireNonNull(rosterSizes, "rosterSizes"));
            seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds"));
            Objects.requireNonNull(budget, "budget");
            checkpointSeconds = List.copyOf(Objects.requireNonNull(checkpointSeconds, "checkpointSeconds"));
            Objects.requireNonNull(warmup, "warmup");
            if (source.isBlank() || rosterSizes.isEmpty() || seeds.isEmpty()) {
                throw new IllegalArgumentException("request");
            }
            if (budget.isNegative() || warmup.isNegative()) {
                throw new IllegalArgumentException("budget");
            }
            for (int roster : rosterSizes) {
                if (roster <= 0) {
                    throw new IllegalArgumentException("roster");
                }
            }
            for (long seed : seeds) {
                if (seed < 0) {
                    throw new IllegalArgumentException("seed");
                }
            }
            for (int checkpoint : checkpointSeconds) {
                if (checkpoint < 0) {
                    throw new IllegalArgumentException("checkpoint");
                }
            }
        }
    }
}
