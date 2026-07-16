package com.aevoreth.abcmm.domain.setlist;

import java.util.List;

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

    List<SetlistItemInfo> listItems(long setlistId) throws LibraryException;

    long addItem(
            long setlistId,
            long songId,
            int position,
            Integer overrideChangeDurationSeconds,
            Long songLayoutId) throws LibraryException;

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
