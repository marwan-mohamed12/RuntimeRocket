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
        assertFalse(RrHotSwapPolicy.shouldAllowStockHotSwap(hasActiveRrSession = true))
        val method = RrHotSwapVeto::class.java.getMethod("shouldHotSwap", com.intellij.task.ProjectTaskContext::class.java)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
        assertTrue(HotSwapVetoableListener::class.java.isAssignableFrom(RrHotSwapVeto::class.java))
    }

    @Test
    fun shouldHotSwapIsFalseWhileSessionIsRegistered() {
        val attached = RrHotSwapVeto(hasActiveSession = { true })
        val detached = RrHotSwapVeto(hasActiveSession = { false })
        val context = com.intellij.task.ProjectTaskContext()
        assertFalse(attached.shouldHotSwap(context))
        assertTrue(detached.shouldHotSwap(context))
    }
}
