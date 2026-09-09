package com.aevoreth.abcmm.domain.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class PartsJsonParserTest {

    @Test
    void parsesMadeForWhenInstrumentIdMissing() {
        List<ExportPartMeta> parts = PartsJsonParser.parse(
                "[{\"part_number\":1,\"part_name\":\"Melody\",\"made_for\":\"Basic Lute\",\"instrument_id\":null}]");
        assertEquals(1, parts.size());
        assertEquals(1, parts.get(0).partNumber());
        assertEquals("Melody", parts.get(0).partName());
        assertNull(parts.get(0).instrumentId());
        assertEquals("Basic Lute", parts.get(0).madeFor());
    }
}
