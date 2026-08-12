package com.aevoreth.abcmm.domain.scan;

/**
 * Confidence classification for a peer duplicate group.
 */
public enum DuplicateMatchType {
    /** Byte-identical files (same SHA-256). */
    EXACT_FILE,
    /** Same logical identity (normalized title + composers + part count). */
    STRONG_METADATA_MATCH,
    /** Reserved for conservative weaker signals. */
    AMBIGUOUS
}
