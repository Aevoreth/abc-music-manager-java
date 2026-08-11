package com.aevoreth.abcmm.domain.export;

/**
 * Thrown when an ABCP playlist cannot be read or is invalid.
 */
public final class AbcpException extends Exception {

    public AbcpException(String message) {
        super(message);
    }

    public AbcpException(String message, Throwable cause) {
        super(message, cause);
    }
}
