package com.aevoreth.abcmm.domain.scan;

/**
 * Per-file disposition within a duplicate group cleanup plan.
 */
public enum FileDisposition {
    /** Index / retain this path; may remap an existing Song to this file. */
    KEEP,
    /** Index as its own Song even if peers exist. */
    KEEP_SEPARATE,
    /** Leave on disk; do not index. */
    IGNORE,
    /** Move to Recycle Bin (when supported) and remove from the index. */
    TRASH
}
