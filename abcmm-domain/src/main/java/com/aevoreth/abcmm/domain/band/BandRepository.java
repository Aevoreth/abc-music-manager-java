package com.aevoreth.abcmm.domain.band;

import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Band, membership, layout, and slot access. Implementations live in {@code abcmm-storage}.
 */
public interface BandRepository {

    List<BandInfo> listBands() throws LibraryException;

    long addBand(String name, String notes) throws LibraryException;

    void updateBand(long id, String name, String notes) throws LibraryException;

    void deleteBand(long id) throws LibraryException;

    void reorderBands(List<Long> idOrder) throws LibraryException;

    /**
     * Copy the band, its primary layout slots, and its optimizer profile.
     * Setlists and occurrence preferences are not copied. Player ids on the copy
     * stay the same as the source layout.
     */
    long duplicateBand(long bandId) throws LibraryException;

    List<PlayerInfo> listMembers(long bandId) throws LibraryException;

    void setMembers(long bandId, List<Long> playerIds) throws LibraryException;

    /**
     * Returns the single layout for a band, creating one named after the band when missing.
     * A band is treated as exactly one layout in the UI.
     */
    BandLayoutInfo getOrCreatePrimaryLayout(long bandId) throws LibraryException;

    /** Replaces {@code BandMember} rows with the players currently placed on the band's layout. */
    void syncMembersFromPrimaryLayout(long bandId) throws LibraryException;

    List<BandLayoutInfo> listLayouts(long bandId) throws LibraryException;

    long addLayout(long bandId, String name) throws LibraryException;

    void updateLayout(long layoutId, String name, String exportColumnOrderJson)
            throws LibraryException;

    void deleteLayout(long layoutId) throws LibraryException;

    List<BandLayoutSlotInfo> listSlots(long bandLayoutId) throws LibraryException;

    void setSlot(
            long bandLayoutId,
            long playerId,
            int x,
            int y,
            int widthUnits,
            int heightUnits) throws LibraryException;

    void deleteSlot(long bandLayoutId, long playerId) throws LibraryException;

    void replaceSlots(long bandLayoutId, List<BandLayoutSlotInfo> slots) throws LibraryException;

    /**
     * Replace {@code oldPlayerId} with {@code newPlayerId} on this band layout and transfer
     * song-layout parts for that layout, plus setlist part copies on sets that use this layout
     * or whose items are linked to a song layout for it. Also rewrites this band's optimizer
     * switch weights and instrument affinities, and part preferences on setlists that use
     * this layout. When the new player already has the same switch or affinity row, that
     * destination row is kept and the source row is dropped. Does not modify
     * {@code PlayerInstrument}. Adds the new player to {@code BandMember} (does not remove
     * the old member), matching Python.
     */
    void replacePlayerInBandLayout(
            long bandLayoutId,
            long bandId,
            long oldPlayerId,
            long newPlayerId) throws LibraryException;
}
