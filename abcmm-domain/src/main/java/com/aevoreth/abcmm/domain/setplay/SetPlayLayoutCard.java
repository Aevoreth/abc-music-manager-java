package com.aevoreth.abcmm.domain.setplay;

/**
 * Read-only band-layout card for Set Play (up-next song, neighbor gutters, warn flags).
 * Mirrors Python {@code LayoutCard} fields used by Set Play.
 */
public record SetPlayLayoutCard(
        long playerId,
        String playerName,
        int x,
        int y,
        int widthUnits,
        int heightUnits,
        String partNumber,
        String partName,
        String instrumentName,
        boolean instrumentWarning,
        boolean partDuplicate,
        boolean useSetlistPlayerHeader,
        String neighborPrevPartLabel,
        String neighborNextPartLabel,
        boolean instrumentChangedFromPriorInSet) {

    /** Formation-only card used when NEXT is unset (Clear session, start of set). */
    public SetPlayLayoutCard asPlaceholder() {
        return new SetPlayLayoutCard(
                playerId,
                playerName,
                x,
                y,
                widthUnits,
                heightUnits,
                "---",
                "(Part Name)",
                "(Made for Instrument)",
                false,
                false,
                useSetlistPlayerHeader,
                "",
                "",
                false);
    }

    /**
     * Same assignment (part/instrument) with live Current/Next gutters and
     * instrument-change highlighting applied at display time.
     */
    public SetPlayLayoutCard withLiveHints(
            String neighborPrevPartLabel,
            String neighborNextPartLabel,
            boolean instrumentChangedFromPriorInSet) {
        return new SetPlayLayoutCard(
                playerId,
                playerName,
                x,
                y,
                widthUnits,
                heightUnits,
                partNumber,
                partName,
                instrumentName,
                instrumentWarning,
                partDuplicate,
                useSetlistPlayerHeader,
                neighborPrevPartLabel == null ? "" : neighborPrevPartLabel,
                neighborNextPartLabel == null ? "" : neighborNextPartLabel,
                instrumentChangedFromPriorInSet);
    }
}
