package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.aevoreth.abcmm.domain.library.LibraryFilter;

/**
 * Builds parameterized SQL for {@link com.aevoreth.abcmm.domain.library.SongRepository#listLibrarySongs}.
 */
final class LibraryFilterQuery {

    private final String sql;
    private final List<Object> args;

    private LibraryFilterQuery(String sql, List<Object> args) {
        this.sql = sql;
        this.args = List.copyOf(args);
    }

    static LibraryFilterQuery build(LibraryFilter filter) {
        String mainLibrary = """
                SELECT DISTINCT song_id FROM SongFile
                WHERE is_primary_library = 1 AND scan_excluded = 0
                """;
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        conditions.add("s.id IN (" + mainLibrary + ")");

        for (String token : filter.titleTokens()) {
            String sub = "%" + token + "%";
            conditions.add("(LOWER(s.title) LIKE ? OR LOWER(s.composers) LIKE ?)");
            args.add(sub);
            args.add(sub);
        }

        if (!filter.transcribers().isEmpty()) {
            String placeholders = placeholders(filter.transcribers().size());
            conditions.add("s.transcriber IN (" + placeholders + ")");
            args.addAll(filter.transcribers());
        }

        Integer durationMin = filter.effectiveDurationMinSec();
        if (durationMin != null) {
            conditions.add("s.duration_seconds >= ?");
            args.add(durationMin);
        }
        Integer durationMax = filter.effectiveDurationMaxSec();
        if (durationMax != null) {
            conditions.add("s.duration_seconds <= ?");
            args.add(durationMax);
        }

        Integer ratingMin = filter.effectiveRatingMin();
        if (ratingMin != null) {
            conditions.add("(s.rating IS NOT NULL AND s.rating >= ?)");
            args.add(ratingMin);
        }
        Integer ratingMax = filter.effectiveRatingMax();
        if (ratingMax != null) {
            conditions.add("(s.rating IS NOT NULL AND s.rating <= ?)");
            args.add(ratingMax);
        }

        if (!filter.statusIds().isEmpty()) {
            String placeholders = placeholders(filter.statusIds().size());
            conditions.add("s.status_id IN (" + placeholders + ")");
            args.addAll(filter.statusIds());
        }

        Integer partsMin = filter.effectivePartsMin();
        if (partsMin != null) {
            conditions.add("json_array_length(COALESCE(s.parts, '[]')) >= ?");
            args.add(partsMin);
        }
        Integer partsMax = filter.effectivePartsMax();
        if (partsMax != null) {
            conditions.add("json_array_length(COALESCE(s.parts, '[]')) <= ?");
            args.add(partsMax);
        }

        appendLastPlayed(filter, conditions, args);

        if (filter.inSet() == LibraryFilter.InSet.YES) {
            conditions.add("""
                    EXISTS (
                        SELECT 1 FROM SetlistItem si JOIN Setlist sl ON sl.id = si.setlist_id
                        WHERE si.song_id = s.id AND sl.locked = 0
                    )
                    """);
        } else if (filter.inSet() == LibraryFilter.InSet.NO) {
            conditions.add("""
                    NOT EXISTS (
                        SELECT 1 FROM SetlistItem si JOIN Setlist sl ON sl.id = si.setlist_id
                        WHERE si.song_id = s.id AND sl.locked = 0
                    )
                    """);
        }

        args.add(filter.limit());
        String sql = """
                SELECT s.id, s.title, s.composers, s.transcriber, s.duration_seconds,
                       json_array_length(COALESCE(s.parts, '[]')) AS part_count,
                       s.parts,
                       s.last_played_at, s.total_plays, s.rating, s.status_id,
                       st.name AS status_name, st.color AS status_color,
                       s.notes, s.lyrics,
                       EXISTS (
                           SELECT 1 FROM SetlistItem si JOIN Setlist sl ON sl.id = si.setlist_id
                           WHERE si.song_id = s.id AND sl.locked = 0
                       ) AS in_upcoming_set
                FROM Song s
                LEFT JOIN Status st ON st.id = s.status_id
                WHERE %s
                ORDER BY s.title
                LIMIT ?
                """.formatted(String.join(" AND ", conditions));
        return new LibraryFilterQuery(sql, args);
    }

    private static void appendLastPlayed(
            LibraryFilter filter, List<String> conditions, List<Object> args) {
        if (filter.lastPlayedNever()) {
            conditions.add("s.last_played_at IS NULL");
            return;
        }
        if (filter.lastPlayedMode() == LibraryFilter.LastPlayedMode.DATE) {
            if (filter.lastPlayedFromIso() == null && filter.lastPlayedToIso() == null) {
                return;
            }
            if (filter.lastPlayedFromIso() != null) {
                conditions.add("s.last_played_at >= ?");
                args.add(filter.lastPlayedFromIso());
            }
            if (filter.lastPlayedToIso() != null) {
                conditions.add("s.last_played_at <= ?");
                args.add(filter.lastPlayedToIso());
            }
            return;
        }

        Integer minAgo = filter.lastPlayedFromSecondsAgo();
        Integer maxAgo = filter.lastPlayedToSecondsAgo();
        // Builtin defaults: from=0, to=null → unrestricted
        if ((minAgo == null || minAgo == 0) && maxAgo == null) {
            return;
        }
        boolean includeNever = maxAgo == null || minAgo == null;
        if (maxAgo != null) {
            if (includeNever) {
                conditions.add(
                        "(datetime(s.last_played_at) >= datetime('now', ?) OR s.last_played_at IS NULL)");
            } else {
                conditions.add("datetime(s.last_played_at) >= datetime('now', ?)");
            }
            args.add("-" + maxAgo + " seconds");
        }
        if (minAgo != null) {
            if (includeNever) {
                conditions.add(
                        "(datetime(s.last_played_at) <= datetime('now', ?) OR s.last_played_at IS NULL)");
            } else {
                conditions.add("datetime(s.last_played_at) <= datetime('now', ?)");
            }
            args.add("-" + minAgo + " seconds");
        }
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('?');
        }
        return builder.toString();
    }

    String sql() {
        return sql;
    }

    void bind(PreparedStatement statement) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            statement.setObject(i + 1, args.get(i));
        }
    }
}
