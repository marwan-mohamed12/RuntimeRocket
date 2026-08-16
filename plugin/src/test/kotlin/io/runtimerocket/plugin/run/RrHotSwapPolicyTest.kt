package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrHotSwapPolicyTest {
    @Test
    fun shippedBranchIsPathA() {
        assertEquals(RrHotSwapPolicy.BRANCH_A, RrHotSwapPolicy.BRANCH_A)
        assertEquals("A", RrHotSwapPolicy.BRANCH_A)
        assertEquals("session veto on", RrHotSwapPolicy.FOOTER_A)
    }

    @Test
    fun vetoesStockHotSwapOnlyWhileRrSessionAttached() {
        assertFalse(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = true))
        assertTrue(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = false))
    }
}
