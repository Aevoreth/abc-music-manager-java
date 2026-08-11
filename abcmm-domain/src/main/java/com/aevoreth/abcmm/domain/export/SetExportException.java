package com.aevoreth.abcmm.domain.export;

/**
 * User-facing export failure (empty setlist, target exists, I/O, etc.).
 */
public final class SetExportException extends Exception {

    public SetExportException(String message) {
        super(message);
    }

    public SetExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
