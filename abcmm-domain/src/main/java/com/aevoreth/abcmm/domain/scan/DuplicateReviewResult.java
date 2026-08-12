package com.aevoreth.abcmm.domain.scan;

/**
 * Outcome of the batch duplicate review dialog.
 */
public record DuplicateReviewResult(Action action, DuplicateCleanupPlan plan) {

    public enum Action {
        /** User cancelled; apply nothing. */
        CANCELLED,
        /** User finished; apply the complete plan then reconcile. */
        FINISHED,
        /** Apply the (possibly partial) plan, re-analyze, and continue reviewing. */
        APPLY_AND_RESCAN
    }

    public static DuplicateReviewResult cancelled() {
        return new DuplicateReviewResult(Action.CANCELLED, DuplicateCleanupPlan.empty());
    }

    public static DuplicateReviewResult finished(DuplicateCleanupPlan plan) {
        return new DuplicateReviewResult(Action.FINISHED, plan == null ? DuplicateCleanupPlan.empty() : plan);
    }

    public static DuplicateReviewResult applyAndRescan(DuplicateCleanupPlan plan) {
        return new DuplicateReviewResult(Action.APPLY_AND_RESCAN, plan == null ? DuplicateCleanupPlan.empty() : plan);
    }
}
