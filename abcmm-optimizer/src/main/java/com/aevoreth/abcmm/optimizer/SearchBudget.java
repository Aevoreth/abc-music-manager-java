package com.aevoreth.abcmm.optimizer;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Limits one search. A zero budget returns the repaired start without neighborhood
 * moves. A null cancellation supplier never cancels. A null listener receives nothing.
 */
public record SearchBudget(
        long seed,
        Duration budget,
        BooleanSupplier cancelled,
        SearchListener listener,
        long maxEvaluatedMoves) {

    public SearchBudget(
            long seed, Duration budget, BooleanSupplier cancelled, SearchListener listener) {
        this(seed, budget, cancelled, listener, Long.MAX_VALUE);
    }

    public SearchBudget {
        if (maxEvaluatedMoves < 0) {
            throw new IllegalArgumentException("maxEvaluatedMoves");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed");
        }
        Objects.requireNonNull(budget, "budget");
        if (budget.isNegative()) {
            throw new IllegalArgumentException("budget");
        }
    }

    /** Repeatable work limit for algorithm comparisons; one minute remains a safety deadline. */
    public static SearchBudget attempts(long seed, long attempts) {
        return new SearchBudget(seed, Duration.ofMinutes(1), null, null, attempts);
    }

    public static SearchBudget of(long seed, Duration budget) {
        return new SearchBudget(seed, budget, null, null);
    }
}
