package io.runtimerocket.plugin.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrConsoleViewportTest {
    @Test
    fun unchangedTextDoesNotRewrite() {
        val decision = RrConsoleViewport.decide("same", "same", scrollValue = 40, visibleAmount = 20, maximum = 100)
        assertTrue(decision.skip)
        assertFalse(decision.followEnd)
        assertNull(decision.restoreValue)
    }

    @Test
    fun midScrollKeepsPositionWhenContentChanges() {
        val decision = RrConsoleViewport.decide("old", "new", scrollValue = 30, visibleAmount = 20, maximum = 100)
        assertFalse(decision.skip)
        assertFalse(decision.followEnd)
        assertEquals(30, decision.restoreValue)
    }

    @Test
    fun atBottomFollowsNewContent() {
        val decision = RrConsoleViewport.decide("old", "new", scrollValue = 80, visibleAmount = 20, maximum = 100)
        assertFalse(decision.skip)
        assertTrue(decision.followEnd)
        assertNull(decision.restoreValue)
    }
}
