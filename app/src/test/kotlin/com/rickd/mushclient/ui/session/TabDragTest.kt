package com.rickd.mushclient.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TabDragTest {
    // Three tabs of differing widths laid end to end: [0,100) [100,300) [300,360)
    private val lefts = listOf(0f, 100f, 300f)
    private val widths = listOf(100f, 200f, 60f)

    @Test fun noDragStaysPut() {
        assertEquals(0, dropIndex(lefts, widths, 0, 0f))
        assertEquals(1, dropIndex(lefts, widths, 1, 0f))
        assertEquals(2, dropIndex(lefts, widths, 2, 0f))
    }

    @Test fun smallDragDoesNotMoveTheTab() {
        assertEquals(1, dropIndex(lefts, widths, 1, 40f))
        assertEquals(1, dropIndex(lefts, widths, 1, -40f))
    }

    @Test fun draggingFirstTabPastTheMiddleOfTheNextLandsThere() {
        // centre starts at 50; +120 -> 170, inside the second tab
        assertEquals(1, dropIndex(lefts, widths, 0, 120f))
        // +270 -> 320, inside the third
        assertEquals(2, dropIndex(lefts, widths, 0, 270f))
    }

    @Test fun draggingBackwardWorksToo() {
        // third tab centre 330; -150 -> 180, inside the second
        assertEquals(1, dropIndex(lefts, widths, 2, -150f))
        assertEquals(0, dropIndex(lefts, widths, 2, -300f))
    }

    @Test fun pastEitherEndClampsToTheEndTab() {
        assertEquals(2, dropIndex(lefts, widths, 0, 5000f))
        assertEquals(0, dropIndex(lefts, widths, 2, -5000f))
    }

    @Test fun degenerateInputsAreSafe() {
        assertEquals(0, dropIndex(emptyList(), emptyList(), 0, 10f))
        assertEquals(5, dropIndex(lefts, widths, 5, 10f))
    }
}
