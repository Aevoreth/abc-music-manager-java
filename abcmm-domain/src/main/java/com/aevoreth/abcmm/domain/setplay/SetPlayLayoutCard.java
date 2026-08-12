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
}
