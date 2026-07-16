package com.aevoreth.abcmm.domain.scan;

/**
 * Decides how to handle a primary-library duplicate during scan.
 */
@FunctionalInterface
public interface DuplicateResolver {

    DuplicateDecision resolve(DuplicateCandidate candidate);
}
