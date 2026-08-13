package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetPlayWorkerPathsTest {

    @TempDir
    Path tmp;

    @Test
    void syncPreservesExistingDatabaseId() throws Exception {
        Path bundle = tmp.resolve("bundle");
        Path deploy = tmp.resolve("deploy");
        Files.createDirectories(bundle);
        Files.createDirectories(deploy);
        Files.writeString(bundle.resolve("package.json"), "{}\n", StandardCharsets.UTF_8);
        Files.writeString(bundle.resolve("wrangler.toml"),
                "database_id = \"REPLACE_WITH_D1_ID\"\n", StandardCharsets.UTF_8);
        String realId = "c5b8c5e8-1111-2222-3333-444444444444";
        Files.writeString(deploy.resolve("wrangler.toml"),
                "database_id = \"" + realId + "\"\n", StandardCharsets.UTF_8);

        SetPlayWorkerPaths.syncTemplateToDeploy(bundle, deploy, null);

        String dest = Files.readString(deploy.resolve("wrangler.toml"), StandardCharsets.UTF_8);
        assertTrue(dest.contains(realId));
        assertFalse(dest.contains("REPLACE_WITH_D1_ID"));
    }
}
