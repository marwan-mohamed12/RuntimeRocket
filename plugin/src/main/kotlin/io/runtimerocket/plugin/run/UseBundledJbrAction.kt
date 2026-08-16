package io.runtimerocket.plugin.run

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.runtimerocket.plugin.settings.RrProjectSettings

class UseBundledJbrAction(
    private val project: Project? = null,
    private val configuration: RunProfile? = null,
) : AnAction(RrJbrConsent.ACTION_TEXT), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val targetProject = project ?: e.project ?: return
        applyFromUi(targetProject, configuration ?: selected(targetProject))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = (project ?: e.project) != null
    }

    companion object {
        fun notificationAction(project: Project, configuration: RunProfile?): NotificationAction {
            return NotificationAction.createExpiring(RrJbrConsent.ACTION_TEXT) { _, notification ->
                applyFromUi(project, configuration, notification)
            }
        }

        fun declineNotificationAction(project: Project): NotificationAction {
            return NotificationAction.createExpiring(RrJbrConsent.CONTINUE_TEXT) { _, _ ->
                RrJbrConsent.decline(RrProjectSettings.getInstance(project))
            }
        }

        internal fun applyFromUi(project: Project, configuration: RunProfile?, notification: Notification? = null) {
            val settings = RrProjectSettings.getInstance(project)
            if (!settings.jbrConsentAccepted) {
                val choice =
                    Messages.showYesNoDialog(
                        project,
                        RrJbrConsent.DIALOG_MESSAGE,
                        RrJbrConsent.DIALOG_TITLE,
                        "Use JetBrains Runtime",
                        RrJbrConsent.CONTINUE_TEXT,
                        Messages.getQuestionIcon(),
                    )
                if (choice != Messages.YES) {
                    RrJbrConsent.decline(settings)
                    return
                }
            }
            RrJbrConsent.accept(settings)
            val applied = RrJbrConsent.applyToRunProfile(configuration, consentAccepted = true, jbrHome = JbrDetector.jbrHome())
            notification?.expire()
            if (!applied) {
                Messages.showErrorDialog(
                    project,
                    "Could not apply bundled JetBrains Runtime. No JBR was found, or this run configuration cannot set a JRE path.",
                    RrJbrConsent.DIALOG_TITLE,
                )
            }
        }

        private fun selected(project: Project): RunProfile? {
            return RunManager.getInstance(project).selectedConfiguration?.configuration
        }
    }
}
