package com.aevoreth.abcmm.domain.scan;

import java.nio.file.Path;

/**
 * Moves files or directories to the OS Recycle Bin / Trash when supported.
 */
public interface TrashService {

    /**
     * @return true if the path was moved to trash
     * @throws Exception if trash is unsupported or the operation failed
     */
    boolean moveToTrash(Path path) throws Exception;
}
