package com.aevoreth.abcmm.optimizer;

/**
 * How a diagnostic affects the load result.
 * {@link #INFO} is reported and does not fail the run.
 */
public enum DiagnosticSeverity {
    INTEGRITY,
    BASELINE,
    INFO
}
