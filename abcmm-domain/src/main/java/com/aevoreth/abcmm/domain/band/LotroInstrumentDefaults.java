package com.aevoreth.abcmm.domain.band;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOTRO character classes and default instrument ownership / proficiency rules
 * derived from the Instruments / Skill proficiency table on lotro-wiki.
 *
 * <p>Bassoon / Lute / Fiddle / Harp Use cover every instrument in that family.
 * Basic Cowbell and Moor Cowbell each require their own Use skill.
 */
public final class LotroInstrumentDefaults {

    /** Purple bullet for festival / limited instruments. */
    public static final String FESTIVAL_PREFIX = "\u2022 ";

    /** Gold bullet for purchased coffer instruments (Jaunty Hand-Knells). */
    public static final String COFFER_PREFIX = "\u2022 ";

    public static final List<String> CHARACTER_CLASSES = List.of(
            "Beorning",
            "Brawler",
            "Burglar",
            "Captain",
            "Champion",
            "Guardian",
            "Hunter",
            "Lore-master",
            "Mariner",
            "Minstrel",
            "Rune-keeper",
            "Warden");

    private static final Set<String> FESTIVAL_INSTRUMENTS = Set.of(
            "bardic fiddle",
            "lonely mountain fiddle",
            "sprightly fiddle",
            "traveler's trusty fiddle",
            "traveller's trusty fiddle",
            "lonely mountain bassoon",
            "brusque bassoon");

    private static final Set<String> COFFER_INSTRUMENTS = Set.of(
            "jaunty hand-knells",
            "jaunty hand-knell");

    /**
     * UI-only spelling overrides. Keys are normalized (lowercase); values are display labels.
     * DB / schema names stay as stored (see docs/SCHEMA_ISSUES.md).
     */
    private static final Map<String, String> DISPLAY_SPELLINGS = Map.of(
            "traveler's trusty fiddle", "Traveller's Trusty Fiddle",
            "traveller's trusty fiddle", "Traveller's Trusty Fiddle");

    private enum UseSkill {
        LUTE,
        THEORBO,
        HARP,
        FLUTE,
        HORN,
        CLARINET,
        BAGPIPE,
        PIBGORN,
        DRUM,
        FIDDLE,
        BASSOON,
        BASIC_COWBELL,
        MOOR_COWBELL,
        HAND_KNELLS
    }

    private static final Map<String, Set<UseSkill>> CLASS_SKILLS = buildClassSkills();

    private LotroInstrumentDefaults() {
    }

    public static boolean isFestivalInstrument(String instrumentName) {
        return FESTIVAL_INSTRUMENTS.contains(normalize(instrumentName));
    }

    public static boolean isCofferInstrument(String instrumentName) {
        return COFFER_INSTRUMENTS.contains(normalize(instrumentName));
    }

