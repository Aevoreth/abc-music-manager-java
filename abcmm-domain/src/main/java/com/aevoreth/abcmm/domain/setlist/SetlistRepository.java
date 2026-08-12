package com.aevoreth.abcmm.domain.setlist;

import java.util.List;

import com.aevoreth.abcmm.domain.export.SetExportItemInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Setlist folder, setlist, item, and band-assignment access.
 * Implementations live in {@code abcmm-storage}.
 */
public interface SetlistRepository {

    List<SetlistFolderInfo> listFolders() throws LibraryException;

    long addFolder(String name) throws LibraryException;

    void updateFolder(long id, String name) throws LibraryException;

    void deleteFolder(long id) throws LibraryException;

    void reorderFolders(List<Long> idOrder) throws LibraryException;

    List<SetlistInfo> listSetlists() throws LibraryException;

    long addSetlist(String name, Long folderId) throws LibraryException;

    /**
     * Move a setlist into {@code folderId} (null = Unfiled) at {@code sortOrder},
     * renumbering other setlists in the target folder. Matches Python
     * {@code move_setlist_to_folder}.
     */
    void moveSetlistToFolder(long setlistId, Long folderId, int sortOrder) throws LibraryException;

    void updateSetlist(
            long id,
            String name,
            Long bandLayoutId,
            Long folderId,
            Integer sortOrder,
            boolean locked,
            Integer defaultChangeDurationSeconds,
            String notes,
            String setDate,
            String setTime,
            Integer targetDurationSeconds) throws LibraryException;

    void deleteSetlist(long id) throws LibraryException;

    /**
     * Create a new setlist in the same folder as {@code sourceSetlistId}, apply the given
     * metadata, and copy all source items (including per-item layouts, change-duration
     * overrides, and band assignments when the band layout is unchanged).
     *
     * @return the new setlist id
     */
    long duplicateSetlist(
            long sourceSetlistId,
            String name,
            Long bandLayoutId,
            boolean locked,
            Integer defaultChangeDurationSeconds,
            String notes,
            String setDate,
            String setTime,
            Integer targetDurationSeconds) throws LibraryException;

    /**
     * Copy songs from {@code sourceSetlistId} into {@code targetSetlistId} (prepend or append).
     * Only song order is copied; new rows use the target setlist's band layout when present
     * (no overrides or band assignments). Target metadata is not modified.
     *
     * @return number of items added
     */
    int mergeSetlistSongs(long targetSetlistId, long sourceSetlistId, boolean prepend)
            throws LibraryException;

    List<SetlistItemInfo> listItems(long setlistId) throws LibraryException;

    /**
     * Setlist items with song metadata needed for set export / CSV (transcriber, notes, status).
     */
    List<SetExportItemInfo> listItemsForExport(long setlistId) throws LibraryException;

    long addItem(
            long setlistId,
            long songId,
            int position,
            Integer overrideChangeDurationSeconds,
            Long songLayoutId) throws LibraryException;

    /**
     * If a library {@code SongLayout} exists for {@code (songId, bandLayoutId)}, link it on the
     * item and copy its assignments into {@code SetlistBandAssignment} when the item has no
     * override rows yet. No-op when no matching song layout exists.
     */
    void snapshotSongLayoutToItem(long itemId, long songId, long bandLayoutId)
            throws LibraryException;

    /**
     * Clear each item's setlist assignments, then snapshot from the song's layout for
     * {@code newBandLayoutId} (or unlink layouts when {@code newBandLayoutId} is null).
     */
    void remapItemsToBandLayout(long setlistId, Long newBandLayoutId) throws LibraryException;

    void updateItem(
            long itemId,
            Integer overrideChangeDurationSeconds,
            Long songLayoutId) throws LibraryException;

    void removeItem(long itemId) throws LibraryException;

    void reorderItems(long setlistId, List<Long> itemIdOrder) throws LibraryException;

    List<SetlistBandAssignmentInfo> listBandAssignments(long setlistItemId) throws LibraryException;

    void upsertBandAssignment(long setlistItemId, long playerId, Integer partNumber)
            throws LibraryException;

    void deleteBandAssignment(long setlistItemId, long playerId) throws LibraryException;
}
