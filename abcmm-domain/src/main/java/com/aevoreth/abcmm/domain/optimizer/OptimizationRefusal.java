package com.aevoreth.abcmm.domain.optimizer;

/**
 * Why an optimization apply or undo left the database unchanged.
 */
public enum OptimizationRefusal {
    /** The setlist is locked. */
    LOCKED,
    /** The setlist no longer matches the snapshot captured when the optimization was prepared. */
    STALE_REVISION,
    /** The database was opened read-only. */
    READ_ONLY,
    /** Override rows differ from the saved optimization result. */
    CONFLICT_ASSIGNMENTS,
    /** Song order, item identity, or which song an item points at changed. */
    CONFLICT_ORDER,
    /** The band layout or a song layout link changed. */
    CONFLICT_LAYOUT,
    /** A song's parts text changed. */
    CONFLICT_PARTS,
    /** Assignment pins changed. */
    CONFLICT_PINS,
    /** No applied optimization remains to undo. */
    NOTHING_TO_UNDO,
    /** The setlist id is not in the database. */
    NOT_FOUND
}
