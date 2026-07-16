package com.aevoreth.abcmm.domain.scan;

/**
 * How to resolve a {@link DuplicateCandidate} during library scan.
 */
public enum DuplicateDecision {
    /** Leave the existing indexed song; do not index the new file. */
    KEEP_EXISTING,
    /** Point the existing song at the new path (replace file location). */
    KEEP_NEW,
    /** Index the new file as a separate song. */
    SEPARATE
}
