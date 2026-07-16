package com.aevoreth.abcmm.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Last-played time-range combo options matching Python {@code LAST_PLAYED_TIME_OPTS}.
 * {@code secondsAgo == null} means "Never".
 */
final class LastPlayedTimeOptions {

    record Option(String label, Integer secondsAgo) {
    }

    private static final List<Option> OPTIONS = buildOptions();

    private LastPlayedTimeOptions() {
    }

    static List<Option> all() {
        return OPTIONS;
    }

    static int indexForSecondsAgo(Integer secondsAgo) {
        if (secondsAgo == null) {
            return OPTIONS.size() - 1;
        }
        for (int i = 0; i < OPTIONS.size(); i++) {
            Integer value = OPTIONS.get(i).secondsAgo();
            if (value != null && value.equals(secondsAgo)) {
                return i;
            }
        }
        return 0;
    }

    static Integer secondsAt(int index) {
        if (index < 0 || index >= OPTIONS.size()) {
            return 0;
        }
        return OPTIONS.get(index).secondsAgo();
    }

    private static List<Option> buildOptions() {
        List<Option> opts = new ArrayList<>();
        opts.add(new Option("Just now", 0));
        for (int h = 1; h < 24; h++) {
            opts.add(new Option(h + " hour(s)", h * 3600));
        }
        for (int d = 1; d < 14; d++) {
            opts.add(new Option(d + " day(s)", d * 86400));
        }
        for (int w = 2; w < 8; w++) {
            opts.add(new Option(w + " week(s)", w * 604800));
        }
        for (int m = 2; m < 24; m++) {
            opts.add(new Option(m + " month(s)", m * 30 * 86400));
        }
        for (int y = 1; y < 11; y++) {
            opts.add(new Option(y + " year(s)", y * 365 * 86400));
        }
        opts.add(new Option("Never", null));
        return List.copyOf(opts);
    }
}
