package com.rickd.mushclient.service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampsTest {

    @Test fun prefixIsBracketedTwentyFourHourTimeWithTrailingSpace() {
        assertEquals("[09:05:03] ", Timestamps.prefix(LocalTime.of(9, 5, 3)))
        assertEquals("[23:59:59] ", Timestamps.prefix(LocalTime.of(23, 59, 59)))
    }

    @Test fun applyPrependsPrefixAndKeepsLineText() {
        val line = buildAnnotatedString { append("You say, \"hi\"") }
        val out = Timestamps.apply(line, LocalTime.of(1, 2, 3))
        assertEquals("[01:02:03] You say, \"hi\"", out.text)
    }

    @Test fun applyKeepsTheLinesOwnAnsiStylingShiftedPastThePrefix() {
        val red = Color(0xFFFF0000)
        val line = buildAnnotatedString { withStyle(SpanStyle(color = red)) { append("alert") } }
        val prefixLen = Timestamps.prefix(LocalTime.NOON).length
        val out = Timestamps.apply(line, LocalTime.NOON)
        assertTrue(out.spanStyles.any { it.item.color == red && it.start == prefixLen && it.end == prefixLen + 5 })
    }

    @Test fun emptyLineStillGetsAPrefix() {
        assertEquals("[12:00:00] ", Timestamps.apply(buildAnnotatedString { }, LocalTime.NOON).text)
    }

    @Test fun markerStatesOnOffAndCarriesTheFullDate() {
        val now = LocalDateTime.of(2026, 10, 17, 10, 42, 7)
        assertEquals("--- Timestamps ON: Sat, Oct 17 2026 10:42:07 ---", Timestamps.marker(true, now))
        assertEquals("--- Timestamps OFF: Sat, Oct 17 2026 10:42:07 ---", Timestamps.marker(false, now))
    }
}
