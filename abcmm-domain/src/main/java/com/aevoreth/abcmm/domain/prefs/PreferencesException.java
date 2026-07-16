package com.aevoreth.abcmm.domain.prefs;

/**
 * Failure reading or writing preferences.
 */
public class PreferencesException extends Exception {

    public PreferencesException(String message) {
        super(message);
    }

    public PreferencesException(String message, Throwable cause) {
        super(message, cause);
    }
}
