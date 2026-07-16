package com.aevoreth.abcmm.domain.library;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Library index access. Implementations live in {@code abcmm-storage}.
 */
public interface SongRepository extends AutoCloseable {

    List<LibrarySong> listLibrarySongs(LibraryFilter filter) throws LibraryException;

    /**
     * Distinct non-blank transcriber values from primary-library songs, sorted.
     */
    List<String> listUniqueTranscribers() throws LibraryException;

    /**
     * Resolves the primary on-disk ABC path for a song, if any {@code SongFile} row exists.
     */
    Optional<Path> resolvePrimaryAbcPath(long songId) throws LibraryException;

    /**
     * Looks up a library song by id, or empty if missing.
     */
    Optional<LibrarySong> findSongById(long songId) throws LibraryException;

    List<StatusInfo> listStatuses() throws LibraryException;

    List<FolderRuleInfo> listFolderRules() throws LibraryException;

    List<AccountTargetInfo> listAccountTargets() throws LibraryException;

    @Override
    void close();
}
