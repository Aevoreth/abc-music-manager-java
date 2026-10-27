package com.aevoreth.abcmm.optimizer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Anytime search over feasible assignments. The repaired baseline is kept until a
 * strict improvement replaces it. Cancellation and the time budget are polled
 * between move attempts. Ties keep the incumbent.
 */
public final class AssignmentSolver {

    private static final int MAX_WINDOW = 4;
    private static final int PERTURB_SWAPS = 3;

    private AssignmentSolver() {
    }

    public static SearchOutcome solve(OptimizationProblem problem, OptimizationRules rules, SearchBudget budget) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(budget, "budget");
        long started = System.nanoTime();
        Assignment baseline = Assignment.fromBaseline(problem);
        ScoreOutcome baselineScore = AssignmentScorer.score(problem, baseline, rules);
        if (baselineScore instanceof ScoreOutcome.Rejected rejected && hasIntegrity(rejected)) {
            return infeasible(rejected.diagnostics());
        }
        ProblemIndex index = ProblemIndex.build(problem, rules);
        AssignmentRepair.Result repaired = AssignmentRepair.repair(index);
        if (!repaired.feasible()) {
            List<Diagnostic> diagnostics = AssignmentValidator.validate(problem, baseline, rules);
            if (diagnostics.isEmpty()) {
                throw new IllegalStateException("repair failed without diagnostics");
            }
            return infeasible(diagnostics);
        }
        CandidateState current = repaired.state();
        List<Diagnostic> repairedProblems = AssignmentValidator.validate(problem, current.toAssignment(), rules);
        if (!repairedProblems.isEmpty()) {
            return infeasible(repairedProblems);
        }
        return new Search(index, current, repaired.preservedBaseline(), budget, started).run();
    }

    private static boolean hasIntegrity(ScoreOutcome.Rejected rejected) {
        for (Diagnostic diagnostic : rejected.diagnostics()) {
            if (diagnostic.severity() == DiagnosticSeverity.INTEGRITY) {
                return true;
            }
        }
        return false;
    }

    private static SearchOutcome.Infeasible infeasible(List<Diagnostic> diagnostics) {
        return new SearchOutcome.Infeasible(new ScoreOutcome.Rejected(diagnostics).diagnostics());
    }

    private static final class Search {
        private final ProblemIndex index;
        private final boolean preservedBaseline;
        private final SearchBudget budget;
        private final long started;
        private final long deadline;
        private final Random random;
        private CandidateState current;
        private CandidateState best;
        private boolean bestReplaced;
        private int accepted;
        private long evaluated;
        private StopReason stop;

        private Search(
                ProblemIndex index,
                CandidateState start,
                boolean preservedBaseline,
                SearchBudget budget,
                long started) {
            this.index = index;
            this.current = start;
            this.best = start.copy();
            this.preservedBaseline = preservedBaseline;
            this.budget = budget;
            this.started = started;
            this.deadline = deadline(started, budget.budget());
            this.random = new Random(budget.seed());
        }

        private SearchOutcome run() {
            notifyBest();
            if (alreadyCancelled()) {
                return finish(StopReason.CANCELLED);
            }
            if (budget.budget().isZero()) {
                return finish(StopReason.BUDGET);
            }
            StopReason reason = StopReason.LOCAL_OPTIMUM;
            while (true) {
                hillClimb();
                if (stop != null) {
                    reason = stop;
                    break;
                }
                if (!perturb()) {
                    if (stop != null) {
                        reason = stop;
                    }
                    break;
                }
                if (stop != null) {
                    reason = stop;
                    break;
                }
            }
            return finish(reason);
        }

        private void hillClimb() {
            while (stop == null) {
                if (expired()) {
                    return;
                }
                if (improveSingles()) {
                    continue;
                }
                if (stop != null) {
                    return;
                }
                if (improveWindows()) {
                    continue;
                }
                if (stop != null) {
                    return;
                }
                return;
            }
        }

        private boolean improveSingles() {
            for (int song : songOrder()) {
                if (improvePairs(song)) {
                    return true;
                }
                if (stop != null) {
                    return false;
                }
                if (improveCycles(song)) {
                    return true;
                }
                if (stop != null) {
                    return false;
                }
            }
            return false;
        }

        private boolean improvePairs(int song) {
            for (int first = 0; first < index.players; first++) {
                for (int second = first + 1; second < index.players; second++) {
                    if (expired()) {
                        return false;
                    }
                    if (acceptPair(song, first, second)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean improveCycles(int song) {
            for (int first = 0; first < index.players; first++) {
                for (int second = first + 1; second < index.players; second++) {
                    for (int third = second + 1; third < index.players; third++) {
                        for (int orientation = 0; orientation < 2; orientation++) {
                            if (expired()) {
                                return false;
                            }
                            if (acceptCycle(song, first, second, third, orientation)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean improveWindows() {
            for (Window window : windows()) {
                if (improvePairWindow(window)) {
                    return true;
                }
                if (stop != null) {
                    return false;
                }
                if (improveCycleWindow(window)) {
                    return true;
                }
                if (stop != null) {
                    return false;
                }
            }
            return false;
        }

        private boolean improvePairWindow(Window window) {
            for (int first = 0; first < index.players; first++) {
                for (int second = first + 1; second < index.players; second++) {
                    if (expired()) {
                        return false;
                    }
                    evaluated++;
                    if (SearchMoves.commitPairWindow(current, window.start, window.length, first, second)) {
                        recordAcceptance();
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean improveCycleWindow(Window window) {
            for (int first = 0; first < index.players; first++) {
                for (int second = first + 1; second < index.players; second++) {
                    for (int third = second + 1; third < index.players; third++) {
                        for (int orientation = 0; orientation < 2; orientation++) {
                            if (expired()) {
                                return false;
                            }
                            evaluated++;
                            if (SearchMoves.commitCycleWindow(
                                    current, window.start, window.length, first, second, third, orientation)) {
                                recordAcceptance();
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean acceptPair(int song, int first, int second) {
            evaluated++;
            if (!SearchMoves.commitPair(current, song, first, second)) {
                return false;
            }
            recordAcceptance();
            return true;
        }

        private boolean acceptCycle(int song, int first, int second, int third, int orientation) {
            evaluated++;
            if (!SearchMoves.commitCycle(current, song, first, second, third, orientation)) {
                return false;
            }
            recordAcceptance();
            return true;
        }

        private void recordAcceptance() {
            accepted++;
            ScoreOutcome scored = AssignmentScorer.score(index.problem, current.toAssignment(), index.rules);
            if (!(scored instanceof ScoreOutcome.Accepted acceptedScore)
                    || !current.breakdown().equals(acceptedScore.breakdown())) {
                throw new IllegalStateException("incremental score diverged");
            }
            updateBest();
        }

        private boolean perturb() {
            for (int swap = 0; swap < PERTURB_SWAPS; swap++) {
                if (expired()) {
                    return false;
                }
                int[] move = randomLegalPair();
                if (move == null) {
                    return swap > 0;
                }
                SearchMoves.applyForcedPair(current, move[0], move[1], move[2]);
                updateBest();
            }
            return true;
        }

        private int[] randomLegalPair() {
            List<int[]> legal = new ArrayList<>();
            for (int song = 0; song < index.songs; song++) {
                for (int first = 0; first < index.players; first++) {
                    for (int second = first + 1; second < index.players; second++) {
                        if (SearchMoves.pairLegal(current, song, first, second)) {
                            legal.add(new int[] {song, first, second});
                        }
                    }
                }
            }
            if (legal.isEmpty()) {
                return null;
            }
            return legal.get(random.nextInt(legal.size()));
        }

        private void updateBest() {
            if (!(current.weightedScore < best.weightedScore)) {
                return;
            }
            best = current.copy();
            bestReplaced = true;
            notifyBest();
        }

        private void notifyBest() {
            if (budget.listener() != null) {
                budget.listener().onBest(best.breakdown(), accepted);
            }
        }

        private int[] songOrder() {
            int[] order = new int[index.songs];
            boolean[] used = new boolean[index.songs];
            int count = 0;
            if (current.transitionChanges.length > 0) {
                int worst = 0;
                for (int transition = 1; transition < current.transitionChanges.length; transition++) {
                    if (current.transitionChanges[transition] > current.transitionChanges[worst]) {
                        worst = transition;
                    }
                }
                order[count++] = worst;
                used[worst] = true;
                if (worst + 1 < index.songs) {
                    order[count++] = worst + 1;
                    used[worst + 1] = true;
                }
            }
            for (int song = 0; song < index.songs; song++) {
                if (!used[song]) {
                    order[count++] = song;
                }
            }
            return order;
        }

        private List<Window> windows() {
            List<Window> windows = new ArrayList<>();
            int limit = Math.min(MAX_WINDOW, index.songs);
            for (int length = 2; length <= limit; length++) {
                for (int start = 0; start + length <= index.songs; start++) {
                    int rankTransition = start;
                    int rankChanges = current.transitionChanges[start];
                    for (int transition = start + 1; transition < start + length - 1; transition++) {
                        int changes = current.transitionChanges[transition];
                        if (changes > rankChanges || (changes == rankChanges && transition < rankTransition)) {
                            rankChanges = changes;
                            rankTransition = transition;
                        }
                    }
                    windows.add(new Window(start, length, rankTransition, rankChanges));
                }
            }
            windows.sort(Comparator
                    .comparingInt(Window::rankChanges).reversed()
                    .thenComparingInt(Window::rankTransition)
                    .thenComparingInt(Window::start)
                    .thenComparingInt(Window::length));
            return windows;
        }

        private boolean alreadyCancelled() {
            return budget.cancelled() != null && budget.cancelled().getAsBoolean();
        }

        private boolean expired() {
            if (stop != null) {
                return true;
            }
            if (alreadyCancelled()) {
                stop = StopReason.CANCELLED;
                return true;
            }
            if (System.nanoTime() >= deadline) {
                stop = StopReason.BUDGET;
                return true;
            }
            return false;
        }

        private SearchOutcome finish(StopReason reason) {
            Assignment assignment = preservedBaseline && !bestReplaced
                    ? Assignment.fromBaseline(index.problem)
                    : best.toAssignment();
            ScoreOutcome scored = AssignmentScorer.score(index.problem, assignment, index.rules);
            if (!(scored instanceof ScoreOutcome.Accepted acceptedScore)) {
                throw new IllegalStateException("search returned an assignment the scorer rejected");
            }
            if (!best.breakdown().equals(acceptedScore.breakdown())) {
                throw new IllegalStateException("incremental score diverged from AssignmentScorer");
            }
            List<Diagnostic> problems = AssignmentValidator.validate(index.problem, assignment, index.rules);
            if (!problems.isEmpty()) {
                throw new IllegalStateException("search returned an assignment that breaks a hard constraint");
            }
            long elapsed = System.nanoTime() - started;
            if (elapsed < 0) {
                elapsed = 0;
            }
            return new SearchOutcome.Solved(
                    assignment,
                    acceptedScore.breakdown(),
                    reason,
                    elapsed,
                    accepted,
                    evaluated,
                    budget.seed());
        }
    }

    private static long deadline(long started, Duration budget) {
        long nanos;
        try {
            nanos = budget.toNanos();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
        if (nanos <= 0) {
            return started;
        }
        if (started > Long.MAX_VALUE - nanos) {
            return Long.MAX_VALUE;
        }
        return started + nanos;
    }

    private record Window(int start, int length, int rankTransition, int rankChanges) {
    }
}
