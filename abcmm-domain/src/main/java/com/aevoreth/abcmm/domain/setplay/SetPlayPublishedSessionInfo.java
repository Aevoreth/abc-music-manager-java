package com.aevoreth.abcmm.domain.setplay;

/**
 * Local copy of a published relay session (code + member PIN).
 */
public record SetPlayPublishedSessionInfo(
        long id,
        long relayId,
        String code,
        String name,
        String passphrase,
        Long setlistId) {
}
