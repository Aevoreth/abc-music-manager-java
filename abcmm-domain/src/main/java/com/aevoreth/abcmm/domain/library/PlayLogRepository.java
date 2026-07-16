package com.aevoreth.abcmm.domain.library;

import java.util.List;
import java.util.Optional;

/**
 * Play history access. Implementations live in {@code abcmm-storage}.
 */
public interface PlayLogRepository {

    void logPlay(long songId, Long contextSetlistId, String contextNote) throws LibraryException;

    void logPlayAt(long songId, String playedAtIso, Long contextSetlistId, String contextNote)
            throws LibraryException;

    List<PlayLogEntry> getPlayHistory(long songId, int limit) throws LibraryException;

    /**
     * @return song id if updated, empty if the entry was missing
     */
    Optional<Long> updatePlay(long playLogId, String playedAtIso, String contextNote)
            throws LibraryException;

    /**
     * @return song id if deleted, empty if the entry was missing
     */
    Optional<Long> deletePlay(long playLogId) throws LibraryException;
}
