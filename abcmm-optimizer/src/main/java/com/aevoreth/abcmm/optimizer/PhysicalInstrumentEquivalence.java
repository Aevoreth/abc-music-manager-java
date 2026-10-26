package com.aevoreth.abcmm.optimizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Physical-instrument identity. Same id, the same normalized name, or the
 * Traveler/Traveller spelling swap count as one instrument. Family and class
 * skill do not. This matches the name grouping in set-play layout comparison.
 */
public final class PhysicalInstrumentEquivalence {

    private PhysicalInstrumentEquivalence() {
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean namesEquivalent(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.equals(b)) {
            return true;
        }
        return travelerAlternate(a).equals(b) || travelerAlternate(b).equals(a);
    }

    public static boolean sameInstrument(long left, long right, Map<Long, Set<Long>> equivalence) {
        if (left == right) {
            return true;
        }
        Set<Long> group = equivalence.get(left);
        return group != null && group.contains(right);
    }

    public static boolean owns(Set<Long> ownedInstrumentIds, long requiredInstrumentId,
            Map<Long, Set<Long>> equivalence) {
        for (long owned : ownedInstrumentIds) {
            if (sameInstrument(owned, requiredInstrumentId, equivalence)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a missing instrument id from {@code madeFor}. Returns the lowest matching
     * catalog id, or null when nothing matches. A non-null {@code instrumentId} is kept
     * even when it is absent from the catalog.
     */
    public static Long resolveInstrumentId(
            Long instrumentId,
            String madeFor,
            Map<Long, String> instrumentNames) {
        if (instrumentId != null) {
            return instrumentId;
        }
        if (madeFor == null || madeFor.isBlank()) {
            return null;
        }
        Long best = null;
        for (Map.Entry<Long, String> entry : instrumentNames.entrySet()) {
            if (!namesEquivalent(madeFor, entry.getValue())) {
                continue;
            }
            if (best == null || entry.getKey() < best) {
                best = entry.getKey();
            }
        }
        return best;
    }

    public static Map<Long, Set<Long>> equivalentIds(Map<Long, String> instrumentNames) {
        Map<String, Set<Long>> byName = new HashMap<>();
        for (Map.Entry<Long, String> entry : instrumentNames.entrySet()) {
            String key = normalize(entry.getValue());
            byName.computeIfAbsent(key, ignored -> new HashSet<>()).add(entry.getKey());
            String alternate = travelerAlternate(key);
            if (!alternate.equals(key)) {
                byName.computeIfAbsent(alternate, ignored -> new HashSet<>()).add(entry.getKey());
            }
        }
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : instrumentNames.entrySet()) {
            Set<Long> group = new HashSet<>();
            String key = normalize(entry.getValue());
            Set<Long> direct = byName.get(key);
            if (direct != null) {
                group.addAll(direct);
            }
            String alternate = travelerAlternate(key);
            if (!alternate.equals(key)) {
                Set<Long> swapped = byName.get(alternate);
                if (swapped != null) {
                    group.addAll(swapped);
                }
            }
            group.add(entry.getKey());
            result.put(entry.getKey(), Set.copyOf(group));
        }
        return Map.copyOf(result);
    }

    private static String travelerAlternate(String normalizedName) {
        if (normalizedName.contains("traveler")) {
            return normalizedName.replace("traveler", "traveller");
        }
        if (normalizedName.contains("traveller")) {
            return normalizedName.replace("traveller", "traveler");
        }
        return normalizedName;
    }
}
