package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.runtimerocket.plugin.run.RrHotSwapPolicy
import io.runtimerocket.plugin.run.RrSessionManager
import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.protocol.ReloadResult
import java.awt.BorderLayout
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JPanel
import javax.swing.JTextArea

class RrToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RrToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class RrToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val sessionsLabel = JBLabel("No session")
    private val footerLabel = JBLabel(RrHotSwapPolicy.footerText())
    private val log = JTextArea()

    init {
        log.isEditable = false
        log.lineWrap = true
        log.wrapStyleWord = true
        log.background = JBColor.background()
        val actions = DefaultActionGroup()
        actions.add(ReloadNowAction())
        actions.add(RestartRunConfigAction(project))
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, actions, true)
        toolbar.targetComponent = this

        val header =
            FormBuilder.createFormBuilder()
                .addComponent(sessionsLabel)
                .addComponent(footerLabel)
                .panel
        header.border = JBUI.Borders.empty(6)
        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(log), BorderLayout.CENTER)
        add(header, BorderLayout.SOUTH)

        project.messageBus.connect(project).subscribe(RrUiRefresh.TOPIC, RrUiRefresh { refresh() })
        refresh()
    }

    fun refresh() {
        val sessions = RrSessionManager.getInstance(project).activeSessions()
        sessionsLabel.text =
            if (sessions.isEmpty()) {
                "No session"
            } else {
                sessions.joinToString("  |  ") { session ->
                    "pid ${session.pid}  ${session.backend}  ${session.handshake.version}"
                }
            }
        footerLabel.text = RrHotSwapPolicy.footerText()
        log.text = renderHistory()
        log.caretPosition = 0
    }

    private fun renderHistory(): String {
        val history = RrReloadHistory.getInstance(project)
        val missing = history.lastMissingOutput
        val latest = history.latest()
        val sb = StringBuilder()
        if (latest != null) {
            sb.append("Last result: ").append(latest.status)
            sb.append("  reload ").append(latest.latencyMs).append(" ms")
            sb.append("  agent ").append(latest.durationMs).append(" ms\n")
            if (!latest.message.isNullOrBlank()) {
                sb.append("Reason: ").append(latest.message).append('\n')
            }
            if (latest.status == ReloadResult.RESTART_REQUIRED) {
                sb.append("RESTART_REQUIRED — use Restart to relaunch the run configuration.\n")
            }
            if (latest.classes.isNotEmpty()) {
                sb.append("Classes:\n")
                for (outcome in latest.classes) {
                    sb.append("  ").append(outcome.binaryName).append("  ").append(outcome.status)
                    if (!outcome.reason.isNullOrBlank()) {
                        sb.append("  ").append(outcome.reason)
                    }
                    val kinds = outcome.changeKinds
                    if (!kinds.isNullOrEmpty()) {
                        sb.append("  ").append(kinds.joinToString(","))
                    }
                    sb.append('\n')
                }
            }
            if (latest.adapters.isNotEmpty()) {
                sb.append("Adapters:\n")
                for (adapter in latest.adapters) {
                    sb.append("  ").append(adapter.adapterId).append("  ").append(adapter.status)
                    if (!adapter.detail.isNullOrBlank()) {
                        sb.append("  ").append(adapter.detail)
                    }
                    sb.append('\n')
                }
            }
            sb.append('\n')
        }
        if (!missing.isNullOrBlank()) {
            sb.append(missing).append("\n\n")
        }
        sb.append("Recent events\n")
        val events = history.recent()
        if (events.isEmpty()) {
            sb.append("  (none yet)\n")
        } else {
            for (event in events) {
                sb.append("  ").append(TIME.format(event.time.atZone(ZoneId.systemDefault())))
                sb.append("  ").append(event.status)
                sb.append("  ").append(event.classCount).append(" classes")
                sb.append("  ").append(event.latencyMs).append(" ms")
                if (!event.message.isNullOrBlank()) {
                    sb.append("  ").append(event.message)
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
