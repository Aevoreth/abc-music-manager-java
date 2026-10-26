package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aevoreth.abcmm.domain.export.ExportPartMeta;
import com.aevoreth.abcmm.domain.export.PartsJsonParser;

/**
 * Turns song parts JSON into required parts. Does not drop an occurrence when parsing fails;
 * the caller records an integrity diagnostic and keeps the item.
 */
public final class RequiredPartResolver {

    private RequiredPartResolver() {
    }

    public static boolean isUnparsable(String partsJson) {
        return partsJson != null && !partsJson.isBlank() && !partsJson.trim().startsWith("[");
    }

    public static List<RequiredPart> resolve(String partsJson, Map<Long, String> instrumentNames) {
        List<RequiredPart> parts = new ArrayList<>();
        for (ExportPartMeta meta : PartsJsonParser.parse(partsJson)) {
            Long instrumentId = PhysicalInstrumentEquivalence.resolveInstrumentId(
                    meta.instrumentId(), meta.madeFor(), instrumentNames);
            parts.add(new RequiredPart(meta.partNumber(), instrumentId));
        }
        return List.copyOf(parts);
    }
}
