package io.runtimerocket.plugin.watch

import io.runtimerocket.protocol.ReloadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RrReloadPlanTest {
    @Test
    fun emptyHotReloadEscalatesToBuild() {
        assertEquals(
            RrReloadPlan.Step.BUILD,
            RrReloadPlan.next(RrReloadPlan.Step.HOT_RELOAD, RrReloadPlan.Outcome.EMPTY),
        )
    }

    @Test
    fun successAndRestartDoNotEscalate() {
        assertEquals(RrReloadPlan.Step.DONE, RrReloadPlan.next(RrReloadPlan.Step.HOT_RELOAD, RrReloadPlan.Outcome.SUCCESS))
        assertEquals(RrReloadPlan.Step.DONE, RrReloadPlan.next(RrReloadPlan.Step.HOT_RELOAD, RrReloadPlan.Outcome.PARTIAL))
        assertEquals(
            RrReloadPlan.Step.DONE,
            RrReloadPlan.next(RrReloadPlan.Step.HOT_RELOAD, RrReloadPlan.Outcome.RESTART_REQUIRED),
        )
        assertEquals(
            "Stopped — compiling again will not apply this edit. Restart the process.",
            RrReloadPlan.stopReason(RrReloadPlan.Outcome.RESTART_REQUIRED),
        )
        assertNull(RrReloadPlan.stopReason(RrReloadPlan.Outcome.SUCCESS))
    }

    @Test
    fun timedOutBuildDoesNotStartAnotherCompile() {
        assertEquals(
            RrReloadPlan.Step.DONE,
            RrReloadPlan.next(RrReloadPlan.Step.BUILD, RrReloadPlan.Outcome.TIMED_OUT),
        )
        assertEquals(
            RrReloadPlan.Step.DONE,
            RrReloadPlan.next(RrReloadPlan.Step.HOT_RELOAD, RrReloadPlan.Outcome.TIMED_OUT),
        )
    }

    @Test
    fun failedBuildGoesToDiagnoseThenStopsOnRealErrors() {
        assertEquals(
            RrReloadPlan.Step.DIAGNOSE,
            RrReloadPlan.next(RrReloadPlan.Step.BUILD, RrReloadPlan.Outcome.COMPILE_FAILED),
        )
        assertEquals(
            RrReloadPlan.Step.DONE,
            RrReloadPlan.next(RrReloadPlan.Step.DIAGNOSE, RrReloadPlan.Outcome.REAL_ERRORS),
        )
    }

    @Test
    fun outcomeMapping() {
        assertEquals(
            RrReloadPlan.Outcome.NOT_ATTACHED,
            RrReloadPlan.outcomeOf(ReloadResult.SUCCESS, emptyDiff = false, attached = false),
        )
        assertEquals(RrReloadPlan.Outcome.EMPTY, RrReloadPlan.outcomeOf(null, emptyDiff = true, attached = true))
        assertEquals(
            RrReloadPlan.Outcome.RESTART_REQUIRED,
            RrReloadPlan.outcomeOf(ReloadResult.RESTART_REQUIRED, emptyDiff = false, attached = true),
        )
    }
}
