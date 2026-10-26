package com.aevoreth.abcmm.optimizer;

/**
 * Observes the best feasible score. Called on the solver thread when that score
 * is first established and whenever a strict improvement replaces it.
 */
@FunctionalInterface
public interface SearchListener {

    void onBest(ScoreBreakdown best, int acceptedMoves);
}
