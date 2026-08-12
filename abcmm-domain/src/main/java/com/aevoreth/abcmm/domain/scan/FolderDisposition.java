package com.aevoreth.abcmm.domain.scan;

/**
 * Per-folder disposition within a duplicated folder-tree cleanup plan.
 */
public enum FolderDisposition {
    /** Keep this folder in the library and continue scanning it. */
    KEEP_AND_SCAN,
    /** Remove indexed SongFile rows under this folder; leave files on disk. */
    REMOVE_FROM_LIBRARY,
    /** Persist an exclude FolderRule and skip on future scans; leave files on disk. */
    EXCLUDE_FROM_SCANS,
    /** Move folder contents to Recycle Bin and remove from the index. */
    TRASH,
    /** Do not apply a folder-level policy; leave related file groups for manual review. */
    REVIEW_INDIVIDUALLY
}
