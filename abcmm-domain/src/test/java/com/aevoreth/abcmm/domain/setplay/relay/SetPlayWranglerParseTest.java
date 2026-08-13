package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SetPlayWranglerParseTest {

    private static final String ID = "c5b8c5e8-1111-2222-3333-444444444444";
    private static final String OTHER = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    void extractTomlSkipsPlaceholder() {
        assertNull(SetPlayWranglerParse.extractTomlDatabaseId(
                "database_id = \"REPLACE_WITH_D1_ID\"\n"));
        assertEquals(ID, SetPlayWranglerParse.extractTomlDatabaseId(
                "database_id = \"REPLACE_WITH_D1_ID\"\ndatabase_id = \"" + ID + "\"\n"));
    }

    @Test
    void replaceTomlDatabaseId() {
        String toml = "database_name = \"abc-set-play-registry\"\ndatabase_id = \"REPLACE_WITH_D1_ID\"\n";
        String patched = SetPlayWranglerParse.replaceTomlDatabaseId(toml, ID);
        assertTrue(patched.contains("database_id = \"" + ID + "\""));
        assertFalse(patched.contains("REPLACE_WITH_D1_ID"));
    }

    @Test
    void findD1IdFromJsonNameAfterUuid() {
        String json = """
                [
                  {"uuid":"%s","name":"other-db"},
                  {"uuid":"%s","name":"abc-set-play-registry","created_at":"2026-01-01"}
                ]
                """.formatted(OTHER, ID);
        assertEquals(ID, SetPlayWranglerParse.findD1IdByName(json, "abc-set-play-registry"));
    }

    @Test
    void findD1IdFromJsonNameBeforeUuid() {
        String json = """
                { "name": "abc-set-play-registry", "uuid": "%s" }
                """.formatted(ID);
        assertEquals(ID, SetPlayWranglerParse.findD1IdByName(json, "abc-set-play-registry"));
    }

    @Test
    void findD1IdFromTable() {
        String table = """
                ┌──────────────────────────────────────┬─────────────────────────┐
                │ uuid                                 │ name                    │
                ├──────────────────────────────────────┼─────────────────────────┤
                │ %s │ other-db                │
                │ %s │ abc-set-play-registry   │
                └──────────────────────────────────────┴─────────────────────────┘
                """.formatted(OTHER, ID);
        assertEquals(ID, SetPlayWranglerParse.findD1IdByName(table, "abc-set-play-registry"));
    }

    @Test
    void extractSessionZipsPairsCodeAndKey() {
        String out = """
                [{"results":[{"code":"ABC12DE","r2_key":"zips/ABC12DE.zip"}],"success":true}]
                """;
        List<SetPlayWranglerParse.SessionZip> zips = SetPlayWranglerParse.extractSessionZips(out);
        assertEquals(1, zips.size());
        assertEquals("ABC12DE", zips.get(0).code());
        assertEquals("zips/ABC12DE.zip", zips.get(0).r2Key());
    }

    @Test
    void extractR2KeysFromExecuteJson() {
        String out = """
                [{"results":[{"r2_key":"zips/ABC12DE.zip"},{"r2_key":null}],"success":true}]
                """;
        assertEquals(List.of("zips/ABC12DE.zip"), SetPlayWranglerParse.extractR2Keys(out));
    }

    @Test
    void extractR2KeysRejectsTraversal() {
        assertTrue(SetPlayWranglerParse.extractR2Keys(
                "\"r2_key\":\"../secret\"").isEmpty());
        assertFalse(SetPlayWranglerParse.isSafeR2Key("zips/../x.zip"));
        assertTrue(SetPlayWranglerParse.isSafeR2Key("zips/ABC12DE.zip"));
    }

    @Test
    void missingAndNotEmptyHints() {
        assertTrue(SetPlayWranglerParse.looksLikeMissingResource("Couldn't find a Worker"));
        assertTrue(SetPlayWranglerParse.looksLikeBucketNotEmpty(
                "The bucket you tried to delete (abc-set-play-zips) is not empty"));
    }
}
