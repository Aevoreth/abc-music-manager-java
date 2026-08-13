package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Ports Python {@code tests/test_set_play_share_url.py}. */
class SetPlayShareUrlsTest {

    @Test
    void buildPlaybackShareUrlFromWss() {
        String url = SetPlayShareUrls.buildPlaybackShareUrl(
                "wss://abc-set-play-relay.example.workers.dev",
                "12AB3CD");
        assertEquals("https://abc-set-play-relay.example.workers.dev/playback?set=12AB3CD", url);
    }

    @Test
    void buildPlaybackShareUrlNormalizesCodeCase() {
        String url = SetPlayShareUrls.buildPlaybackShareUrl("https://relay.example.com/", "ab12cd3");
        assertEquals("https://relay.example.com/playback?set=AB12CD3", url);
    }

    @Test
    void relayWsOrigin() {
        assertEquals("wss://host.example", SetPlayShareUrls.relayWsOrigin("https://host.example"));
        assertEquals("wss://host.example", SetPlayShareUrls.relayWsOrigin("wss://host.example"));
    }

    @Test
    void parseShareLink() {
        Optional<SetPlayShareUrls.ParsedShareLink> parsed = SetPlayShareUrls.parseShareOrCode(
                "https://abc-set-play-relay.example.workers.dev/playback?set=12AB3CD",
                null);
        assertTrue(parsed.isPresent());
        assertEquals("12AB3CD", parsed.get().roomCode());
        assertEquals("wss://abc-set-play-relay.example.workers.dev", parsed.get().relayWsUrl());
    }

    @Test
    void parseWssShareLink() {
        Optional<SetPlayShareUrls.ParsedShareLink> parsed = SetPlayShareUrls.parseShareOrCode(
                "wss://relay.example.com/playback?set=ZZZZZZZ",
                null);
        assertTrue(parsed.isPresent());
        assertEquals("ZZZZZZZ", parsed.get().roomCode());
        assertEquals("wss://relay.example.com", parsed.get().relayWsUrl());
    }

    @Test
    void parseApiRoomsPath() {
        Optional<SetPlayShareUrls.ParsedShareLink> parsed = SetPlayShareUrls.parseShareOrCode(
                "https://relay.example.com/api/rooms/HELLO12/ws",
                null);
        assertTrue(parsed.isPresent());
        assertEquals("HELLO12", parsed.get().roomCode());
        assertEquals("wss://relay.example.com", parsed.get().relayWsUrl());
    }

    @Test
    void parseBareCodeWithFallbackRelay() {
        Optional<SetPlayShareUrls.ParsedShareLink> parsed = SetPlayShareUrls.parseShareOrCode(
                "12AB3CD",
                "wss://relay.example.com");
        assertTrue(parsed.isPresent());
        assertEquals("12AB3CD", parsed.get().roomCode());
        assertEquals("wss://relay.example.com", parsed.get().relayWsUrl());
    }

    @Test
    void parseBareCodeWithoutRelayFails() {
        assertFalse(SetPlayShareUrls.parseShareOrCode("12AB3CD", null).isPresent());
    }

    @Test
    void parseShortCodeFails() {
        assertFalse(SetPlayShareUrls.parseShareOrCode("AB", "wss://x.example").isPresent());
        assertFalse(SetPlayShareUrls.parseShareOrCode("https://x.example/playback?set=AB", null).isPresent());
    }

    @Test
    void parseEmpty() {
        assertFalse(SetPlayShareUrls.parseShareOrCode("", null).isPresent());
        assertFalse(SetPlayShareUrls.parseShareOrCode("   ", null).isPresent());
    }

    @Test
    void normalizeRelayBaseUrlStripsApiRooms() {
        assertEquals(
                "wss://relay.example.com",
                SetPlayShareUrls.normalizeRelayBaseUrl("wss://relay.example.com/api/rooms"));
    }

    @Test
    void joinWsUrlHasNoQueryToken() {
        String url = SetPlayRelayClient.joinWsUrl(
                "wss://relay.example.com",
                "ab12cd3");
        assertEquals("wss://relay.example.com/api/rooms/AB12CD3/ws", url);
    }

    @Test
    void downloadShareUrlAddsFragment() {
        String url = SetPlayShareUrls.buildDownloadShareUrl(
                "https://relay.example.com", "12AB3CD", "012345");
        assertEquals("https://relay.example.com/playback?set=12AB3CD#p=012345", url);
    }

    @Test
    void parseDownloadShareUrlReadsPin() {
        Optional<SetPlayShareUrls.ParsedShareLink> parsed = SetPlayShareUrls.parseShareOrCode(
                "https://relay.example.com/playback?set=12AB3CD#p=012345",
                null);
        assertTrue(parsed.isPresent());
        assertEquals("12AB3CD", parsed.get().roomCode());
        assertEquals("012345", parsed.get().passphrase());
    }
}
