package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class SetPlayPinHashTest {

    @Test
    void workerStoresSha256HexNotPlaintextPin() throws Exception {
        String pin = "012345";
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(pin.getBytes(StandardCharsets.UTF_8)));
        assertEquals(64, hash.length());
        assertFalse(hash.contains(pin));
        assertEquals("a4ac914c09d7c097fe1f4f8c5aa1f4568ab3cce0fbc432a39bfa64c2d2a2f3e3".length(), hash.length());
    }
}
