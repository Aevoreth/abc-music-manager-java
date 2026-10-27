package com.aevoreth.abcmm.optimizer;

/**
 * Id-only text report. Does not print names, titles, or account paths from the library.
 */
public final class ProblemReport {

    private ProblemReport() {
    }

    public static String format(LoadOutcome outcome) {
        StringBuilder text = new StringBuilder();
        text.append("setlist_id: ").append(outcome.setlistId()).append('\n');
        OptimizationProblem problem = outcome.problem();
        if (problem != null) {
            text.append("locked: ").append(problem.locked()).append('\n');
            text.append("band_layout_id: ").append(problem.bandLayoutId()).append('\n');
            text.append("items: ").append(problem.itemCount()).append('\n');
            text.append("parts: ").append(problem.partCount()).append('\n');
            text.append("instruments: ").append(problem.instrumentCount()).append('\n');
            text.append("players: ").append(problem.playerCount()).append('\n');
            text.append("transitions: ").append(problem.transitionCount()).append('\n');
        }
        appendOptional(text, "seed", outcome.seed());
        appendOptional(text, "budget_seconds", outcome.budgetSeconds());
        appendOptional(text, "requested_players", outcome.requestedPlayers());
        FeasibilityReport report = outcome.report();
        text.append("integrity: ").append(report.integrityOk() ? "ok" : "failed").append('\n');
        if (problem == null) {
            text.append("baseline: not_checked").append('\n');
        } else {
            text.append("baseline: ").append(report.baselineFeasible() ? "ok" : "failed").append('\n');
        }
        for (Diagnostic diagnostic : report.diagnostics()) {
            text.append(diagnostic.format()).append('\n');
        }
        if (problem != null) {
            OptimizationRules rules = outcome.rules() == null
                    ? OptimizationRules.defaults()
                    : outcome.rules();
            ScoreOutcome scored = AssignmentScorer.score(
                    problem, Assignment.fromBaseline(problem), rules);
            if (scored instanceof ScoreOutcome.Accepted accepted) {
                text.append(accepted.breakdown().format());
            }
        }
        return text.toString();
    }

    private static void appendOptional(StringBuilder text, String label, Object value) {
        if (value != null) {
            text.append(label).append(": ").append(value).append('\n');
        }
    }
}
