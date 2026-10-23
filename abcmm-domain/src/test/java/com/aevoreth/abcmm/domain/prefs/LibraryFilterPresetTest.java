package com.aevoreth.abcmm.domain.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.library.LibraryFilter;

class LibraryFilterPresetTest {

    @Test
    void reservedNamesAreRejected() {
        assertTrue(LibraryFilterPreset.isReservedName("Default"));
        assertTrue(LibraryFilterPreset.isReservedName(" none "));
        assertTrue(LibraryFilterPreset.isReservedName("<- Modified ->"));
        assertFalse(LibraryFilterPreset.isReservedName("Unrated"));
        assertFalse(LibraryFilterPreset.isReservedName(""));
    }

    @Test
    void sanitizeDropsReservedBlankAndDuplicateNames() {
        DefaultFilters filters = DefaultFilters.builtins();
        List<LibraryFilterPreset> sanitized = LibraryFilterPreset.sanitize(List.of(
                new LibraryFilterPreset("  ", filters),
                new LibraryFilterPreset("None", filters),
                new LibraryFilterPreset("Unrated", filters),
                new LibraryFilterPreset("unrated", filters),
                new LibraryFilterPreset("Default", filters),
                new LibraryFilterPreset("Recent", filters)));

        assertEquals(List.of("Unrated", "Recent"), sanitized.stream().map(LibraryFilterPreset::name).toList());
    }

    @Test
    void copyAndEqualsIncludeTranscribers() {
        DefaultFilters filters = DefaultFilters.builtins();
        filters.setTranscribers(List.of("Ada", " Ben "));
        DefaultFilters copy = filters.copy();
        assertEquals(List.of("Ada", "Ben"), copy.transcribers());
        assertEquals(filters, copy);

        copy.setTranscribers(List.of("Ada"));
        assertNotEquals(filters, copy);
    }

    @Test
    void fromLibraryFilterIgnoresTitleAndCapturesNever() {
        LibraryFilter filter = new LibraryFilter();
        filter.setTitleOrComposer("March");
        filter.setTranscribers(List.of("Ada"));
        filter.setLastPlayedNever(true);
        filter.setInSet(LibraryFilter.InSet.NO);

        DefaultFilters captured = DefaultFilters.fromLibraryFilter(filter);
        assertEquals("no", captured.inSet());
        assertEquals(List.of("Ada"), captured.transcribers());
        assertEquals(null, captured.lastPlayedFromSecondsAgo());
        assertEquals(null, captured.lastPlayedToSecondsAgo());

        LibraryFilter roundTrip = captured.toLibraryFilter();
        assertTrue(roundTrip.titleOrComposer().isEmpty());
        assertTrue(roundTrip.lastPlayedNever());
        assertEquals(List.of("Ada"), roundTrip.transcribers());
    }
}
