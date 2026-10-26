package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PhysicalInstrumentEquivalenceTest {

    @Test
    void travelerSpellingIsTheSameInstrumentAndFamilyIsNot() {
        Map<Long, String> names = Map.of(
                1L, "Traveler's Trusty Fiddle",
                2L, "Traveller's Trusty Fiddle",
                3L, "Basic Lute",
                4L, "Basic Harp");
        Map<Long, Set<Long>> equivalence = PhysicalInstrumentEquivalence.equivalentIds(names);

        assertTrue(equivalence.get(1L).contains(2L));
        assertTrue(equivalence.get(2L).contains(1L));
        assertFalse(equivalence.get(3L).contains(4L));
        assertTrue(PhysicalInstrumentEquivalence.owns(Set.of(1L), 2L, equivalence));
        assertFalse(PhysicalInstrumentEquivalence.owns(Set.of(3L), 4L, equivalence));
        assertTrue(PhysicalInstrumentEquivalence.namesEquivalent("Basic Harp", " basic harp "));
        assertEquals(3L, PhysicalInstrumentEquivalence.resolveInstrumentId(null, "Basic Lute", names));
        assertEquals(1L, PhysicalInstrumentEquivalence.resolveInstrumentId(
                null, "Traveller's Trusty Fiddle", names));
        assertEquals(9L, PhysicalInstrumentEquivalence.resolveInstrumentId(9L, "Basic Lute", names));
    }
}
