package com.aevoreth.abcmm.optimizer;

/**
 * Builds stable {@code key=value} diagnostic details.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static String detail(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("detail pairs");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(pairs[i]).append('=').append(pairs[i + 1]);
        }
        return text.toString();
    }
}
