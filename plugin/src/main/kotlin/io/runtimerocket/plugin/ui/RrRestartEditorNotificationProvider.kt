package io.runtimerocket.plugin.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import io.runtimerocket.plugin.run.RrSessionManager
import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.protocol.ReloadResult
import java.util.function.Function
import javax.swing.JComponent

class RrRestartEditorNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        val history = RrReloadHistory.getInstance(project)
        if (!history.showRestartBanner()) {
            return null
        }
        val last = history.latest() ?: return null
        val reason = last.message?.takeIf { it.isNotBlank() } ?: last.status
        return Function { editor ->
            if (!history.showRestartBanner()) {
                return@Function null
            }
            val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Error)
            panel.text =
                if (last.status == ReloadResult.RESTART_REQUIRED) {
                    "RuntimeRocket: restart required — $reason"
                } else {
                    "RuntimeRocket: last reload failed — $reason"
                }
            panel.createActionLabel("Restart") {
                val manager = RrSessionManager.getInstance(project)
                val target = manager.activeSessions().firstOrNull { it.canRestart() } ?: manager.activeSessions().firstOrNull()
                if (target == null || !target.restart()) {
                    Messages.showInfoMessage(
                        project,
                        RestartRunConfigAction.LATE_ATTACH_NO_RUN_CONFIG,
                        RestartRunConfigAction.ACTION_TEXT,
                    )
                }
            }
            panel.createActionLabel("Dismiss") {
                history.dismissRestartBanner()
                com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
            }
            panel
        }
    }
}
