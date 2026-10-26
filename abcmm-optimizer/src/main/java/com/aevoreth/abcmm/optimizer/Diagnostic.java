package com.aevoreth.abcmm.optimizer;

import java.util.Objects;

/**
 * One structured finding. {@code detail} uses {@code key=value} pairs and ids, not names.
 */
public record Diagnostic(DiagnosticSeverity severity, String code, String detail) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code");
        }
        detail = detail == null ? "" : detail;
    }

    public static Diagnostic integrity(String code, String detail) {
        return new Diagnostic(DiagnosticSeverity.INTEGRITY, code, detail);
    }

    public static Diagnostic baseline(String code, String detail) {
        return new Diagnostic(DiagnosticSeverity.BASELINE, code, detail);
    }

    public static Diagnostic info(String code, String detail) {
        return new Diagnostic(DiagnosticSeverity.INFO, code, detail);
    }

    public String format() {
        if (detail.isBlank()) {
            return "diagnostic: " + severity.name() + " " + code;
        }
        return "diagnostic: " + severity.name() + " " + code + " " + detail;
    }
}
