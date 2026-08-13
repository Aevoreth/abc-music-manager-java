package com.aevoreth.abcmm.domain.setplay;

import java.util.List;
import java.util.Optional;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.prefs.Preferences;

/**
 * CRUD for Set Play relays and locally remembered published sessions.
 */
public interface SetPlayRelayRepository {

    List<SetPlayRelayInfo> listRelays() throws LibraryException;

    Optional<SetPlayRelayInfo> findRelay(long id) throws LibraryException;

    long addRelay(String name, String url, String token, int retentionDays) throws LibraryException;

    void updateRelay(long id, String name, String url, String token, int retentionDays)
            throws LibraryException;

    void deleteRelay(long id) throws LibraryException;

    /**
     * If the relay table is empty, copy {@code preferences.setPlayRelays()} into SQLite
     * and update {@code setPlaySelectedRelayId} to the new integer ids.
     *
     * @return true if any rows were copied
     */
    boolean copyRelaysFromPreferencesIfEmpty(Preferences preferences) throws LibraryException;

    List<SetPlayPublishedSessionInfo> listPublishedSessions(long relayId) throws LibraryException;

    Optional<SetPlayPublishedSessionInfo> findPublishedSession(long relayId, String code)
            throws LibraryException;

    long upsertPublishedSession(
            long relayId, String code, String name, String passphrase, Long setlistId)
            throws LibraryException;

    void updatePublishedSessionName(long relayId, String code, String name) throws LibraryException;

    void deletePublishedSession(long relayId, String code) throws LibraryException;
}
