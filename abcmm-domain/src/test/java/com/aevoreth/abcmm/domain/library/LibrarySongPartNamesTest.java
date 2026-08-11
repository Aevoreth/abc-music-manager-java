package com.aevoreth.abcmm.domain.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LibrarySongPartNamesTest {

    @Test
    void formatsPartNumberNameAndMadeFor() {
        LibrarySong song = songWithParts("""
                [{"part_number":51,"part_name":"Basic Flute 1","made_for":"Basic Flute"},
                 {"part_number":52,"part_name":null,"made_for":null,"instrument_id":7}]
                """);
        assertEquals(
                List.of("51: Basic Flute 1 (Basic Flute)", "52: Part 52 (Basic Clarinet)"),
                song.partNames(Map.of(7L, "Basic Clarinet")));
    }

    @Test
    void omitsInstrumentWhenUnavailable() {
        LibrarySong song = songWithParts(
                "[{\"part_number\":1,\"part_name\":\"Melody\"},{\"part_number\":2}]");
        assertEquals(List.of("1: Melody", "2: Part 2"), song.partNames());
    }

    private static LibrarySong songWithParts(String partsJson) {
        return new LibrarySong(
                1L, "Title", "Composer", null, 60, 2, partsJson, null, 0, null, null, null, null,
                null, null, false);
    }
}
