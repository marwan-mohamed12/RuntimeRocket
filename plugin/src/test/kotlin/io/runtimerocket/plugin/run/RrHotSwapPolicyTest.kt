package io.runtimerocket.plugin.run

import com.intellij.debugger.ui.HotSwapVetoableListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrHotSwapPolicyTest {
    @Test
    fun pathAVetoListenerCancelsStockHotSwapWhileAttached() {
        assertEquals("session veto on", RrHotSwapPolicy.footerText())
        assertTrue(HotSwapVetoableListener::class.java.isAssignableFrom(RrHotSwapVeto::class.java))
        assertFalse(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = true))
        assertTrue(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = false))
    }
}
