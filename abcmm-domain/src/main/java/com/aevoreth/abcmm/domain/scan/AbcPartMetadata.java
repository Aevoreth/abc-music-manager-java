package com.aevoreth.abcmm.domain.scan;

/**
 * One ABC part block ({@code X:}) with Maestro part tags resolved for indexing.
 */
public record AbcPartMetadata(
        int partNumber,
        String partName,
        Long instrumentId,
        String titleFromT) {
}
