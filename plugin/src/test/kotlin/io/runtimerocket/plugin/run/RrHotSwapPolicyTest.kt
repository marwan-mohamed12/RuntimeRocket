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

    @Test
    fun addMethodDuringCompileTakesVetoPathNotStockHotSwapDialog() {
        assertEquals(RrHotSwapPolicy.BRANCH_A, RrHotSwapPolicy.installedBranch)
        assertEquals(RrHotSwapPolicy.FOOTER_A, RrHotSwapPolicy.footerText())
        // Path A: stock HotSwap is vetoed for the RR process, so add-method compile
        // must not open the IDE "add method not supported" dialog.
        assertFalse(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = true))
        val method = RrHotSwapVeto::class.java.getMethod("shouldHotSwap", com.intellij.task.ProjectTaskContext::class.java)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
        assertTrue(HotSwapVetoableListener::class.java.isAssignableFrom(RrHotSwapVeto::class.java))
    }
}
