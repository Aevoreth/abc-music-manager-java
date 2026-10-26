package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Merged diagnostics for one load. Exit 1 when any integrity diagnostic is present,
 * exit 2 when the problem is well-defined but the baseline assignment is infeasible,
 * and exit 0 otherwise. Info diagnostics do not change the exit code.
 */
public record FeasibilityReport(List<Diagnostic> diagnostics) {

    public FeasibilityReport {
        List<Diagnostic> sorted = new ArrayList<>(diagnostics);
        sorted.sort(Comparator
                .comparingInt(FeasibilityReport::severityOrder)
                .thenComparing(Diagnostic::code)
                .thenComparing(diagnostic -> diagnostic.detail() == null ? "" : diagnostic.detail()));
        diagnostics = List.copyOf(new ArrayList<>(new LinkedHashSet<>(sorted)));
    }

    public boolean integrityOk() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.INTEGRITY);
    }

    public boolean baselineFeasible() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.BASELINE);
    }

    public int exitCode() {
        if (!integrityOk()) {
            return 1;
        }
        if (!baselineFeasible()) {
            return 2;
        }
        return 0;
    }

    private static int severityOrder(Diagnostic diagnostic) {
        return switch (diagnostic.severity()) {
            case INTEGRITY -> 0;
            case BASELINE -> 1;
            case INFO -> 2;
        };
    }
}
