package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks problem integrity and whether the baseline assignment can be used as a
 * feasible starting point. Assignment coverage, ownership, completeness, and pins
 * are delegated to {@link AssignmentValidator}. Hard constraints are reported,
 * not converted into score penalties.
 */
public final class AssignmentFeasibility {

    private AssignmentFeasibility() {
    }

    public static FeasibilityReport check(OptimizationProblem problem, Integer requestedPlayers) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (requestedPlayers != null && requestedPlayers != problem.playerCount()) {
            diagnostics.add(Diagnostic.integrity(
                    "players_mismatch",
                    Diagnostics.detail(
                            "requested", requestedPlayers,
                            "actual", problem.playerCount())));
        }
        diagnostics.addAll(AssignmentValidator.validate(problem, Assignment.fromBaseline(problem)));
        return new FeasibilityReport(diagnostics);
    }
}
