package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compact JSON for the rules stored with an optimization history row.
 * The journal keeps the text and does not parse it.
 */
public final class OptimizationRulesJson {

    private OptimizationRulesJson() {
    }

    public static String toJson(OptimizationRules rules) {
        Objects.requireNonNull(rules, "rules");
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        number(json, "smoothWeight", rules.smoothWeight(), true);
        number(json, "totalWeight", rules.totalWeight(), false);
        number(json, "roleWeight", rules.roleWeight(), false);
        number(json, "keepWeight", rules.keepWeight(), false);
        integer(json, "targetChanges", rules.targetChanges());
        number(json, "defaultSwitchMultiplier", rules.defaultSwitchMultiplier(), false);
        json.append(",\"playerSwitchMultiplier\":{");
        appendWeightMap(json, rules.playerSwitchMultiplier());
        json.append("},\"instrumentAffinities\":[");
        List<InstrumentAffinity> affinities = rules.instrumentAffinities();
        for (int i = 0; i < affinities.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            InstrumentAffinity affinity = affinities.get(i);
            json.append("{\"playerId\":").append(affinity.playerId());
            json.append(",\"instrumentId\":").append(affinity.instrumentId());
            json.append(",\"missPenalty\":").append(Double.toString(affinity.missPenalty()));
            json.append(",\"required\":").append(affinity.required());
            json.append('}');
        }
        json.append("],\"partPreferences\":[");
        List<PartPreference> preferences = rules.partPreferences();
        for (int i = 0; i < preferences.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            PartPreference preference = preferences.get(i);
            json.append("{\"setlistItemId\":").append(preference.setlistItemId());
            json.append(",\"partNumber\":").append(preference.partNumber());
            json.append(",\"playerId\":").append(preference.playerId());
            json.append(",\"penalty\":").append(Double.toString(preference.penalty()));
            json.append('}');
        }
        json.append("],\"itemKeepMultiplier\":{");
        appendWeightMap(json, rules.itemKeepMultiplier());
        json.append("},\"partKeepMultiplier\":[");
        List<Map.Entry<OccurrencePart, Double>> partKeep = new ArrayList<>(rules.partKeepMultiplier().entrySet());
        partKeep.sort(Comparator
                .comparingLong((Map.Entry<OccurrencePart, Double> entry) -> entry.getKey().setlistItemId())
                .thenComparingInt(entry -> entry.getKey().partNumber()));
        for (int i = 0; i < partKeep.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            OccurrencePart part = partKeep.get(i).getKey();
            json.append("{\"setlistItemId\":").append(part.setlistItemId());
            json.append(",\"partNumber\":").append(part.partNumber());
            json.append(",\"multiplier\":").append(Double.toString(partKeep.get(i).getValue()));
            json.append('}');
        }
        json.append("],\"instrumentPlacements\":[");
        for (int i = 0; i < rules.instrumentPlacements().size(); i++) {
            if (i > 0) json.append(',');
            var placement = rules.instrumentPlacements().get(i);
            json.append("{\"instrumentId\":").append(placement.instrumentId());
            json.append(",\"avoidedPlayerIds\":").append(placement.avoidedPlayerIds());
            json.append(",\"preferredPlayerIds\":").append(placement.preferredPlayerIds());
            json.append(",\"missPenalty\":").append(placement.missPenalty()).append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private static void appendWeightMap(StringBuilder json, Map<Long, Double> weights) {
        List<Long> ids = new ArrayList<>(weights.keySet());
        ids.sort(Comparator.naturalOrder());
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Long id = ids.get(i);
            json.append('"').append(id).append("\":").append(Double.toString(weights.get(id)));
        }
    }

    private static void number(StringBuilder json, String name, double value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":").append(Double.toString(value));
    }

    private static void integer(StringBuilder json, String name, int value) {
        json.append(",\"").append(name).append("\":").append(value);
    }
}
