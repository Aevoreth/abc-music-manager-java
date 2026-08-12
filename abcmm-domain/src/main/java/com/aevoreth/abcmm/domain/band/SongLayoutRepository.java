package com.aevoreth.abcmm.domain.band;

import java.util.List;
import java.util.Optional;

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

    /** All song layouts for {@code songId}, ordered by name then id. */
    List<SongLayoutInfo> listSongLayouts(long songId) throws LibraryException;

    /**
     * Existing song layout for {@code (songId, bandLayoutId)}, if any. Does not create a row.
     */
    Optional<SongLayoutInfo> findSongLayout(long songId, long bandLayoutId) throws LibraryException;

    /**
     * Deletes the song layout and its assignments. Setlist items that pointed at it have
     * {@code song_layout_id} cleared; setlist assignment rows are left in place.
     */
    void deleteSongLayout(long songLayoutId) throws LibraryException;

    List<SongLayoutAssignmentInfo> listAssignments(long songLayoutId) throws LibraryException;

    void setAssignment(long songLayoutId, long playerId, Integer partNumberOrNull)
            throws LibraryException;
}
