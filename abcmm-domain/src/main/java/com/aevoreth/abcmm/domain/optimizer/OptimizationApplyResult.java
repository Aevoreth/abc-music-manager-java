package com.aevoreth.abcmm.domain.optimizer;

import java.util.List;

/**
 * Result of one optimization apply. A successful result has an operation id and
 * no refusals. A refusal leaves every assignment and history row unchanged.
 */
public record OptimizationApplyResult(Long operationId, List<OptimizationRefusal> refusals, String message) {

    public OptimizationApplyResult {
        refusals = List.copyOf(refusals);
        message = message == null ? "" : message;
        if (operationId != null && !refusals.isEmpty()) {
            throw new IllegalArgumentException("applied result cannot also be refused");
        }
        if (operationId == null && refusals.isEmpty()) {
            throw new IllegalArgumentException("refused result needs a reason");
        }
    }

    public boolean applied() {
        return operationId != null;
    }

    public static OptimizationApplyResult applied(long operationId) {
        return new OptimizationApplyResult(operationId, List.of(), "");
    }

    public static OptimizationApplyResult refused(List<OptimizationRefusal> refusals, String message) {
        return new OptimizationApplyResult(null, refusals, message);
    }
}
