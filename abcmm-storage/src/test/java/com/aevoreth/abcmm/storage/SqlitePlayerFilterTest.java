package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerFilter;
import com.aevoreth.abcmm.domain.band.PlayerInfo;

class SqlitePlayerFilterTest {

    @TempDir
    Path tempDir;

    @Test
    void listPlayersAppliesNameLevelClassAndInstrumentFilters() throws Exception {
        Path dbPath = tempDir.resolve("player-filter.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            long alice = players.addPlayer("Alice", 50, "Minstrel");
            long bob = players.addPlayer("Bob", 20, "Guardian");
            long carol = players.addPlayer("Carol", null, "Minstrel");

            List<InstrumentInfo> instruments = players.listInstruments();
            assertTrue(instruments.size() >= 2);
            long luteId = instruments.get(0).id();
            long harpId = instruments.get(1).id();
            players.setPlayerInstrument(alice, luteId, true, false, null);
            players.setPlayerInstrument(bob, harpId, true, false, null);
            players.setPlayerInstrument(carol, luteId, false, false, null);

            List<PlayerInfo> byName = players.listPlayers(new PlayerFilter("ali", null, null, null, null));
            assertEquals(1, byName.size());
            assertEquals(alice, byName.get(0).id());

            List<PlayerInfo> byLevel = players.listPlayers(new PlayerFilter(null, 40, null, null, null));
            assertEquals(1, byLevel.size());
            assertEquals(alice, byLevel.get(0).id());

            List<PlayerInfo> byClass = players.listPlayers(new PlayerFilter(null, null, null, "minst", null));
            assertEquals(2, byClass.size());

            List<PlayerInfo> byInstrument = players.listPlayers(
                    new PlayerFilter(null, null, null, null, List.of(luteId)));
            assertEquals(1, byInstrument.size());
            assertEquals(alice, byInstrument.get(0).id());

            List<PlayerInfo> all = players.listPlayers(PlayerFilter.none());
            assertEquals(3, all.size());
            assertEquals("Alice", all.get(0).name());
            assertEquals("Bob", all.get(1).name());
            assertEquals("Carol", all.get(2).name());
        }
    }
}
