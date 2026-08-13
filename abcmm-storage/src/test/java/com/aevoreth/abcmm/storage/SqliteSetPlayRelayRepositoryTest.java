package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setplay.SetPlayRelayInfo;

class SqliteSetPlayRelayRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void crudAndCopyFromPreferences() throws Exception {
        Path dbPath = tempDir.resolve("relays.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteSetPlayRelayRepository repo = new SqliteSetPlayRelayRepository(database);

            Preferences prefs = new Preferences();
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", "uuid-1");
            raw.put("name", "Main");
            raw.put("url", "wss://example.workers.dev");
            prefs.setSetPlayRelays(List.of(raw));
            prefs.setSetPlaySelectedRelayId("uuid-1");

            assertTrue(repo.copyRelaysFromPreferencesIfEmpty(prefs));
            assertTrue(prefs.setPlayRelays().isEmpty());
            List<SetPlayRelayInfo> relays = repo.listRelays();
            assertEquals(1, relays.size());
            assertEquals("Main", relays.get(0).name());
            assertEquals("wss://example.workers.dev", relays.get(0).url());
            assertEquals(SetPlayRelayInfo.DEFAULT_RETENTION_DAYS, relays.get(0).retentionDays());
            assertEquals(String.valueOf(relays.get(0).id()), prefs.setPlaySelectedRelayId());

            assertFalse(repo.copyRelaysFromPreferencesIfEmpty(prefs));

            long id = relays.get(0).id();
            repo.updateRelay(id, "Main*", "https://example.workers.dev", "secret-token", 21);
            SetPlayRelayInfo updated = repo.findRelay(id).orElseThrow();
            assertEquals("Main*", updated.name());
            assertEquals("https://example.workers.dev", updated.url());
            assertEquals("secret-token", updated.token());
            assertEquals(21, updated.retentionDays());

            repo.upsertPublishedSession(id, "ab12cd3", "RAVE Test", "012345", 9L);
            assertEquals("012345", repo.findPublishedSession(id, "AB12CD3").orElseThrow().passphrase());
            repo.updatePublishedSessionName(id, "AB12CD3", "RAVE Final");
            assertEquals("RAVE Final", repo.listPublishedSessions(id).get(0).name());
            repo.deletePublishedSession(id, "ab12cd3");
            assertTrue(repo.listPublishedSessions(id).isEmpty());

            repo.deleteRelay(id);
            assertTrue(repo.listRelays().isEmpty());
        }
    }
}
