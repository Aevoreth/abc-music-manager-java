package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;

class AbcMetadataParserTest {

    private final AbcMetadataParser parser = new AbcMetadataParser();

    @Test
    void prefersMaestroTagsOverHeaders() {
        String abc = """
                %%song-title Maestro Title
                %%song-composer Composer A, Composer B
                %%song-transcriber Pat
                %%song-duration 3:45
                %%export-timestamp 2024-01-02T03:04:05Z
                T: Header Title
                C: Header Composer
                Z: Header Transcriber
                X:1
                %%part-name Melody
                %%made-for Basic Lute
                T: Part Track
                """;
        AbcFileMetadata meta = parser.parse(abc, "fallback.abc", name -> 42L);
        assertEquals("Maestro Title", meta.title());
        assertEquals("Composer A, Composer B", meta.composers());
        assertEquals("Pat", meta.transcriber());
        assertEquals(225, meta.durationSeconds());
        assertEquals("2024-01-02T03:04:05Z", meta.exportTimestamp());
        assertEquals(1, meta.parts().size());
        AbcPartMetadata part = meta.parts().get(0);
        assertEquals(1, part.partNumber());
        assertEquals("Melody", part.partName());
        assertEquals(42L, part.instrumentId());
        assertEquals("Part Track", part.titleFromT());
    }

    @Test
    void fallsBackToHeadersThenFilename() {
        String abc = """
                T: From T
                C: From C
                Z: From Z
                X:2
                """;
        AbcFileMetadata withHeaders = parser.parse(abc, "ignored.abc");
        assertEquals("From T", withHeaders.title());
        assertEquals("From C", withHeaders.composers());
        assertEquals("From Z", withHeaders.transcriber());
        assertNull(withHeaders.durationSeconds());
        assertEquals(1, withHeaders.parts().size());
        assertEquals(2, withHeaders.parts().get(0).partNumber());

        AbcFileMetadata filenameOnly = parser.parse("X:1\n", "My Song.abc");
        assertEquals("My Song.abc", filenameOnly.title());
        assertEquals("Unknown", filenameOnly.composers());
    }

    @Test
    void doesNotSplitComposersOnCommas() {
        AbcFileMetadata meta = parser.parse("%%song-composer Ada, Bea, Cai\nX:1\n", "x.abc");
        assertEquals("Ada, Bea, Cai", meta.composers());
    }

    @Test
    void parsesMultiplePartsAndLeavesInstrumentNullWithoutResolver() {
        String abc = """
                %%song-title Multi
                X:1
                %%part-name One
                %%made-for Basic Harp
                X:3
                %%part-name Three
                """;
        AbcFileMetadata meta = parser.parse(abc, "multi.abc");
        assertEquals(2, meta.parts().size());
        assertEquals(1, meta.parts().get(0).partNumber());
        assertNull(meta.parts().get(0).instrumentId());
        assertEquals(3, meta.parts().get(1).partNumber());
        assertEquals("Three", meta.parts().get(1).partName());
    }

    @Test
    void parseMmSsRejectsInvalid() {
        assertEquals(125, AbcMetadataParser.parseMmSs("2:05"));
        assertNull(AbcMetadataParser.parseMmSs("2:60"));
        assertNull(AbcMetadataParser.parseMmSs("bad"));
        assertNull(AbcMetadataParser.parseMmSs(""));
    }

    @Test
    void blankTitleBecomesUnknown() {
        AbcFileMetadata meta = parser.parse("%%song-title   \nX:1\n", null);
        assertEquals("Unknown", meta.title());
        assertTrue(meta.parts().size() >= 1);
    }
}
