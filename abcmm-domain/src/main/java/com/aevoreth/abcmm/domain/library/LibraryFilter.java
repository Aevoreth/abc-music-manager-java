package com.aevoreth.abcmm.domain.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Filter criteria for {@link SongRepository#listLibrarySongs(LibraryFilter)}.
 * Mirrors Python {@code list_library_songs} parameters and {@code default_filters}.
 */
public final class LibraryFilter {

    public enum InSet {
        EITHER,
        YES,
        NO
    }

    public enum LastPlayedMode {
        TIME,
        DATE
    }

    private String titleOrComposer = "";
    private List<Long> statusIds = List.of();
    private List<String> transcribers = List.of();
    private InSet inSet = InSet.EITHER;
    private Integer ratingFrom;
    private Integer ratingTo;
    private Integer partsMin;
    private Integer partsMax;
    private boolean durationMinNone = true;
    private boolean durationMaxNone = true;
    private Integer durationMinSec;
    private Integer durationMaxSec;
    private LastPlayedMode lastPlayedMode = LastPlayedMode.TIME;
    private boolean lastPlayedNever;
    private Integer lastPlayedFromSecondsAgo;
    private Integer lastPlayedToSecondsAgo;
    private String lastPlayedFromIso;
    private String lastPlayedToIso;
    private int limit = 2000;

    public LibraryFilter() {
    }

    public LibraryFilter copy() {
        LibraryFilter copy = new LibraryFilter();
        copy.titleOrComposer = titleOrComposer;
        copy.statusIds = List.copyOf(statusIds);
        copy.transcribers = List.copyOf(transcribers);
        copy.inSet = inSet;
        copy.ratingFrom = ratingFrom;
        copy.ratingTo = ratingTo;
        copy.partsMin = partsMin;
        copy.partsMax = partsMax;
        copy.durationMinNone = durationMinNone;
        copy.durationMaxNone = durationMaxNone;
        copy.durationMinSec = durationMinSec;
        copy.durationMaxSec = durationMaxSec;
        copy.lastPlayedMode = lastPlayedMode;
        copy.lastPlayedNever = lastPlayedNever;
        copy.lastPlayedFromSecondsAgo = lastPlayedFromSecondsAgo;
        copy.lastPlayedToSecondsAgo = lastPlayedToSecondsAgo;
        copy.lastPlayedFromIso = lastPlayedFromIso;
        copy.lastPlayedToIso = lastPlayedToIso;
        copy.limit = limit;
        return copy;
    }

    /**
     * Unrestricted filters (Clear Filters). Title search cleared.
     */
    public static LibraryFilter cleared() {
        return new LibraryFilter();
    }

    public String titleOrComposer() {
        return titleOrComposer;
    }

    public void setTitleOrComposer(String titleOrComposer) {
        this.titleOrComposer = titleOrComposer == null ? "" : titleOrComposer;
    }

    public List<Long> statusIds() {
        return statusIds;
    }

    public void setStatusIds(List<Long> statusIds) {
        if (statusIds == null || statusIds.isEmpty()) {
            this.statusIds = List.of();
        } else {
            this.statusIds = List.copyOf(statusIds);
        }
    }

    public List<String> transcribers() {
        return transcribers;
    }

    public void setTranscribers(List<String> transcribers) {
        if (transcribers == null || transcribers.isEmpty()) {
            this.transcribers = List.of();
        } else {
            this.transcribers = List.copyOf(transcribers);
        }
    }

    public InSet inSet() {
        return inSet;
    }

    public void setInSet(InSet inSet) {
        this.inSet = Objects.requireNonNullElse(inSet, InSet.EITHER);
    }

    public Integer ratingFrom() {
        return ratingFrom;
    }

    public void setRatingFrom(Integer ratingFrom) {
        this.ratingFrom = ratingFrom;
    }

    public Integer ratingTo() {
        return ratingTo;
    }

    public void setRatingTo(Integer ratingTo) {
        this.ratingTo = ratingTo;
    }

    public Integer partsMin() {
        return partsMin;
    }

    public void setPartsMin(Integer partsMin) {
        this.partsMin = partsMin;
    }

    public Integer partsMax() {
        return partsMax;
    }

    public void setPartsMax(Integer partsMax) {
        this.partsMax = partsMax;
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

    public Integer durationMinSec() {
        return durationMinSec;
    }

    public void setDurationMinSec(Integer durationMinSec) {
        this.durationMinSec = durationMinSec;
    }

    public Integer durationMaxSec() {
        return durationMaxSec;
    }

    public void setDurationMaxSec(Integer durationMaxSec) {
        this.durationMaxSec = durationMaxSec;
    }

    public LastPlayedMode lastPlayedMode() {
        return lastPlayedMode;
    }

    public void setLastPlayedMode(LastPlayedMode lastPlayedMode) {
        this.lastPlayedMode = Objects.requireNonNullElse(lastPlayedMode, LastPlayedMode.TIME);
    }

    public boolean lastPlayedNever() {
        return lastPlayedNever;
    }

    public void setLastPlayedNever(boolean lastPlayedNever) {
        this.lastPlayedNever = lastPlayedNever;
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
        this.lastPlayedFromIso = lastPlayedFromIso;
    }

    public String lastPlayedToIso() {
        return lastPlayedToIso;
    }

    public void setLastPlayedToIso(String lastPlayedToIso) {
        this.lastPlayedToIso = lastPlayedToIso;
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * Effective rating lower bound for SQL (null = do not filter).
     * Full range 0–5 is treated as unrestricted.
     */
    public Integer effectiveRatingMin() {
        return ratingUnrestricted() ? null : ratingFrom;
    }

    public Integer effectiveRatingMax() {
        return ratingUnrestricted() ? null : ratingTo;
    }

    private boolean ratingUnrestricted() {
        return (ratingFrom == null || ratingFrom <= 0) && (ratingTo == null || ratingTo >= 5);
    }

    public Integer effectivePartsMin() {
        return partsUnrestricted() ? null : partsMin;
    }

    public Integer effectivePartsMax() {
        return partsUnrestricted() ? null : partsMax;
    }

    private boolean partsUnrestricted() {
        return (partsMin == null || partsMin <= 1) && (partsMax == null || partsMax >= 24);
    }

    public Integer effectiveDurationMinSec() {
        if (durationMinNone) {
            return null;
        }
        return durationMinSec;
    }

    public Integer effectiveDurationMaxSec() {
        if (durationMaxNone) {
            return null;
        }
        return durationMaxSec;
    }

    public List<String> titleTokens() {
        if (titleOrComposer == null || titleOrComposer.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : titleOrComposer.toLowerCase().split("\\s+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return Collections.unmodifiableList(tokens);
    }
}
