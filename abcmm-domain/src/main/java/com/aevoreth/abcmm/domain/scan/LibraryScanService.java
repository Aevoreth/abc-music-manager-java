package com.aevoreth.abcmm.domain.scan;

import java.util.function.Consumer;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Discovers {@code .abc} files under configured roots and upserts the library index.
 *
 * <p>Flow: {@link #analyze} (inventory + peer groups) → user review → {@link #apply} →
 * {@link #reconcile}.
 */
public interface LibraryScanService {

    /**
     * Inventory all files, safely refresh known SongFile paths, and build peer duplicate groups.
     * Does not insert first-scanned peers or treat walk order as canonical.
     */
    DuplicateAnalysis analyze(ScanRequest request, Consumer<ScanProgress> progress) throws LibraryException;

    /**
     * Apply a validated cleanup plan (filesystem + database). Does not reconcile unindexed uniques.
     */
    CleanupApplyResult apply(DuplicateCleanupPlan plan, Consumer<ScanProgress> progress) throws LibraryException;

    /**
     * Reconcile the library with the filesystem after cleanup (or when analysis found no duplicates):
     * insert remaining unique primary files, refresh known paths, prune missing SongFiles.
     */
    ScanProgress reconcile(ScanRequest request, Consumer<ScanProgress> progress) throws LibraryException;
}
