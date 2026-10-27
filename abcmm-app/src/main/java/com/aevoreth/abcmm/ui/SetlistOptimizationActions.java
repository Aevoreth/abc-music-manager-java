package com.aevoreth.abcmm.ui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.OptimizationApplyResult;
import com.aevoreth.abcmm.domain.optimizer.OptimizationJournal;
import com.aevoreth.abcmm.optimizer.Assignment;
import com.aevoreth.abcmm.optimizer.AssignmentOverridePlan;
import com.aevoreth.abcmm.optimizer.AssignmentSolver;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.LoadRequest;
import com.aevoreth.abcmm.optimizer.OptimizationRulesJson;
import com.aevoreth.abcmm.optimizer.SearchBudget;
import com.aevoreth.abcmm.optimizer.SearchListener;
import com.aevoreth.abcmm.optimizer.SearchOutcome;
import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;

/**
 * Read-only load and background search for one setlist, plus the explicit apply
 * that writes through the journal. Search never receives the journal.
 */
final class SetlistOptimizationActions {

    private SetlistOptimizationActions() {
    }

    static LoadOutcome load(Path database, long setlistId, Long seed) {
        return new SqliteOptimizationLoader().load(
                new LoadRequest(database, setlistId, seed, null, null));
    }

    static SearchOutcome search(
            LoadOutcome loaded,
            Duration budget,
            long seed,
            BooleanSupplier cancelled,
            SearchListener listener) {
        return AssignmentSolver.solve(
                loaded.problem(),
                loaded.rules(),
                new SearchBudget(seed, budget, cancelled, listener));
    }

    static OptimizationApplyResult apply(
            OptimizationJournal journal,
            LoadOutcome loaded,
            Assignment assignment) throws LibraryException {
        return journal.apply(
                loaded.setlistId(),
                loaded.revision(),
                AssignmentOverridePlan.plan(loaded, assignment),
                OptimizationRulesJson.toJson(loaded.rules()));
    }
}
