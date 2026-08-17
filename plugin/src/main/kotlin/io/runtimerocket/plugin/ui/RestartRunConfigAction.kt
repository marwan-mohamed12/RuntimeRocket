package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.runtimerocket.plugin.run.RrSession
import io.runtimerocket.plugin.run.RrSessionManager

class RestartRunConfigAction(
    private val project: Project? = null,
    private val session: RrSession? = null,
) : AnAction("Restart", "Restart the RuntimeRocket run configuration", RrIcons.Restart), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val targetProject = project ?: e.project ?: return
        val target = session ?: RrSessionManager.getInstance(targetProject).activeSessions().firstOrNull()
        target?.restart()
    }

    override fun update(e: AnActionEvent) {
        val targetProject = project ?: e.project
        e.presentation.isEnabled = targetProject != null &&
            (session != null || RrSessionManager.getInstance(targetProject).hasActiveSession())
    }
}
