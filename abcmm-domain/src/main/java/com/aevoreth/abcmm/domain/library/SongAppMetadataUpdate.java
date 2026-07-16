package com.aevoreth.abcmm.domain.library;

/**
 * Partial update for app-managed {@code Song} fields (Python {@code update_song_app_metadata}).
 * Only fields marked for update are written.
 */
public final class SongAppMetadataUpdate {

    private boolean updateRating;
    private Integer rating;
    private boolean updateStatusId;
    private Long statusId;
    private boolean updateNotes;
    private String notes;
    private boolean updateLyrics;
    private String lyrics;

    public static SongAppMetadataUpdate ratingOnly(int rating) {
        return new SongAppMetadataUpdate().rating(rating);
    }

    public static SongAppMetadataUpdate statusOnly(long statusId) {
        return new SongAppMetadataUpdate().statusId(statusId);
    }

    public static SongAppMetadataUpdate full(Integer rating, Long statusId, String notes, String lyrics) {
        SongAppMetadataUpdate update = new SongAppMetadataUpdate();
        update.updateRating = true;
        update.rating = rating;
        update.updateStatusId = true;
        update.statusId = statusId;
        update.updateNotes = true;
        update.notes = notes == null ? "" : notes;
        update.updateLyrics = true;
        update.lyrics = lyrics == null ? "" : lyrics;
        return update;
    }

    public SongAppMetadataUpdate rating(Integer rating) {
        this.updateRating = true;
        this.rating = rating;
        return this;
    }

    public SongAppMetadataUpdate statusId(Long statusId) {
        this.updateStatusId = true;
        this.statusId = statusId;
        return this;
    }

    public SongAppMetadataUpdate notes(String notes) {
        this.updateNotes = true;
        this.notes = notes == null ? "" : notes;
        return this;
    }

    public SongAppMetadataUpdate lyrics(String lyrics) {
        this.updateLyrics = true;
        this.lyrics = lyrics == null ? "" : lyrics;
        return this;
    }

    public boolean updateRating() {
        return updateRating;
    }

    public Integer rating() {
        return rating;
    }

    public boolean updateStatusId() {
        return updateStatusId;
    }

    public Long statusId() {
        return statusId;
    }

    public boolean updateNotes() {
        return updateNotes;
    }

    public String notes() {
        return notes;
    }

    public boolean updateLyrics() {
        return updateLyrics;
    }

    public String lyrics() {
        return lyrics;
    }

    public boolean isEmpty() {
        return !updateRating && !updateStatusId && !updateNotes && !updateLyrics;
    }
}
