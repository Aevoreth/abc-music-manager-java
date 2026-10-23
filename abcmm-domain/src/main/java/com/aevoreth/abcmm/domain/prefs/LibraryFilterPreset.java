package com.aevoreth.abcmm.domain.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A named library filter preset. {@link #DEFAULT_NAME} is stored separately as
 * {@code default_filters}; this type is only for additional presets.
 */
public final class LibraryFilterPreset {

    public static final String DEFAULT_NAME = "Default";
    public static final String NONE_NAME = "None";
    public static final String MODIFIED_LABEL = "<- Modified ->";

    private final String name;
    private final DefaultFilters filters;

    public LibraryFilterPreset(String name, DefaultFilters filters) {
        this.name = name == null ? "" : name.trim();
        this.filters = filters == null ? DefaultFilters.builtins() : filters.copy();
    }

    public String name() {
        return name;
    }

    public DefaultFilters filters() {
        return filters;
    }

    public LibraryFilterPreset withFilters(DefaultFilters filters) {
        return new LibraryFilterPreset(name, filters);
    }

    public LibraryFilterPreset withName(String name) {
        return new LibraryFilterPreset(name, filters);
    }

    public static boolean isReservedName(String name) {
        String normalized = normalize(name);
        return normalized.equalsIgnoreCase(DEFAULT_NAME)
                || normalized.equalsIgnoreCase(NONE_NAME)
                || normalized.equalsIgnoreCase(MODIFIED_LABEL);
    }

    /**
     * Drop blank names, reserved names, and case-insensitive duplicates (first wins).
     */
    public static List<LibraryFilterPreset> sanitize(List<LibraryFilterPreset> presets) {
        if (presets == null || presets.isEmpty()) {
            return List.of();
        }
        List<LibraryFilterPreset> unique = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (LibraryFilterPreset preset : presets) {
            if (preset == null) {
                continue;
            }
            String name = normalize(preset.name());
            if (name.isEmpty() || isReservedName(name) || seen.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            seen.add(name.toLowerCase(Locale.ROOT));
            unique.add(new LibraryFilterPreset(name, preset.filters()));
        }
        return List.copyOf(unique);
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LibraryFilterPreset that)) {
            return false;
        }
        return name.equals(that.name) && filters.equals(that.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, filters);
    }
}
