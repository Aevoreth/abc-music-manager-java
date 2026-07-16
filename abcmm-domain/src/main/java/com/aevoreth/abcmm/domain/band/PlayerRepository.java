package com.aevoreth.abcmm.domain.band;

import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Player and instrument access. Implementations live in {@code abcmm-storage}.
 */
public interface PlayerRepository {

    List<PlayerInfo> listPlayers() throws LibraryException;

    PlayerInfo getPlayer(long id) throws LibraryException;

    long addPlayer(String name, Integer level, String characterClass) throws LibraryException;

    void updatePlayer(long id, String name, Integer level, String characterClass)
            throws LibraryException;

    void deletePlayer(long id) throws LibraryException;

    List<InstrumentInfo> listInstruments() throws LibraryException;

    List<PlayerInstrumentInfo> listPlayerInstruments(long playerId) throws LibraryException;

    void setPlayerInstrument(
            long playerId,
            long instrumentId,
            boolean hasInstrument,
            boolean hasProficiency,
            String notes) throws LibraryException;
}
