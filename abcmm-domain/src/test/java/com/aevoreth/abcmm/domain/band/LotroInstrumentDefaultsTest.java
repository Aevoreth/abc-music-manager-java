package com.aevoreth.abcmm.domain.band;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LotroInstrumentDefaultsTest {

    @Test
    void minstrelOwnsBasicButNotFestivalOrCoffer() {
        assertTrue(LotroInstrumentDefaults.defaultHasInstrument("Minstrel", "Basic Lute"));
        assertTrue(LotroInstrumentDefaults.defaultHasProficiency("Minstrel", "Basic Lute"));
        assertTrue(LotroInstrumentDefaults.defaultHasProficiency("Minstrel", "Lute of Ages"));

        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Minstrel", "Lonely Mountain Fiddle"));
        assertTrue(LotroInstrumentDefaults.defaultHasProficiency("Minstrel", "Lonely Mountain Fiddle"));

        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Minstrel", "Jaunty Hand-Knells"));
        assertFalse(LotroInstrumentDefaults.defaultHasProficiency("Minstrel", "Jaunty Hand-Knells"));
    }

    @Test
    void championHasHornFamilyNotHarp() {
        assertTrue(LotroInstrumentDefaults.defaultHasInstrument("Champion", "Basic Horn"));
        assertTrue(LotroInstrumentDefaults.defaultHasInstrument("Champion", "Basic Lute"));
        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Champion", "Basic Harp"));
        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Champion", "Basic Flute"));
    }

    @Test
    void cowbellsAreExclusive() {
        assertTrue(LotroInstrumentDefaults.defaultHasInstrument("Minstrel", "Basic Cowbell"));
        assertTrue(LotroInstrumentDefaults.defaultHasInstrument("Minstrel", "Moor Cowbell"));
        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Hunter", "Basic Cowbell"));
        assertFalse(LotroInstrumentDefaults.defaultHasInstrument("Hunter", "Moor Cowbell"));
    }

    @Test
    void displayPrefixes() {
        assertTrue(LotroInstrumentDefaults.displayName("Bardic Fiddle")
                .startsWith(LotroInstrumentDefaults.FESTIVAL_PREFIX));
        assertTrue(LotroInstrumentDefaults.displayName("Jaunty Hand-Knells")
                .startsWith(LotroInstrumentDefaults.COFFER_PREFIX));
        assertEquals("Basic Flute", LotroInstrumentDefaults.displayName("Basic Flute"));
    }

    @Test
    void travelerFiddleDisplaysBritishSpelling() {
        assertEquals(
                "Traveller's Trusty Fiddle",
                LotroInstrumentDefaults.uiName("Traveler's Trusty Fiddle"));
        assertTrue(LotroInstrumentDefaults.displayName("Traveler's Trusty Fiddle")
                .endsWith("Traveller's Trusty Fiddle"));
        assertTrue(LotroInstrumentDefaults.displayName("Traveler's Trusty Fiddle")
                .startsWith(LotroInstrumentDefaults.FESTIVAL_PREFIX));
    }

    @Test
    void classesMatchWikiTableHeaders() {
        assertEquals(12, LotroInstrumentDefaults.CHARACTER_CLASSES.size());
        assertTrue(LotroInstrumentDefaults.CHARACTER_CLASSES.contains("Minstrel"));
        assertTrue(LotroInstrumentDefaults.CHARACTER_CLASSES.contains("Lore-master"));
    }
}
