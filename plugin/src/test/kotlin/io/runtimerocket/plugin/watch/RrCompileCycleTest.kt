package io.runtimerocket.plugin.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrCompileCycleTest {
    @Test
    fun vfsIsBlockedDuringAndAfterFailedCompile() {
        val cycle = RrCompileCycle()
        cycle.onBuildStarted()
        assertTrue(cycle.compiling)
        assertFalse(cycle.shouldScheduleVfs())

        assertFalse(cycle.onCompileFinished(failed = true))
        assertFalse(cycle.compiling)
        assertTrue(cycle.listenerHandled)
        assertFalse(cycle.shouldScheduleVfs())
        assertEquals(RrCompileCycle.BuildFinish.RESTORE_IDLE, cycle.onBuildFinished())
        assertFalse(cycle.shouldScheduleVfs())
    }

    @Test
    fun vfsIsArmedOnlyWhenCompileListenerDidNotFire() {
        val cycle = RrCompileCycle()
        cycle.onBuildStarted()
        assertEquals(RrCompileCycle.BuildFinish.ARM_VFS, cycle.onBuildFinished())
        assertTrue(cycle.shouldScanOutputs())
        assertTrue(cycle.shouldScheduleVfs())
    }

    @Test
    fun armVfsMeansScanOutputsNotWaitForEvents() {
        val cycle = RrCompileCycle()
        cycle.onBuildStarted()
        assertFalse(cycle.shouldScanOutputs())
        assertEquals(RrCompileCycle.BuildFinish.ARM_VFS, cycle.onBuildFinished())
        assertTrue(cycle.shouldScanOutputs())
        cycle.onBuildStarted()
        assertFalse(cycle.shouldScanOutputs())
        assertFalse(cycle.shouldScheduleVfs())
    }

    @Test
    fun successfulCompileListenerDoesNotLeaveVfsArmed() {
        val cycle = RrCompileCycle()
        cycle.onBuildStarted()
        assertTrue(cycle.onCompileFinished(failed = false))
        cycle.markReloadStarted()
        assertEquals(RrCompileCycle.BuildFinish.NOTHING, cycle.onBuildFinished())
        assertFalse(cycle.shouldScheduleVfs())
    }
}
