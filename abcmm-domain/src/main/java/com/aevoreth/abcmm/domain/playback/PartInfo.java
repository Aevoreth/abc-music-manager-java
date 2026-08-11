package com.aevoreth.abcmm.domain.playback;

/**
 * One ABC part (voice / track) as presented to ABC Music Manager UI and band tools.
 */
public final class PartInfo {

    private final int index;
    private final int number;
    private final String name;
    private final String instrument;

    public PartInfo(int index, int number, String name, String instrument) {
        if (index < 0) {
            throw new IllegalArgumentException("part index must be >= 0");
        }
        this.index = index;
        this.number = number;
        this.name = name == null ? "" : name;
        this.instrument = instrument == null ? "" : instrument;
    }

    public int index() {
        return index;
    }

    /** ABC X: part number shown before the name (Maestro / ABC Player style). */
    public int number() {
        return number;
    }

    public String name() {
        return name;
    }

    public String instrument() {
        return instrument;
    }
}
