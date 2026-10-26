package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Result of scoring one assignment. A rejected assignment has diagnostics only.
 * There is no penalty score and no stand-in numeric value.
 */
public sealed interface ScoreOutcome permits ScoreOutcome.Accepted, ScoreOutcome.Rejected {

    record Accepted(ScoreBreakdown breakdown) implements ScoreOutcome {
        public Accepted {
            Objects.requireNonNull(breakdown, "breakdown");
        }
    }

    record Rejected(List<Diagnostic> diagnostics) implements ScoreOutcome {
        public Rejected {
            Objects.requireNonNull(diagnostics, "diagnostics");
            List<Diagnostic> sorted = new ArrayList<>(diagnostics);
            sorted.sort(Comparator
                    .comparingInt(Rejected::severityOrder)
                    .thenComparing(Diagnostic::code)
                    .thenComparing(diagnostic -> diagnostic.detail() == null ? "" : diagnostic.detail()));
            diagnostics = List.copyOf(new ArrayList<>(new LinkedHashSet<>(sorted)));
        }

        private static int severityOrder(Diagnostic diagnostic) {
            return switch (diagnostic.severity()) {
                case INTEGRITY -> 0;
                case BASELINE -> 1;
                case INFO -> 2;
            };
        }
    }
}
