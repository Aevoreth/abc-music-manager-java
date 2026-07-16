package com.aevoreth.abcmm.domain.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.library.LibraryFilter;

/**
 * Default library filter values persisted under {@code preferences.json} → {@code default_filters}.
 * Key names match the Python edition.
 */
public final class DefaultFilters {

    private String inSet; // null | "yes" | "no"
    private int ratingFrom = 0;
    private int ratingTo = 5;
    private boolean durationMinNone = true;
    private boolean durationMaxNone = true;
    private int durationMinSec = 0;
    private int durationMaxSec = 1200;
    private String lastPlayedMode = "time";
    private Integer lastPlayedFromSecondsAgo = 0;
    private Integer lastPlayedToSecondsAgo;
    private String lastPlayedFromIso;
    private String lastPlayedToIso;
    private int partsMin = 1;
    private int partsMax = 24;
    private List<Long> statusIds = List.of();

    public static DefaultFilters builtins() {
        return new DefaultFilters();
    }

    public DefaultFilters copy() {
        DefaultFilters copy = new DefaultFilters();
        copy.inSet = inSet;
        copy.ratingFrom = ratingFrom;
        copy.ratingTo = ratingTo;
        copy.durationMinNone = durationMinNone;
        copy.durationMaxNone = durationMaxNone;
        copy.durationMinSec = durationMinSec;
        copy.durationMaxSec = durationMaxSec;
        copy.lastPlayedMode = lastPlayedMode;
        copy.lastPlayedFromSecondsAgo = lastPlayedFromSecondsAgo;
        copy.lastPlayedToSecondsAgo = lastPlayedToSecondsAgo;
        copy.lastPlayedFromIso = lastPlayedFromIso;
        copy.lastPlayedToIso = lastPlayedToIso;
        copy.partsMin = partsMin;
        copy.partsMax = partsMax;
        copy.statusIds = List.copyOf(statusIds);
        return copy;
    }

    /**
     * Build a runtime {@link LibraryFilter} from these defaults (title search empty).
     */
    public LibraryFilter toLibraryFilter() {
        LibraryFilter filter = new LibraryFilter();
        if ("yes".equalsIgnoreCase(inSet)) {
            filter.setInSet(LibraryFilter.InSet.YES);
        } else if ("no".equalsIgnoreCase(inSet)) {
            filter.setInSet(LibraryFilter.InSet.NO);
        } else {
            filter.setInSet(LibraryFilter.InSet.EITHER);
        }
        filter.setRatingFrom(ratingFrom);
        filter.setRatingTo(ratingTo);
        filter.setDurationMinNone(durationMinNone);
        filter.setDurationMaxNone(durationMaxNone);
        filter.setDurationMinSec(durationMinSec);
        filter.setDurationMaxSec(durationMaxSec);
        if ("date".equalsIgnoreCase(lastPlayedMode)) {
            filter.setLastPlayedMode(LibraryFilter.LastPlayedMode.DATE);
        } else {
            filter.setLastPlayedMode(LibraryFilter.LastPlayedMode.TIME);
        }
        filter.setLastPlayedFromSecondsAgo(lastPlayedFromSecondsAgo);
        filter.setLastPlayedToSecondsAgo(lastPlayedToSecondsAgo);
        filter.setLastPlayedFromIso(lastPlayedFromIso);
        filter.setLastPlayedToIso(lastPlayedToIso);
        filter.setPartsMin(partsMin);
        filter.setPartsMax(partsMax);
        filter.setStatusIds(statusIds);
        filter.setTitleOrComposer("");
        return filter;
    }

    public String inSet() {
        return inSet;
    }

    public void setInSet(String inSet) {
        if (inSet == null || inSet.isBlank()) {
            this.inSet = null;
        } else {
            String normalized = inSet.trim().toLowerCase();
            this.inSet = ("yes".equals(normalized) || "no".equals(normalized)) ? normalized : null;
        }
    }

    public int ratingFrom() {
        return ratingFrom;
    }

    public void setRatingFrom(int ratingFrom) {
        this.ratingFrom = clamp(ratingFrom, 0, 5);
    }

    public int ratingTo() {
        return ratingTo;
    }

