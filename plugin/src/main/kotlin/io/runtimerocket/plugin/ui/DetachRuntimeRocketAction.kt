package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.runtimerocket.plugin.run.RrSessionManager

class DetachRuntimeRocketAction : AnAction(
    "Detach",
    "Disconnect RuntimeRocket from the running JVM without stopping it",
    RrIcons.Detach,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        detach(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && RrSessionManager.getInstance(project).hasActiveSession()
    }

    companion object {
        const val ACTION_TEXT = "Detach"

        fun detach(project: Project): String {
            val count = RrSessionManager.getInstance(project).disconnectAll()
            val message = message(count)
            if (count > 0) {
                RrStatus.notAttached(project)
                RrNotifier.detached(project, message)
            }
            return message
        }

        internal fun message(count: Int): String {
            return when {
                count <= 0 -> "Nothing is attached."
                count == 1 -> "RuntimeRocket detached. The JVM is still running."
                else -> "RuntimeRocket detached $count sessions. The JVMs are still running."
            }
        }
    }
}
