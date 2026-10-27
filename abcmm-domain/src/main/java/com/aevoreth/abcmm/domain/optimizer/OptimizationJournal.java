package com.aevoreth.abcmm.domain.optimizer;

import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Transactional apply and one-step undo for setlist assignment overrides.
 * Implementations live in {@code abcmm-storage}.
 *
 * <p>Apply checks the setlist lock and the revision captured when the
 * optimization was prepared, then replaces override rows and writes history
 * in one transaction. Undo restores that before snapshot in one step, including
 * after a restart. A missing override row and an explicit null part stay distinct.
 * Song layouts, song parts, item order, pins, and song files are not modified.
 *
 * <p>Undo selects the newest applied operation whose saved result still matches
 * the database. A newer applied optimization must be undone first. An edit to
 * song order, layouts, parts, pins, or overrides refuses the undo and leaves
 * the later edit in place.
 *
 * <p>On each apply the journal keeps the newest applied row, then the newest
 * other rows until {@link #MAX_ENTRIES_PER_SETLIST} are kept, and drops
 * unprotected rows older than {@link #MAX_AGE_DAYS} days. The newest applied
 * row is kept even when it is older than that window or would fall outside
 * the count, so a restart can still undo it. There is no settings control
 * for these limits. Redo is not supported.
 */
public interface OptimizationJournal {

    /** History rows kept for one setlist. The newest applied row is kept in addition when it would otherwise be dropped. */
    int MAX_ENTRIES_PER_SETLIST = 10;

    /** Unprotected history rows older than this many days are deleted on the next apply. */
    int MAX_AGE_DAYS = 30;

    /**
     * Current band layout, item order, song parts, pins, and override rows.
     *
     * @throws LibraryException when the setlist does not exist or the read fails
     */
    OptimizationRevision revision(long setlistId) throws LibraryException;

    /**
     * Replace the setlist's override rows with {@code desiredOverrides} when
     * {@code expected} still matches the database and the setlist is unlocked.
     * {@code rulesJson} is stored with the history row and is not compared later.
     */
    OptimizationApplyResult apply(
            long setlistId,
            OptimizationRevision expected,
            List<AssignmentOverride> desiredOverrides,
            String rulesJson) throws LibraryException;

    /**
     * Undo the newest applied optimization for the setlist when the current
     * rows still match its saved result.
     */
    OptimizationUndoResult undoLatest(long setlistId) throws LibraryException;
}