    public void setRatingTo(int ratingTo) {
        this.ratingTo = clamp(ratingTo, 0, 5);
    }

    public boolean durationMinNone() {
        return durationMinNone;
    }

    public void setDurationMinNone(boolean durationMinNone) {
        this.durationMinNone = durationMinNone;
    }

    public boolean durationMaxNone() {
        return durationMaxNone;
    }

    public void setDurationMaxNone(boolean durationMaxNone) {
        this.durationMaxNone = durationMaxNone;
    }

    public int durationMinSec() {
        return durationMinSec;
    }

    public void setDurationMinSec(int durationMinSec) {
        this.durationMinSec = Math.max(0, durationMinSec);
    }

    public int durationMaxSec() {
        return durationMaxSec;
    }

    public void setDurationMaxSec(int durationMaxSec) {
        this.durationMaxSec = Math.max(0, durationMaxSec);
    }

    public String lastPlayedMode() {
        return lastPlayedMode;
    }

    public void setLastPlayedMode(String lastPlayedMode) {
        this.lastPlayedMode = "date".equalsIgnoreCase(lastPlayedMode) ? "date" : "time";
    }

    public Integer lastPlayedFromSecondsAgo() {
        return lastPlayedFromSecondsAgo;
    }

    public void setLastPlayedFromSecondsAgo(Integer lastPlayedFromSecondsAgo) {
        this.lastPlayedFromSecondsAgo = lastPlayedFromSecondsAgo;
    }

    public Integer lastPlayedToSecondsAgo() {
        return lastPlayedToSecondsAgo;
    }

    public void setLastPlayedToSecondsAgo(Integer lastPlayedToSecondsAgo) {
        this.lastPlayedToSecondsAgo = lastPlayedToSecondsAgo;
    }

    public String lastPlayedFromIso() {
        return lastPlayedFromIso;
    }

    public void setLastPlayedFromIso(String lastPlayedFromIso) {
        this.lastPlayedFromIso = blankToNull(lastPlayedFromIso);
    }

    public String lastPlayedToIso() {
        return lastPlayedToIso;
    }

    public void setLastPlayedToIso(String lastPlayedToIso) {
        this.lastPlayedToIso = blankToNull(lastPlayedToIso);
    }

    public int partsMin() {
        return partsMin;
    }

    public void setPartsMin(int partsMin) {
        this.partsMin = clamp(partsMin, 1, 24);
    }

    public int partsMax() {
        return partsMax;
    }

    public void setPartsMax(int partsMax) {
        this.partsMax = clamp(partsMax, 1, 24);
    }

    public List<Long> statusIds() {
        return statusIds;
    }

    public void setStatusIds(List<Long> statusIds) {
        this.statusIds = statusIds == null || statusIds.isEmpty()
                ? List.of()
                : List.copyOf(new ArrayList<>(statusIds));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultFilters that)) {
            return false;
        }
        return ratingFrom == that.ratingFrom
                && ratingTo == that.ratingTo
                && durationMinNone == that.durationMinNone
                && durationMaxNone == that.durationMaxNone
                && durationMinSec == that.durationMinSec
                && durationMaxSec == that.durationMaxSec
                && partsMin == that.partsMin
                && partsMax == that.partsMax
                && Objects.equals(inSet, that.inSet)
                && Objects.equals(lastPlayedMode, that.lastPlayedMode)
                && Objects.equals(lastPlayedFromSecondsAgo, that.lastPlayedFromSecondsAgo)
                && Objects.equals(lastPlayedToSecondsAgo, that.lastPlayedToSecondsAgo)
                && Objects.equals(lastPlayedFromIso, that.lastPlayedFromIso)
                && Objects.equals(lastPlayedToIso, that.lastPlayedToIso)
                && Objects.equals(statusIds, that.statusIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                inSet, ratingFrom, ratingTo, durationMinNone, durationMaxNone,
                durationMinSec, durationMaxSec, lastPlayedMode, lastPlayedFromSecondsAgo,
                lastPlayedToSecondsAgo, lastPlayedFromIso, lastPlayedToIso, partsMin, partsMax, statusIds);
    }
}
