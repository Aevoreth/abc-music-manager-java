package com.aevoreth.abcmm.domain.library;

import java.util.List;

/**
 * Status, folder-rule, and account-target CRUD. Implementations live in {@code abcmm-storage}.
 */
public interface SettingsRepository {

    List<StatusInfo> listStatuses() throws LibraryException;

    long addStatus(String name, String color, Integer sortOrder) throws LibraryException;

    void updateStatus(long id, String name, String color, Integer sortOrder) throws LibraryException;

    void deleteStatus(long id) throws LibraryException;

    void reorderStatuses(List<Long> idOrder) throws LibraryException;

    List<FolderRuleInfo> listFolderRules() throws LibraryException;

    long addFolderRule(String path, boolean enabled, boolean includeInExport) throws LibraryException;

    void updateFolderRule(long id, String path, boolean enabled, boolean includeInExport)
            throws LibraryException;

    void deleteFolderRule(long id) throws LibraryException;

    List<AccountTargetInfo> listAccountTargets() throws LibraryException;

    long addAccountTarget(String accountName, String pluginDataPath, boolean enabled)
            throws LibraryException;

    void updateAccountTarget(long id, String accountName, String pluginDataPath, boolean enabled)
            throws LibraryException;

    void deleteAccountTarget(long id) throws LibraryException;
}
