package com.aevoreth.abcmm.domain.optimizer;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Band optimizer settings and setlist-occurrence soft preferences.
 * Implementations live in {@code abcmm-storage}. This store does not read
 * {@code has_proficiency} and does not store hard pins.
 *
 * <p>A missing band profile reads as {@link BandOptimizationProfile#defaults()}
 * and is not inserted. Saving one band does not change another. A stored
 * {@code settings_version} this build does not know is rejected rather than applied.
 *
 * <p><b>Duplicate band</b> copies the profile JSON and player-scoped rows. Player
 * ids stay the same, matching copied layout slots. Occurrence rows are not copied,
 * because setlists are not copied with the band.
 *
 * <p><b>Duplicate setlist</b> copies occurrence rows onto the new item ids.
 * A song-only merge does not.
 *
 * <p><b>Replace player on a layout</b> rewrites that band's switch weights and
 * instrument affinities from the old player to the new one. When the new player
 * already has the same row, the destination is kept and the source row is dropped.
 * Part-preference player ids are rewritten only on setlists that use that layout.
 * A part preference is keyed by occurrence part, so changing its player does not
 * collide with another row; a preference that already names the new player is left
 * as it is.
 *
 * <p><b>Delete player</b> removes that player's switch weights, affinities, and
 * part preferences before the player row. <b>Delete band</b> removes the profile
 * and player-scoped rows and leaves occurrence rows on setlists. <b>Remove item</b>,
 * <b>delete setlist</b>, and library-scan orphan items remove the three occurrence
 * tables with the item.
 */
public interface OptimizationProfileRepository {

    BandOptimizationProfile getBandProfile(long bandId) throws LibraryException;

    void saveBandProfile(long bandId, BandOptimizationProfile profile) throws LibraryException;

    OccurrencePreferences listOccurrencePreferences(long setlistId) throws LibraryException;

    void replaceOccurrencePreferences(long setlistId, OccurrencePreferences preferences)
            throws LibraryException;
}
