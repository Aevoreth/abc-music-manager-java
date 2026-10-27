package com.aevoreth.abcmm.domain.optimizer;

import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Band optimizer settings, setlist-occurrence soft preferences, and hard pins.
 * Implementations live in {@code abcmm-storage}. This store does not read
 * {@code has_proficiency}.
 *
 * <p>A missing band profile reads as {@link BandOptimizationProfile#defaults()} and is not
 * inserted. Saving one band does not change another. A stored {@code settings_version} this build
 * does not know is rejected rather than applied. A profile document that omits {@code searchBudget}
 * reads as {@link SearchBudgetPreset#NORMAL}. Optional instrument-first placements live in the
 * profile JSON; older documents read with an empty list. Copy, player replacement, and deletion
 * preserve or rewrite their character references along with the other profile settings.
 *
 * <p><b>Duplicate band</b> copies the profile JSON and player-scoped rows. Player
 * ids stay the same, matching copied layout slots. Occurrence rows and pins are
 * not copied, because setlists are not copied with the band.
 *
 * <p><b>Duplicate setlist</b> copies occurrence rows and pins onto the new item ids.
 * A song-only merge does not.
 *
 * <p><b>Replace player on a layout</b> rewrites that band's switch weights and
 * instrument affinities from the old player to the new one. When the new player
 * already has the same row, the destination is kept and the source row is dropped.
 * Part-preference and pin player ids are rewritten only on setlists that use that
 * layout. A part preference is keyed by occurrence part, so changing its player does
 * not collide with another row; a preference that already names the new player is
 * left as it is. A pin is also one row per occurrence part, so the rewrite changes
 * who is pinned.
 *
 * <p><b>Delete player</b> removes that player's switch weights, affinities, part
 * preferences, and pins before the player row. <b>Delete band</b> removes the profile
 * and player-scoped rows and leaves occurrence rows and pins on setlists.
 * <b>Remove item</b>, <b>delete setlist</b>, and library-scan orphan items remove
 * the occurrence tables and pins with the item.
 *
 * <p>{@link #replaceOccurrencePreferences} and {@link #replacePins} reject a locked
 * setlist. Band profile saves do not.
 */
public interface OptimizationProfileRepository {

    String LOCKED_SETLIST_MESSAGE =
            "This setlist is locked. Unlock it before changing assignments, preferences, or pins.";

    BandOptimizationProfile getBandProfile(long bandId) throws LibraryException;

    void saveBandProfile(long bandId, BandOptimizationProfile profile) throws LibraryException;

    OccurrencePreferences listOccurrencePreferences(long setlistId) throws LibraryException;

    void replaceOccurrencePreferences(long setlistId, OccurrencePreferences preferences)
            throws LibraryException;

    /**
     * Pins for one setlist, sorted by item id then part number. Two parts may name
     * the same player; that conflict is kept so a later check can explain it.
     */
    List<AssignmentPin> listPins(long setlistId) throws LibraryException;

    /**
     * Replace every pin on the setlist. Two pins for the same item and part are rejected.
     * A locked setlist is rejected and the previous pins stay.
     */
    void replacePins(long setlistId, List<AssignmentPin> pins) throws LibraryException;
}
