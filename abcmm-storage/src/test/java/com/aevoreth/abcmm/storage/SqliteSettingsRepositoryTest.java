package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.StatusInfo;

class SqliteSettingsRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void statusFolderRuleAndAccountTargetCrud() throws Exception {
        Path dbPath = tempDir.resolve("settings.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteSettingsRepository repo = new SqliteSettingsRepository(database);

            long statusId = repo.addStatus("CustomA", "#00ff00", 10);
            List<StatusInfo> statuses = repo.listStatuses();
            assertTrue(statuses.stream().anyMatch(s -> s.id() == statusId && s.name().equals("CustomA")));

            repo.updateStatus(statusId, "CustomA*", "#11ff11", 5);
            StatusInfo updated = repo.listStatuses().stream()
                    .filter(s -> s.id() == statusId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("CustomA*", updated.name());
            assertEquals("#11ff11", updated.color());
            assertEquals(5, updated.sortOrder());

            long otherStatus = repo.addStatus("CustomB", "#000000", 0);
            // Reorder only the two custom rows relative to each other.
            repo.reorderStatuses(List.of(otherStatus, statusId));
            assertEquals(0, repo.listStatuses().stream()
                    .filter(s -> s.id() == otherStatus).findFirst().orElseThrow().sortOrder());
            assertEquals(1, repo.listStatuses().stream()
                    .filter(s -> s.id() == statusId).findFirst().orElseThrow().sortOrder());

            repo.deleteStatus(otherStatus);
            assertFalse(repo.listStatuses().stream().anyMatch(s -> s.id() == otherStatus));

            long ruleId = repo.addFolderRule("Excluded/Path", true, false);
            FolderRuleInfo rule = repo.listFolderRules().stream()
                    .filter(r -> r.id() == ruleId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("Excluded/Path", rule.path());
            assertTrue(rule.enabled());
            assertFalse(rule.includeInExport());

            repo.updateFolderRule(ruleId, "Excluded/Other", false, true);
            FolderRuleInfo updatedRule = repo.listFolderRules().stream()
                    .filter(r -> r.id() == ruleId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("Excluded/Other", updatedRule.path());
            assertFalse(updatedRule.enabled());
            assertTrue(updatedRule.includeInExport());

            repo.deleteFolderRule(ruleId);
            assertFalse(repo.listFolderRules().stream().anyMatch(r -> r.id() == ruleId));

            long targetId = repo.addAccountTarget("Main", "C:/PluginData", true);
            AccountTargetInfo target = repo.listAccountTargets().stream()
                    .filter(t -> t.id() == targetId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("Main", target.accountName());
            assertEquals("C:/PluginData", target.pluginDataPath());
            assertTrue(target.enabled());

            repo.updateAccountTarget(targetId, "Alt", "D:/Data", false);
            AccountTargetInfo updatedTarget = repo.listAccountTargets().stream()
                    .filter(t -> t.id() == targetId)
                    .findFirst()
                    .orElseThrow();
            assertEquals("Alt", updatedTarget.accountName());
            assertEquals("D:/Data", updatedTarget.pluginDataPath());
            assertFalse(updatedTarget.enabled());

            repo.deleteAccountTarget(targetId);
            assertFalse(repo.listAccountTargets().stream().anyMatch(t -> t.id() == targetId));
        }
    }
}
