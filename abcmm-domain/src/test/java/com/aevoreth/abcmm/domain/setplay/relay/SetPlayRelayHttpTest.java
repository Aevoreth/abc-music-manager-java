package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class SetPlayRelayHttpTest {

    @Test
    void maxZipIsTwoMegabytes() {
        assertEquals(2 * 1024 * 1024, SetPlayRelayHttp.MAX_ZIP_BYTES);
    }

    @Test
    void uploadZipRejectsOverLimitBeforeHttp() {
        byte[] tooBig = new byte[SetPlayRelayHttp.MAX_ZIP_BYTES + 1];
        SetPlayRelayHttp http = new SetPlayRelayHttp();
        IOException ex = assertThrows(
                IOException.class,
                () -> http.uploadZip("https://example.invalid", "token", "AB12CD3", tooBig, null));
        assertTrueContains2Mb(ex.getMessage());
    }

    private static void assertTrueContains2Mb(String message) {
        if (message == null || !message.toLowerCase().contains("2 mb")) {
            throw new AssertionError("Expected 2 MB rejection, got: " + message);
        }
    }
}
