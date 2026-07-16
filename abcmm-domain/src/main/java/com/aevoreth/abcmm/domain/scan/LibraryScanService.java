package com.aevoreth.abcmm.domain.scan;

import java.util.function.Consumer;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Discovers {@code .abc} files under configured roots and upserts the library index.
 */
public interface LibraryScanService {

    /**
     * Scan the library. {@code resolver} may be null (treat duplicates as {@link DuplicateDecision#SEPARATE}).
     * {@code progress} may be null.
     */
    ScanProgress scan(ScanRequest request, DuplicateResolver resolver, Consumer<ScanProgress> progress)
            throws LibraryException;
}
