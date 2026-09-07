package com.aevoreth.abcmm.domain.setplay.relay;

import java.util.Locale;

/**
 * Pure policy for Set Play relay reconnect, snapshot apply, and share-link PIN lookup.
 */
public final class SetPlayRelayRules {

    public static final long RECONNECT_BASE_MS = 1_000L;
    public static final long RECONNECT_MAX_MS = 30_000L;
    public static final long PING_INTERVAL_MS = 25_000L;

    private SetPlayRelayRules() {
    }

    public static boolean shouldAutoReconnect(boolean userClosed, String closeReason) {
        if (userClosed) {
            return false;
        }
        String why = closeReason == null ? "" : closeReason.toLowerCase(Locale.ROOT);
        return !why.contains("session ended");
    }

    public static long reconnectDelayMillis(int attempt) {
        int n = Math.max(1, attempt);
        int shift = Math.min(n - 1, 10);
        long delay = RECONNECT_BASE_MS << shift;
        return Math.min(delay, RECONNECT_MAX_MS);
    }

    /**
     * Leader should push the local runthrough on connect when the loaded set is the
     * hosted one (not a dirty Load-set, not snapshot-only hosting).
     */
    public static boolean shouldPushLocalOnLeaderConnect(
            boolean assistantMode,
            boolean songListDirtyVsRelay,
            boolean hostingFromSnapshot,
            boolean canHostFromLocal) {
        return !assistantMode && !songListDirtyVsRelay && !hostingFromSnapshot && canHostFromLocal;
    }

    /**
     * Assistants always apply. Leaders ignore echo/stale revisions and ignore
     * snapshots after Load set until Republish.
     */
    public static boolean shouldApplyRemoteSnapshot(
            boolean assistantMode,
            boolean songListDirtyVsRelay,
            int incomingRevision,
            int lastPushedRevision) {
        if (assistantMode) {
            return true;
        }
        if (songListDirtyVsRelay) {
            return false;
        }
        return incomingRevision > lastPushedRevision;
    }

    /** Durable Object: drop leader snapshots that would rewind the room. */
    public static boolean shouldAcceptLeaderRevision(int incomingRevision, int storedRevision) {
        return incomingRevision >= storedRevision;
    }

    /**
     * PIN for Copy Download and Play must follow the <em>selected</em> session, never
     * a different connected session's passphrase.
     */
    public static String pinForSelectedSession(
            String selectedCode,
            String connectedCode,
            String connectedPin,
            String localPinForSelected) {
        if (selectedCode == null || selectedCode.isBlank()) {
            return null;
        }
        if (localPinForSelected != null && !localPinForSelected.isBlank()) {
            return localPinForSelected;
        }
        if (connectedPin != null && !connectedPin.isBlank()
                && selectedCode.equalsIgnoreCase(connectedCode)) {
            return connectedPin;
        }
        return null;
    }
}
