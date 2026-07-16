package com.aevoreth.abcmm.domain.band;

/**
 * Player row from the {@code Player} table.
 */
public record PlayerInfo(long id, String name, Integer level, String characterClass) {
}
