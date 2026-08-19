package io.runtimerocket.plugin.ui

import com.intellij.notification.NotificationType
import io.runtimerocket.protocol.ClassOutcome
import io.runtimerocket.protocol.ReloadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrReloadPresenterTest {
    @Test
    fun restartRequiredRendersRestartAction() {
        val result = ReloadResult()
        result.status = ReloadResult.RESTART_REQUIRED
        result.message = "hierarchy change"
        result.classes = listOf(ClassOutcome("com.example.Foo", ClassOutcome.FAILED, listOf("HIERARCHY"), "hierarchy change"))

        val decision = RrReloadPresenter.present(result, reloadLatencyMs = 88, showSuccessBalloon = false, classCount = 1)
        assertFalse(decision.silent)
        assertTrue(decision.showBalloon)
        assertEquals(NotificationType.ERROR, decision.balloonType)
        assertEquals(listOf(RrReloadPresenter.ACTION_RESTART), decision.actions)
        assertEquals(RrReloadPresenter.STATUS_RESTART, decision.statusBar)
        assertTrue(decision.balloonText.contains("Restart required") || decision.balloonText.contains("hierarchy"), decision.balloonText)

        val action = RestartRunConfigAction()
        assertEquals("Restart", action.templatePresentation.text)
        assertTrue(RestartRunConfigAction.LATE_ATTACH_NO_RUN_CONFIG.contains("attached"), RestartRunConfigAction.LATE_ATTACH_NO_RUN_CONFIG)
    }

    @Test
    fun partialIsNeverSilent() {
        val result = ReloadResult()
        result.status = ReloadResult.PARTIAL
        result.message = "controller mappings may be stale"
        val decision = RrReloadPresenter.present(result, reloadLatencyMs = 40, showSuccessBalloon = false, classCount = 1)
        assertTrue(decision.showBalloon)
        assertFalse(decision.silent)
        assertEquals(NotificationType.WARNING, decision.balloonType)
        assertTrue(decision.actions.isEmpty())
        assertEquals("RR ~ 40 ms", decision.statusBar)
    }

    @Test
    fun successBalloonIsOptionalAndLatencyIsReloadOnly() {
        val result = ReloadResult()
        result.status = ReloadResult.SUCCESS
        result.durationMs = 12
        val hidden = RrReloadPresenter.present(result, reloadLatencyMs = 142, showSuccessBalloon = false, classCount = 3)
        val shown = RrReloadPresenter.present(result, reloadLatencyMs = 142, showSuccessBalloon = true, classCount = 3)
        assertFalse(hidden.showBalloon)
        assertTrue(shown.showBalloon)
        assertEquals("RR ✓ 142 ms", hidden.statusBar)
        assertFalse(hidden.statusBar.contains("12"))
        assertEquals("RR ✓ 142 ms", RrReloadPresenter.formatSuccess(142))
        assertFalse(RrReloadPresenter.formatSuccess(142).contains("compile"))
    }

    @Test
    fun failedAlsoOffersRestart() {
        val result = ReloadResult()
        result.status = ReloadResult.FAILED
        result.message = "JVMTI redefine failed"
        val decision = RrReloadPresenter.present(result, reloadLatencyMs = 9, showSuccessBalloon = false, classCount = 1)
        assertTrue(decision.showBalloon)
        assertEquals(listOf(RrReloadPresenter.ACTION_RESTART), decision.actions)
        assertEquals(RrReloadPresenter.STATUS_FAILED, decision.statusBar)
    }
}
