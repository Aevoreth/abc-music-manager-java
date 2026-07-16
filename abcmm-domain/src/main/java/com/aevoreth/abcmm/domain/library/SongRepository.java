package com.aevoreth.abcmm.domain.library;

import java.util.List;

/**
 * Library index access. Implementations live in {@code abcmm-storage}.
 */
public interface SongRepository extends AutoCloseable {

    List<LibrarySong> listLibrarySongs(LibraryFilter filter) throws LibraryException;

    /**
     * Distinct non-blank transcriber values from primary-library songs, sorted.
     */
    List<String> listUniqueTranscribers() throws LibraryException;

    List<StatusInfo> listStatuses() throws LibraryException;

    List<FolderRuleInfo> listFolderRules() throws LibraryException;

    List<AccountTargetInfo> listAccountTargets() throws LibraryException;

    @Override
    void close();
}
