package com.aevoreth.abcmm.optimizer;

import java.util.List;
import java.util.Objects;

/**
 * Result of one search. An infeasible problem has diagnostics and no assignment.
 */
public sealed interface SearchOutcome permits SearchOutcome.Solved, SearchOutcome.Infeasible {

    /**
     * Best feasible assignment found. {@code acceptedMoves} counts hill-climb moves
     * that strictly improved the current state. {@code evaluatedMoves} counts
     * neighborhood attempts, including attempts that were rejected.
     * The breakdown is a fresh {@link AssignmentScorer} result.
     */
    record Solved(
            Assignment assignment,
            ScoreBreakdown breakdown,
            StopReason reason,
            long elapsedNanos,
            int acceptedMoves,
            long evaluatedMoves,
            long seed) implements SearchOutcome {

        public Solved {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(breakdown, "breakdown");
            Objects.requireNonNull(reason, "reason");
            if (elapsedNanos < 0) {
                throw new IllegalArgumentException("elapsedNanos");
            }
            if (acceptedMoves < 0) {
                throw new IllegalArgumentException("acceptedMoves");
            }
            if (evaluatedMoves < 0) {
                throw new IllegalArgumentException("evaluatedMoves");
            }
            if (seed < 0) {
                throw new IllegalArgumentException("seed");
            }
        }
    }

    record Infeasible(List<Diagnostic> diagnostics) implements SearchOutcome {
        public Infeasible {
            Objects.requireNonNull(diagnostics, "diagnostics");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
