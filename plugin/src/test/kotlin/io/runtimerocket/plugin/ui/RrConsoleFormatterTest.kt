package io.runtimerocket.plugin.ui

import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.protocol.AdapterOutcome
import io.runtimerocket.protocol.ClassOutcome
import io.runtimerocket.protocol.ReloadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RrConsoleFormatterTest {
    @Test
    fun statusKindsMatchDesignColors() {
        assertEquals(RrConsoleFormatter.Kind.SUCCESS, RrConsoleFormatter.kindForStatus(ReloadResult.SUCCESS))
        assertEquals(RrConsoleFormatter.Kind.PARTIAL, RrConsoleFormatter.kindForStatus(ReloadResult.PARTIAL))
        assertEquals(RrConsoleFormatter.Kind.WARNING, RrConsoleFormatter.kindForStatus(ReloadResult.RESTART_REQUIRED))
        assertEquals(RrConsoleFormatter.Kind.ERROR, RrConsoleFormatter.kindForStatus(ReloadResult.FAILED))
        assertEquals(RrConsoleFormatter.Kind.SUCCESS, RrConsoleFormatter.kindForStatus(ClassOutcome.REDEFINED))
    }

    @Test
    fun lastResultIncludesStatusLatencyAndClasses() {
        val event =
            RrReloadHistory.Event(
                time = Instant.parse("2026-08-17T12:00:00Z"),
                classCount = 1,
                resourceCount = 0,
                status = ReloadResult.SUCCESS,
                durationMs = 18,
                latencyMs = 42,
                message = null,
                classes = listOf(ClassOutcome("com.example.Foo", ClassOutcome.REDEFINED, listOf("METHOD_BODY"), null)),
                adapters = listOf(AdapterOutcome("spring", AdapterOutcome.SUCCESS, 7, "ok")),
                trigger = "compile",
            )
        val text = RrConsoleFormatter.lastResult(event, null).joinToString("") { it.text }
        assertTrue(text.contains("LAST RESULT"), text)
        assertTrue(text.contains("SUCCESS"), text)
        assertTrue(text.contains("42 ms"), text)
        assertTrue(text.contains("18 ms"), text)
        assertTrue(text.contains("com.example.Foo"), text)
        assertTrue(text.contains("REDEFINED"), text)
        assertTrue(text.contains("METHOD_BODY"), text)
        assertTrue(text.contains("spring"), text)
    }

    @Test
    fun restartRequiredKeepsRestartHint() {
        val event =
            RrReloadHistory.Event(
                time = Instant.parse("2026-08-17T12:00:00Z"),
                classCount = 1,
                resourceCount = 0,
                status = ReloadResult.RESTART_REQUIRED,
                durationMs = 5,
                latencyMs = 12,
                message = "hierarchy change",
                classes = emptyList(),
                adapters = emptyList(),
                trigger = "compile",
            )
        val text = RrConsoleFormatter.lastResult(event, null).joinToString("") { it.text }
        assertTrue(text.contains("RESTART_REQUIRED"), text)
        assertTrue(text.contains("Restart"), text)
        assertTrue(text.contains("hierarchy change"), text)
    }

    @Test
    fun eventsAlignStatusAndCount() {
        val event =
            RrReloadHistory.Event(
                time = Instant.parse("2026-08-17T12:00:00Z"),
                classCount = 2,
                resourceCount = 0,
                status = ReloadResult.PARTIAL,
                durationMs = 9,
                latencyMs = 33,
                message = "stale mappings",
                classes = emptyList(),
                adapters = emptyList(),
                trigger = "compile",
            )
        val segments = RrConsoleFormatter.events(listOf(event))
        val text = segments.joinToString("") { it.text }
        assertTrue(text.contains("RECENT EVENTS"), text)
        assertTrue(text.contains(RrConsoleFormatter.padStatus(ReloadResult.PARTIAL)), text)
        assertTrue(text.contains("2 classes"), text)
        assertTrue(text.contains("33 ms"), text)
        assertTrue(text.contains("stale mappings"), text)
        assertEquals(RrConsoleFormatter.Kind.PARTIAL, segments.first { it.text.trim() == RrConsoleFormatter.padStatus(ReloadResult.PARTIAL).trim() || it.text == RrConsoleFormatter.padStatus(ReloadResult.PARTIAL) }.kind)
    }

    @Test
    fun lastResultListsReloadSteps() {
        val text =
            RrConsoleFormatter.lastResult(null, null, listOf("1. Hot reload", "   no module in focus"))
                .joinToString("") { it.text }
        assertTrue(text.contains("RELOAD STEPS"), text)
        assertTrue(text.contains("1. Hot reload"), text)
        assertTrue(text.contains("no module in focus"), text)
    }

    @Test
    fun emptyLastResultExplainsHowToStart() {
        val text = RrConsoleFormatter.lastResult(null, null).joinToString("") { it.text }
        assertTrue(text.contains("No reload yet"), text)
        assertTrue(text.contains("Attach"), text)
    }

    @Test
    fun detachMessageKeepsTheJvmRunning() {
        assertEquals("Nothing is attached.", DetachRuntimeRocketAction.message(0))
        assertEquals("RuntimeRocket detached. The JVM is still running.", DetachRuntimeRocketAction.message(1))
        assertTrue(DetachRuntimeRocketAction.message(2).contains("2 sessions"))
    }

    @Test
    fun statusCaptionsDescribePhase() {
        assertEquals("ENHANCED", RrConsoleFormatter.statusCaption(RrStatus.Phase.ATTACHED, "enhanced"))
        assertEquals("STANDARD", RrConsoleFormatter.statusCaption(RrStatus.Phase.ATTACHED, "standard"))
        assertEquals("NOT ATTACHED", RrConsoleFormatter.statusCaption(RrStatus.Phase.NOT_ATTACHED))
        assertEquals("RESTART", RrConsoleFormatter.statusCaption(RrStatus.Phase.RESTART_REQUIRED))
    }
}
