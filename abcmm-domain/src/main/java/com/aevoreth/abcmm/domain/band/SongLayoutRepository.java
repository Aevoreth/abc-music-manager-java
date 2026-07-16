package com.aevoreth.abcmm.domain.band;

import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Song layout and part-assignment access. Implementations live in {@code abcmm-storage}.
 */
public interface SongLayoutRepository {

    /**
     * Returns an existing song layout for {@code (songId, bandLayoutId)}, or creates one
     * with the given {@code name} (nullable).
     */
    SongLayoutInfo getOrCreateSongLayout(long songId, long bandLayoutId, String name)
            throws LibraryException;

    List<SongLayoutAssignmentInfo> listAssignments(long songLayoutId) throws LibraryException;

    void setAssignment(long songLayoutId, long playerId, Integer partNumberOrNull)
            throws LibraryException;
}
