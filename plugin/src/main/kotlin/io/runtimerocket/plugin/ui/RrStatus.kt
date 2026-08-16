package io.runtimerocket.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import java.util.concurrent.ConcurrentHashMap

/** In-memory session status for the status-bar widget. Latency is reload-only (compile-finished → result). */
object RrStatus {
    const val TOOL_WINDOW_ID = "RuntimeRocket"
    const val SUCCESS_HOLD_MS = 3_000

    enum class Phase {
        IDLE,
        WAITING,
        ATTACHED,
        NOT_ATTACHED,
        COMPILING,
        RELOADING,
        SUCCESS,
        PARTIAL,
        RESTART_REQUIRED,
        FAILED,
    }

    data class Snapshot(
        val phase: Phase,
        val detail: String = "",
        val latencyMs: Long? = null,
        val backend: String = "",
    )

    private val byProject = ConcurrentHashMap<Project, Snapshot>()
    private val alarms = ConcurrentHashMap<Project, Alarm>()

    fun get(project: Project): Snapshot = byProject[project] ?: Snapshot(Phase.IDLE)

    fun widgetText(project: Project): String {
        val snap = get(project)
        return when (snap.phase) {
            Phase.IDLE -> RrReloadPresenter.formatIdle(snap.detail)
            Phase.WAITING -> RrReloadPresenter.formatWaiting()
            Phase.ATTACHED -> RrReloadPresenter.formatAttached(snap.backend.ifBlank { snap.detail })
            Phase.NOT_ATTACHED -> RrReloadPresenter.formatNotAttached()
            Phase.COMPILING -> RrReloadPresenter.formatCompiling()
            Phase.RELOADING -> RrReloadPresenter.formatReloading(snap.detail.toIntOrNull() ?: 0)
            Phase.SUCCESS -> RrReloadPresenter.formatSuccess(snap.latencyMs ?: 0L)
            Phase.PARTIAL -> RrReloadPresenter.formatPartial(snap.latencyMs ?: 0L)
            Phase.RESTART_REQUIRED -> RrReloadPresenter.STATUS_RESTART
            Phase.FAILED -> RrReloadPresenter.STATUS_FAILED
        }
    }

    fun waitingForAgent(project: Project) {
        publish(project, Snapshot(Phase.WAITING, "waiting for agent"))
    }

    fun attached(project: Project, backend: String) {
        publish(project, Snapshot(Phase.ATTACHED, backend, backend = backend))
    }

    fun notAttached(project: Project) {
        publish(project, Snapshot(Phase.NOT_ATTACHED, "not attached"))
    }

    fun idle(project: Project, detail: String = "") {
        publish(project, Snapshot(Phase.IDLE, detail))
    }

    fun compiling(project: Project) {
        val previous = get(project)
        publish(project, Snapshot(Phase.COMPILING, "compiling", backend = previous.backend))
    }

    fun reloading(project: Project, classCount: Int) {
        val previous = get(project)
        publish(project, Snapshot(Phase.RELOADING, classCount.toString(), backend = previous.backend))
    }

    fun success(project: Project, latencyMs: Long) {
        val previous = get(project)
        publish(project, Snapshot(Phase.SUCCESS, "$latencyMs ms", latencyMs, previous.backend))
        scheduleRestore(project, previous.backend)
    }

    fun partial(project: Project, latencyMs: Long) {
        val previous = get(project)
        publish(project, Snapshot(Phase.PARTIAL, "$latencyMs ms", latencyMs, previous.backend))
    }

    fun restartRequired(project: Project) {
        val previous = get(project)
        publish(project, Snapshot(Phase.RESTART_REQUIRED, "restart", backend = previous.backend))
    }

    fun failed(project: Project) {
        val previous = get(project)
        publish(project, Snapshot(Phase.FAILED, "failed", backend = previous.backend))
    }

    fun openToolWindow(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)
    }

    private fun scheduleRestore(project: Project, backend: String) {
        val alarm =
            alarms.compute(project) { p, existing ->
                existing?.cancelAllRequests()
                existing ?: Alarm(Alarm.ThreadToUse.SWING_THREAD, p)
            }!!
        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                val current = get(project)
                if (current.phase == Phase.SUCCESS) {
                    if (backend.isBlank()) {
                        idle(project)
                    } else {
                        attached(project, backend)
                    }
                }
            },
            SUCCESS_HOLD_MS,
        )
    }

    private fun publish(project: Project, snapshot: Snapshot) {
        byProject[project] = snapshot
        val app = ApplicationManager.getApplication()
        val refresh = {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget(RrStatusBarWidget.WIDGET_ID)
            project.messageBus.syncPublisher(RrUiRefresh.TOPIC).refresh()
        }
        if (app.isDispatchThread) {
            refresh()
        } else {
            app.invokeLater(refresh)
        }
    }
}
