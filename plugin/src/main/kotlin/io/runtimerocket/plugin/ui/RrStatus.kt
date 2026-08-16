package io.runtimerocket.plugin.ui

import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/** In-memory session status for the status-bar widget. */
object RrStatus {
    enum class Phase {
        IDLE,
        WAITING,
        ATTACHED,
        NOT_ATTACHED,
    }

    data class Snapshot(
        val phase: Phase,
        val detail: String = "",
    )

    private val byProject = ConcurrentHashMap<Project, Snapshot>()

    fun get(project: Project): Snapshot = byProject[project] ?: Snapshot(Phase.IDLE)

    fun waitingForAgent(project: Project) {
        byProject[project] = Snapshot(Phase.WAITING, "waiting for agent")
    }

    fun attached(project: Project, backend: String) {
        byProject[project] = Snapshot(Phase.ATTACHED, backend)
    }

    fun notAttached(project: Project) {
        byProject[project] = Snapshot(Phase.NOT_ATTACHED, "not attached")
    }

    fun idle(project: Project, detail: String = "") {
        byProject[project] = Snapshot(Phase.IDLE, detail)
    }
}
