package com.aevoreth.abcmm.domain.export;

/**
 * One part entry from song {@code parts} JSON for set export.
 */
public record ExportPartMeta(
        int partNumber,
        String partName,
        String titleFromT,
        Long instrumentId,
        String madeFor) {

    public ExportPartMeta(int partNumber, String partName, String titleFromT, Long instrumentId) {
        this(partNumber, partName, titleFromT, instrumentId, null);
    }
}
