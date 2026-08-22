package io.runtimerocket.plugin.run

import io.runtimerocket.plugin.watch.RrReloadService
import io.runtimerocket.protocol.ReloadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrProcessRestartTest {
    @Test
    fun prefersFreshEnvironmentWhenRunConfigSettingsExist() {
        assertEquals(RrProcessRestart.Strategy.FRESH_ENVIRONMENT, RrProcessRestart.strategy(hasRunnerSettings = true))
        assertEquals(RrProcessRestart.Strategy.REUSE_STORED_ENVIRONMENT, RrProcessRestart.strategy(hasRunnerSettings = false))
    }

    @Test
    fun restartRequiredAdvancesSnapshotSoWatchCannotResend() {
        assertTrue(RrReloadService.shouldAdvanceSnapshot(ReloadResult.SUCCESS))
        assertTrue(RrReloadService.shouldAdvanceSnapshot(ReloadResult.PARTIAL))
        assertTrue(RrReloadService.shouldAdvanceSnapshot(ReloadResult.RESTART_REQUIRED))
        assertFalse(RrReloadService.shouldAdvanceSnapshot(ReloadResult.FAILED))
        assertFalse(RrReloadService.shouldAdvanceSnapshot(null))
    }
}
