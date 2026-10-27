package com.aevoreth.abcmm.domain.optimizer;

import java.util.List;

/**
 * Result of undoing the newest applied optimization. Success names that history
 * row. A refusal leaves assignments and history status unchanged.
 */
public record OptimizationUndoResult(Long operationId, List<OptimizationRefusal> refusals, String message) {

    public OptimizationUndoResult {
        refusals = List.copyOf(refusals);
        message = message == null ? "" : message;
        if (operationId != null && !refusals.isEmpty()) {
            throw new IllegalArgumentException("undone result cannot also be refused");
        }
        if (operationId == null && refusals.isEmpty()) {
            throw new IllegalArgumentException("refused result needs a reason");
        }
    }

    public boolean undone() {
        return operationId != null;
    }

    public static OptimizationUndoResult undone(long operationId) {
        return new OptimizationUndoResult(operationId, List.of(), "");
    }

    public static OptimizationUndoResult refused(List<OptimizationRefusal> refusals, String message) {
        return new OptimizationUndoResult(null, refusals, message);
    }
}
