package com.aevoreth.abcmm.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Per-song setlist warnings matching Python {@code SetlistsView._song_warning_message}.
 */
final class SetlistSongWarningChecker {

    static final String MSG_NO_SONG_LAYOUT =
            "No song layout is linked for this setlist's band. Link or create a layout in the assignment panel.";
    static final String MSG_INSTRUMENT =
            "A player is assigned a part that requires an instrument they do not have "
                    + "(including instruments matched by the same name).";
    static final String MSG_UNASSIGNED_PARTS =
            "One or more parts in this song are not assigned to any player in the band layout.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private SetlistSongWarningChecker() {
    }

    /**
     * @param setlistOverrides player → part overrides present for this setlist item
     *                         (value may be {@code null} for an explicit clear)
     * @return tooltip text when a warning applies; otherwise {@code null}
     */
    static String warningMessage(
            Long bandLayoutId,
            Long songLayoutId,
            String partsJson,
            List<BandLayoutSlotInfo> slots,
            Map<Long, Integer> songLayoutAssigns,
            Map<Long, Integer> setlistOverrides,
            Map<Long, Set<Long>> ownedInstrumentsByPlayer,
            Map<Long, Set<Long>> equivalentInstrumentIds) {
        if (bandLayoutId == null) {
            return null;
        }
        if (songLayoutId == null) {
            return MSG_NO_SONG_LAYOUT;
        }

        List<PartMeta> parts = parseParts(partsJson);
        Map<Integer, PartMeta> partsByNum = new HashMap<>();
        for (PartMeta part : parts) {
            partsByNum.put(part.partNumber(), part);
        }

        Map<Long, Integer> layout = songLayoutAssigns == null ? Map.of() : songLayoutAssigns;
        Map<Long, Integer> overrides = setlistOverrides == null ? Map.of() : setlistOverrides;
        List<BandLayoutSlotInfo> layoutSlots = slots == null ? List.of() : slots;

        Set<Integer> assignedParts = new HashSet<>();
        for (BandLayoutSlotInfo slot : layoutSlots) {
            long playerId = slot.playerId();
            Integer partNumber = overrides.containsKey(playerId)
                    ? overrides.get(playerId)
                    : layout.get(playerId);
            if (partNumber == null) {
                continue;
            }
            assignedParts.add(partNumber);
            PartMeta meta = partsByNum.get(partNumber);
            if (meta == null || meta.instrumentId() == null) {
                continue;
            }
            long instrumentId = meta.instrumentId();
            Set<Long> equiv = equivalentInstrumentIds == null
                    ? Set.of(instrumentId)
                    : equivalentInstrumentIds.getOrDefault(instrumentId, Set.of(instrumentId));
            Set<Long> owned = ownedInstrumentsByPlayer == null
                    ? Set.of()
                    : ownedInstrumentsByPlayer.getOrDefault(playerId, Set.of());
            boolean hasInstrument = false;
            for (Long id : equiv) {
                if (owned.contains(id)) {
                    hasInstrument = true;
                    break;
                }
            }
            if (!hasInstrument) {
                return MSG_INSTRUMENT;
            }
        }

        for (PartMeta part : parts) {
            if (!assignedParts.contains(part.partNumber())) {
                return MSG_UNASSIGNED_PARTS;
            }
        }
        return null;
    }

    static Map<Long, Set<Long>> buildEquivalentInstrumentIds(Map<Long, String> names) {
        Map<String, Set<Long>> byName = new HashMap<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            String key = normalizeName(entry.getValue());
            byName.computeIfAbsent(key, k -> new HashSet<>()).add(entry.getKey());
            if (key.contains("traveler") || key.contains("traveller")) {
                String alt = key.contains("traveler")
                        ? key.replace("traveler", "traveller")
                        : key.replace("traveller", "traveler");
                byName.computeIfAbsent(alt, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            Set<Long> equiv = new HashSet<>();
            String key = normalizeName(entry.getValue());
            if (byName.containsKey(key)) {
                equiv.addAll(byName.get(key));
            }
            if (key.contains("traveler") || key.contains("traveller")) {
                String alt = key.contains("traveler")
                        ? key.replace("traveler", "traveller")
                        : key.replace("traveller", "traveler");
                if (byName.containsKey(alt)) {
                    equiv.addAll(byName.get(alt));
                }
            }
            equiv.add(entry.getKey());
            result.put(entry.getKey(), equiv);
        }
        return result;
    }

    static List<PartMeta> parseParts(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(partsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<PartMeta> parts = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) {
                    continue;
                }
                int pn = node.path("part_number").asInt(0);
                if (pn <= 0) {
                    continue;
                }
                String name = node.path("part_name").asText("");
                Long iid = null;
                JsonNode idNode = node.get("instrument_id");
                if (idNode != null && !idNode.isNull() && idNode.isNumber()) {
                    iid = idNode.asLong();
                }
                parts.add(new PartMeta(pn, name == null ? "" : name.strip(), iid));
            }
            return List.copyOf(parts);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    record PartMeta(int partNumber, String partName, Long instrumentId) {
    }
}
