package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SetPlayRelayRulesTest {

    @Test
    void shouldAutoReconnectExceptUserCloseOrSessionEnded() {
        assertTrue(SetPlayRelayRules.shouldAutoReconnect(false, "bye"));
        assertTrue(SetPlayRelayRules.shouldAutoReconnect(false, null));
        assertFalse(SetPlayRelayRules.shouldAutoReconnect(true, "bye"));
        assertFalse(SetPlayRelayRules.shouldAutoReconnect(false, "session ended"));
        assertFalse(SetPlayRelayRules.shouldAutoReconnect(false, "Session Ended by leader"));
    }

    @Test
    void reconnectDelayBackoffCaps() {
        assertEquals(1_000L, SetPlayRelayRules.reconnectDelayMillis(1));
        assertEquals(2_000L, SetPlayRelayRules.reconnectDelayMillis(2));
        assertEquals(4_000L, SetPlayRelayRules.reconnectDelayMillis(3));
        assertEquals(30_000L, SetPlayRelayRules.reconnectDelayMillis(20));
        assertEquals(1_000L, SetPlayRelayRules.reconnectDelayMillis(0));
    }

    @Test
    void shouldPushLocalOnLeaderConnect() {
        assertTrue(SetPlayRelayRules.shouldPushLocalOnLeaderConnect(false, false, false, true));
        assertFalse(SetPlayRelayRules.shouldPushLocalOnLeaderConnect(true, false, false, true));
        assertFalse(SetPlayRelayRules.shouldPushLocalOnLeaderConnect(false, true, false, true));
        assertFalse(SetPlayRelayRules.shouldPushLocalOnLeaderConnect(false, false, true, true));
        assertFalse(SetPlayRelayRules.shouldPushLocalOnLeaderConnect(false, false, false, false));
    }

    @Test
    void shouldApplyRemoteSnapshot() {
        assertTrue(SetPlayRelayRules.shouldApplyRemoteSnapshot(true, false, 1, 99));
        assertFalse(SetPlayRelayRules.shouldApplyRemoteSnapshot(false, true, 50, 1));
        assertFalse(SetPlayRelayRules.shouldApplyRemoteSnapshot(false, false, 5, 5));
        assertFalse(SetPlayRelayRules.shouldApplyRemoteSnapshot(false, false, 4, 5));
        assertTrue(SetPlayRelayRules.shouldApplyRemoteSnapshot(false, false, 6, 5));
        assertTrue(SetPlayRelayRules.shouldApplyRemoteSnapshot(false, false, 0, -1));
    }

    @Test
    void shouldAcceptLeaderRevision() {
        assertTrue(SetPlayRelayRules.shouldAcceptLeaderRevision(10, 10));
        assertTrue(SetPlayRelayRules.shouldAcceptLeaderRevision(11, 10));
        assertFalse(SetPlayRelayRules.shouldAcceptLeaderRevision(9, 10));
    }

    @Test
    void pinFollowsSelectedSessionNotConnectedCache() {
        assertEquals("111111", SetPlayRelayRules.pinForSelectedSession(
                "AAA1111", "BBB2222", "999999", "111111"));
        assertEquals("222222", SetPlayRelayRules.pinForSelectedSession(
                "BBB2222", "BBB2222", "222222", null));
        assertNull(SetPlayRelayRules.pinForSelectedSession(
                "AAA1111", "BBB2222", "999999", null));
        assertNull(SetPlayRelayRules.pinForSelectedSession("", "BBB2222", "999999", "111111"));
    }
}
