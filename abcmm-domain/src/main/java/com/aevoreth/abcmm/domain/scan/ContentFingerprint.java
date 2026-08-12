package com.aevoreth.abcmm.domain.scan;

/**
 * Musical-content fingerprint placeholder for future Maestro-export equivalence detection.
 * Phase 3 may populate this; callers should treat null as unknown.
 */
public final class ContentFingerprint {

    private ContentFingerprint() {
    }

    /**
     * @return always null until a conservative fingerprint is implemented
     */
    public static String compute(AbcFileMetadata metadata, String rawAbc) {
        return null;
    }
}
