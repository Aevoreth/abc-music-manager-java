package com.aevoreth.abcmm.optimizer;

/**
 * Why search returned the best feasible assignment it had.
 */
public enum StopReason {
    LOCAL_OPTIMUM("local_optimum"),
    BUDGET("budget"),
    ATTEMPTS("attempts"),
    CANCELLED("cancelled");

    private final String reportName;

    StopReason(String reportName) {
        this.reportName = reportName;
    }

    /** Lowercase token used in the CLI report. */
    public String reportName() {
        return reportName;
    }
}
