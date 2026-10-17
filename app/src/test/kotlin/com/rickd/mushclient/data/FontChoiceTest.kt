package com.rickd.mushclient.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontChoiceTest {

    @Test fun storedNamesRoundTrip() {
        FontChoice.entries.forEach {
            assertEquals(it, FontChoice.fromName(it.name, FontChoice.MONOSPACE))
        }
    }

    @Test fun unknownOrMissingNameFallsBackToTheGivenDefault() {
        assertEquals(FontChoice.SERIF, FontChoice.fromName("COMIC_SANS", FontChoice.SERIF))
        assertEquals(FontChoice.MONOSPACE, FontChoice.fromName(null, FontChoice.MONOSPACE))
        assertEquals(FontChoice.SANS_SERIF, FontChoice.fromName("", FontChoice.SANS_SERIF))
    }

    @Test fun sizeIsClampedToTheSupportedRange() {
        assertEquals(MIN_FONT_SIZE_SP, clampFontSize(0))
        assertEquals(MIN_FONT_SIZE_SP, clampFontSize(-5))
        assertEquals(MAX_FONT_SIZE_SP, clampFontSize(500))
        assertEquals(20, clampFontSize(20))
    }

    @Test fun defaultsAreInsideTheRange() {
        assertTrue(DEFAULT_TERMINAL_FONT_SIZE_SP in MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP)
        assertTrue(DEFAULT_INPUT_FONT_SIZE_SP in MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP)
    }
}
