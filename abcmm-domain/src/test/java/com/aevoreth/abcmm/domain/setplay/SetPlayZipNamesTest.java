package com.aevoreth.abcmm.domain.setplay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SetPlayZipNamesTest {

    @Test
    void prefersSetName() {
        assertEquals(
                "RAVE-26-08-14 Testing.zip",
                SetPlayZipNames.downloadFileName("RAVE-26-08-14 Testing", "12AB3CD"));
    }

    @Test
    void fallsBackToCodeWhenNameBlank() {
        assertEquals("12AB3CD.zip", SetPlayZipNames.downloadFileName("  ", "12AB3CD"));
    }

    @Test
    void stripsIllegalFilenameCharacters() {
        assertEquals(
                "Set Name.zip",
                SetPlayZipNames.downloadFileName("Set:Name/<>", "CODE"));
    }
}
