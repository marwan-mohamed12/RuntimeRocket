package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.runtimerocket.plugin.run.RrSession
import io.runtimerocket.plugin.run.RrSessionManager

class RestartRunConfigAction(
    private val project: Project? = null,
    private val session: RrSession? = null,
) : AnAction("Restart", "Restart the RuntimeRocket run configuration", RrIcons.Restart), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val targetProject = project ?: e.project ?: return
        val manager = RrSessionManager.getInstance(targetProject)
        val target =
            session
                ?: manager.activeSessions().firstOrNull { it.canRestart() }
                ?: manager.activeSessions().firstOrNull()
        if (target == null) {
            Messages.showInfoMessage(targetProject, NOTHING_ATTACHED, ACTION_TEXT)
            return
        }
        if (!target.restart()) {
            Messages.showInfoMessage(targetProject, LATE_ATTACH_NO_RUN_CONFIG, ACTION_TEXT)
        }
    }

    override fun update(e: AnActionEvent) {
        val targetProject = project ?: e.project
        e.presentation.isEnabled = targetProject != null &&
            (session != null || RrSessionManager.getInstance(targetProject).hasActiveSession())
    }

    companion object {
        const val ACTION_TEXT = "Restart"
        const val NOTHING_ATTACHED = "Nothing is attached. Start the app or use Attach first."
        const val LATE_ATTACH_NO_RUN_CONFIG =
            "This JVM was attached, not started from a run configuration.\n\n" +
                "Stop the process (for Hybris: stop hybrisserver), start it again, then Attach."
    }
}
