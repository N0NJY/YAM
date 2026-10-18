package com.rickd.mushclient.service

import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.ui.session.TabMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStateTest {

    private fun tab(id: Long) = TabMeta(id, "World $id", ConnectionState.CONNECTED, hasError = false)
    private fun tabs() = listOf(tab(1), tab(2), tab(3))

    @Test fun activityInABackgroundTabMarksOnlyThatTab() {
        val out = tabs().withActivity(id = 2, activeId = 1)
        assertEquals(listOf(false, true, false), out.map { it.hasUnseenActivity })
    }

    @Test fun activityInTheTabInViewIsNeverMarked() {
        val before = tabs()
        assertSame(before, before.withActivity(id = 1, activeId = 1))
    }

    @Test fun activityWhenNoTabIsActiveStillMarks() {
        assertTrue(tabs().withActivity(id = 3, activeId = null)[2].hasUnseenActivity)
    }

    @Test fun switchingToAFlashingTabClearsItAndLeavesOthersFlashing() {
        val flashing = tabs().withActivity(2, 1).withActivity(3, 1)
        val out = flashing.withActivityCleared(2)
        assertEquals(listOf(false, false, true), out.map { it.hasUnseenActivity })
    }

    @Test fun clearingAnAlreadyQuietTabChangesNothing() {
        assertEquals(tabs(), tabs().withActivityCleared(1))
    }

    @Test fun trackingIsByIdNotPosition() {
        // Tab 1 closed: ids 2 and 3 now sit at positions 0 and 1.
        val remaining = tabs().drop(1).withActivity(id = 3, activeId = 2)
        assertEquals(listOf(2L, 3L), remaining.map { it.id })
        assertEquals(listOf(false, true), remaining.map { it.hasUnseenActivity })
    }

    @Test fun characterNameIsSetOnTheRightTabOnly() {
        val out = tabs().withCharacter(2, "Estrellita")
        assertNull(out[0].characterName)
        assertEquals("Estrellita", out[1].characterName)
        assertNull(out[2].characterName)
    }

    @Test fun characterNameSurvivesLaterActivityChanges() {
        val out = tabs().withCharacter(2, "Thoran").withActivity(2, 1).withActivityCleared(2)
        assertEquals("Thoran", out[1].characterName)
        assertFalse(out[1].hasUnseenActivity)
    }
}

class TabMoveTest {
    private fun tab(id: Long) = TabMeta(id, "W$id", ConnectionState.CONNECTED, hasError = false)
    private fun ids(l: List<TabMeta>) = l.map { it.id }
    private val tabs = listOf(tab(1), tab(2), tab(3), tab(4))

    @Test fun movesForwardAndKeepsTheRestInOrder() {
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(tabs.withMoved(1, 2)))
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(tabs.withMoved(1, 3)))
    }

    @Test fun movesBackward() {
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(tabs.withMoved(4, 0)))
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(tabs.withMoved(3, 1)))
    }

    @Test fun movingToTheSamePlaceOrAnUnknownTabChangesNothing() {
        assertSame(tabs, tabs.withMoved(2, 1))
        assertSame(tabs, tabs.withMoved(99, 0))
    }

    @Test fun targetIsClampedIntoTheList() {
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(tabs.withMoved(1, 50)))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(tabs.withMoved(4, -7)))
    }

    @Test fun movingKeepsEachTabsOwnState() {
        val marked = tabs.withActivity(3, activeId = 1).withCharacter(3, "Thoran")
        val moved = marked.withMoved(3, 0)
        assertEquals("Thoran", moved[0].characterName)
        assertTrue(moved[0].hasUnseenActivity)
    }
}
