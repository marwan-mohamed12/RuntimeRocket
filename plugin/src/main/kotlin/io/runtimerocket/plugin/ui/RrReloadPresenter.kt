package io.runtimerocket.plugin.ui

import com.intellij.notification.NotificationType
import io.runtimerocket.protocol.ReloadResult

/** Pure mapping from [ReloadResult] to status-bar / balloon / action choices. */
object RrReloadPresenter {
    const val ACTION_RESTART = "Restart"

    data class Decision(
        val statusBar: String,
        val showBalloon: Boolean,
        val balloonType: NotificationType,
        val balloonText: String,
        val actions: List<String>,
        val silent: Boolean,
    )

    fun present(
        result: ReloadResult,
        reloadLatencyMs: Long,
        showSuccessBalloon: Boolean,
        classCount: Int,
    ): Decision {
        val status = result.status ?: ReloadResult.FAILED
        val reason = result.message?.takeIf { it.isNotBlank() } ?: defaultReason(status)
        return when (status) {
            ReloadResult.SUCCESS ->
                Decision(
                    statusBar = formatSuccess(reloadLatencyMs),
                    showBalloon = showSuccessBalloon,
                    balloonType = NotificationType.INFORMATION,
                    balloonText = "Reloaded $classCount class(es) in $reloadLatencyMs ms",
                    actions = emptyList(),
                    silent = false,
                )
            ReloadResult.PARTIAL ->
                Decision(
                    statusBar = formatPartial(reloadLatencyMs),
                    showBalloon = true,
                    balloonType = NotificationType.WARNING,
                    balloonText = "Partial reload: $reason",
                    actions = emptyList(),
                    silent = false,
                )
            ReloadResult.RESTART_REQUIRED ->
                Decision(
                    statusBar = STATUS_RESTART,
                    showBalloon = true,
                    balloonType = NotificationType.ERROR,
                    balloonText = restartText(reason),
                    actions = listOf(ACTION_RESTART),
                    silent = false,
                )
            else ->
                Decision(
                    statusBar = STATUS_FAILED,
                    showBalloon = true,
                    balloonType = NotificationType.ERROR,
                    balloonText = "Reload failed: $reason",
                    actions = listOf(ACTION_RESTART),
                    silent = false,
                )
        }
    }

    fun formatSuccess(reloadLatencyMs: Long): String = "RR ✓ $reloadLatencyMs ms"

    fun formatPartial(reloadLatencyMs: Long): String = "RR ~ $reloadLatencyMs ms"

    fun formatReloading(classCount: Int): String = "RR ↻ $classCount classes"

    fun formatCompiling(): String = "RR ⚙ compiling"

    fun formatAttached(backend: String): String = "RR ● ${backend.ifBlank { "attached" }}"

    fun formatNotAttached(): String = "RR ○ not attached"

    fun formatWaiting(): String = "RR … waiting for agent"

    fun formatIdle(detail: String): String = if (detail.isBlank()) "RR" else "RR $detail"

    private fun restartText(reason: String): String {
        val name = reason.substringAfter('`').substringBefore('`').ifBlank { "change" }
        return if (reason.contains('`')) {
            "Cannot hot-reload `$name`: ${reason.substringAfterLast(':').trim().ifBlank { reason }}. Restart required."
        } else {
            "Cannot hot-reload: $reason. Restart required."
        }
    }

    private fun defaultReason(status: String): String {
        return when (status) {
            ReloadResult.RESTART_REQUIRED -> "unsupported change"
            ReloadResult.PARTIAL -> "adapter reported a partial result"
            ReloadResult.FAILED -> "reload failed"
            else -> status
        }
    }

    const val STATUS_RESTART = "RR ✕ restart"
    const val STATUS_FAILED = "RR ✕ failed"
}
