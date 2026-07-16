package com.aevoreth.abcmm.domain.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.prefs.DefaultFilters;

class LibraryFilterTest {

    @Test
    void titleTokensSplitOnWhitespace() {
        LibraryFilter filter = new LibraryFilter();
        filter.setTitleOrComposer("  Alpha   March ");
        assertEquals(List.of("alpha", "march"), filter.titleTokens());
    }

    @Test
    void fullRatingAndPartsRangesAreUnrestricted() {
        LibraryFilter filter = new LibraryFilter();
        filter.setRatingFrom(0);
        filter.setRatingTo(5);
        filter.setPartsMin(1);
        filter.setPartsMax(24);
        assertNull(filter.effectiveRatingMin());
        assertNull(filter.effectiveRatingMax());
        assertNull(filter.effectivePartsMin());
        assertNull(filter.effectivePartsMax());
    }

    @Test
    void defaultFiltersConvertToLibraryFilter() {
        DefaultFilters defaults = DefaultFilters.builtins();
        defaults.setInSet("no");
        defaults.setStatusIds(List.of(3L));
        LibraryFilter filter = defaults.toLibraryFilter();
        assertEquals(LibraryFilter.InSet.NO, filter.inSet());
        assertEquals(List.of(3L), filter.statusIds());
        assertTrue(filter.titleOrComposer().isEmpty());
    }

    @Test
    void copyPreservesTranscribersAndLastPlayedNever() {
        LibraryFilter filter = new LibraryFilter();
        filter.setTranscribers(List.of("Ada", "Ben"));
        filter.setLastPlayedNever(true);
        LibraryFilter copy = filter.copy();
        assertEquals(List.of("Ada", "Ben"), copy.transcribers());
        assertTrue(copy.lastPlayedNever());
    }
}
