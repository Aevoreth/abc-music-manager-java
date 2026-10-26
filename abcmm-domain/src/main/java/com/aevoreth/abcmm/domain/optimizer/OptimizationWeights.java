package com.aevoreth.abcmm.domain.optimizer;

/**
 * Shared range check for optimizer weights. Zero is allowed. A non-finite value is not.
 */
final class OptimizationWeights {

    private OptimizationWeights() {
    }

    static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name);
        }
    }
}