    /**
     * Canonical UI label for an instrument name (corrects known schema misspellings for display).
     */
    public static String uiName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return "";
        }
        String display = DISPLAY_SPELLINGS.get(normalize(instrumentName));
        return display != null ? display : instrumentName;
    }

    /**
     * Display label with a purple festival or gold coffer bullet when applicable.
     */
    public static String displayName(String instrumentName) {
        String label = uiName(instrumentName);
        if (label.isEmpty()) {
            return "";
        }
        if (isFestivalInstrument(instrumentName)) {
            return FESTIVAL_PREFIX + label;
        }
        if (isCofferInstrument(instrumentName)) {
            return COFFER_PREFIX + label;
        }
        return label;
    }

    /**
     * Whether the given class is assumed to own / be proficient with the instrument
     * (festival instruments are never owned; coffer instruments are never owned or proficient).
     */
    public static boolean defaultHasInstrument(String characterClass, String instrumentName) {
        if (isFestivalInstrument(instrumentName) || isCofferInstrument(instrumentName)) {
            return false;
        }
        return classHasUseFor(characterClass, instrumentName);
    }

    public static boolean defaultHasProficiency(String characterClass, String instrumentName) {
        if (isCofferInstrument(instrumentName)) {
            return false;
        }
        return classHasUseFor(characterClass, instrumentName);
    }

    public static boolean classHasUseFor(String characterClass, String instrumentName) {
        Set<UseSkill> skills = CLASS_SKILLS.get(normalizeClass(characterClass));
        if (skills == null || skills.isEmpty()) {
            return false;
        }
        UseSkill required = useSkillForInstrument(instrumentName);
        return required != null && skills.contains(required);
    }

    static UseSkill useSkillForInstrument(String instrumentName) {
        String name = normalize(instrumentName);
        if (name.isEmpty()) {
            return null;
        }
        if (name.contains("hand-knell") || name.contains("hand knell")) {
            return UseSkill.HAND_KNELLS;
        }
        if (name.contains("moor cowbell")) {
            return UseSkill.MOOR_COWBELL;
        }
        if (name.contains("cowbell")) {
            return UseSkill.BASIC_COWBELL;
        }
        if (name.contains("bassoon")) {
            return UseSkill.BASSOON;
        }
        if (name.contains("fiddle")) {
            return UseSkill.FIDDLE;
        }
        if (name.contains("harp")) {
            return UseSkill.HARP;
        }
        if (name.contains("theorbo")) {
            return UseSkill.THEORBO;
        }
        // Check flute before lute — "flute" contains the substring "lute".
        if (name.contains("flute")) {
            return UseSkill.FLUTE;
        }
        if (name.contains("lute")) {
            return UseSkill.LUTE;
        }
        if (name.contains("horn")) {
            return UseSkill.HORN;
        }
        if (name.contains("clarinet")) {
            return UseSkill.CLARINET;
        }
        if (name.contains("bagpipe")) {
            return UseSkill.BAGPIPE;
        }
        if (name.contains("pibgorn")) {
            return UseSkill.PIBGORN;
        }
        if (name.contains("drum")) {
            return UseSkill.DRUM;
        }
        return null;
    }

    private static Map<String, Set<UseSkill>> buildClassSkills() {
        Map<String, Set<UseSkill>> map = new LinkedHashMap<>();
        Set<UseSkill> shared = Set.of(UseSkill.LUTE, UseSkill.BASSOON, UseSkill.FIDDLE);

        put(map, "Beorning", union(shared, UseSkill.CLARINET));
        put(map, "Brawler", union(shared, UseSkill.CLARINET));
        put(map, "Burglar", union(shared, UseSkill.CLARINET));
        put(map, "Captain", union(shared, UseSkill.HORN));
        put(map, "Champion", union(shared, UseSkill.HORN));
        put(map, "Guardian", union(shared, UseSkill.HORN));
        put(map, "Hunter", union(shared, UseSkill.CLARINET));
        put(map, "Lore-master", union(shared, UseSkill.CLARINET));
        put(map, "Mariner", union(shared, UseSkill.FLUTE, UseSkill.HORN));
        put(map, "Rune-keeper", union(shared, UseSkill.CLARINET));
        put(map, "Warden", union(shared, UseSkill.HORN));

        Set<UseSkill> minstrel = new LinkedHashSet<>();
        minstrel.addAll(List.of(
                UseSkill.LUTE,
                UseSkill.THEORBO,
                UseSkill.HARP,
                UseSkill.FLUTE,
                UseSkill.HORN,
                UseSkill.CLARINET,
                UseSkill.BAGPIPE,
                UseSkill.PIBGORN,
                UseSkill.DRUM,
                UseSkill.FIDDLE,
                UseSkill.BASSOON,
                UseSkill.BASIC_COWBELL,
                UseSkill.MOOR_COWBELL));
        // Hand-knells are coffer-only; never granted by class selection.
        put(map, "Minstrel", minstrel);
        return Map.copyOf(map);
    }

    private static void put(Map<String, Set<UseSkill>> map, String characterClass, Set<UseSkill> skills) {
        map.put(normalizeClass(characterClass), Set.copyOf(skills));
    }

    private static Set<UseSkill> union(Set<UseSkill> base, UseSkill... extra) {
        Set<UseSkill> out = new LinkedHashSet<>(base);
        out.addAll(List.of(extra));
        return out;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeClass(String characterClass) {
        return normalize(characterClass);
    }
}
