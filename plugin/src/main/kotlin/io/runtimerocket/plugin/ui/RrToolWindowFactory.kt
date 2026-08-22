package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import io.runtimerocket.plugin.run.AttachRuntimeRocketAction
import io.runtimerocket.plugin.run.RrHotSwapPolicy
import io.runtimerocket.plugin.run.RrSessionManager
import io.runtimerocket.plugin.watch.RrReloadHistory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class RrToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RrToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class RrToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val statusDot = StatusDot()
    private val statusCaption = JBLabel("IDLE")
    private val sessionsLabel = consolePane()
    private val footerLabel = JBLabel(RrHotSwapPolicy.footerText())
    private val lastResult = consolePane()
    private val events = consolePane()
    private val lastResultCard = JPanel(BorderLayout())

    init {
        val actions = DefaultActionGroup()
        actions.add(LabeledToolAction(ReloadNowAction()))
        actions.add(Separator())
        actions.add(LabeledToolAction(AttachRuntimeRocketAction()))
        actions.add(Separator())
        actions.add(LabeledToolAction(DetachRuntimeRocketAction()))
        actions.add(Separator())
        actions.add(LabeledToolAction(RestartRunConfigAction(project)))
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, actions, true)
        toolbar.targetComponent = this
        toolbar.setReservePlaceAutoPopupIcon(false)
        setToolbar(toolbar.component)

        statusCaption.font = JBFont.label().asBold()
        sessionsLabel.isOpaque = false
        sessionsLabel.font = JBFont.small()
        sessionsLabel.foreground = RrUiColors.muted
        footerLabel.font = JBFont.small()
        footerLabel.foreground = RrUiColors.muted

        val statusRow = JPanel()
        statusRow.layout = BoxLayout(statusRow, BoxLayout.X_AXIS)
        statusRow.isOpaque = false
        statusRow.add(statusDot)
        statusRow.add(Box.createHorizontalStrut(JBUI.scale(8)))
        statusRow.add(statusCaption)
        statusRow.border = JBUI.Borders.emptyRight(12)

        val header = JPanel(BorderLayout())
        header.background = RrUiColors.headerBg
        header.border = JBUI.Borders.empty(8, 10, 8, 10)
        header.add(statusRow, BorderLayout.WEST)
        header.add(sessionsLabel, BorderLayout.CENTER)

        lastResultCard.background = RrUiColors.lastResultBg
        lastResultCard.border =
            JBUI.Borders.compound(
                JBUI.Borders.customLine(RrUiColors.attached, 0, 3, 0, 0),
                JBUI.Borders.empty(8, 12, 10, 12),
            )
        lastResult.border = JBUI.Borders.empty()
        lastResultCard.add(lastResult, BorderLayout.CENTER)
        val lastScroll = JBScrollPane(lastResultCard)
        lastScroll.border = JBUI.Borders.empty()
        lastScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        lastScroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

        val eventsScroll = JBScrollPane(events)
        eventsScroll.border = JBUI.Borders.empty()
        eventsScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        eventsScroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        events.border = JBUI.Borders.empty(8, 12, 8, 12)

        val tabs = JBTabbedPane()
        tabs.addTab("Reload steps", lastScroll)
        tabs.addTab("Recent events", eventsScroll)

        val footer = JPanel(BorderLayout())
        footer.isOpaque = false
        footer.border =
            JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(6, 12, 6, 12),
            )
        footer.add(footerLabel, BorderLayout.WEST)

        val body = JPanel(BorderLayout())
        body.background = RrUiColors.consoleBg
        body.add(header, BorderLayout.NORTH)

        val middle = JPanel(BorderLayout())
        middle.isOpaque = false
        middle.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
        middle.add(tabs, BorderLayout.CENTER)
        body.add(middle, BorderLayout.CENTER)
        body.add(footer, BorderLayout.SOUTH)

        setContent(body)

        project.messageBus.connect(project).subscribe(RrUiRefresh.TOPIC, RrUiRefresh { refresh() })
        refresh()
    }

    fun refresh() {
        val sessions = RrSessionManager.getInstance(project).activeSessions()
        val snap = RrStatus.get(project)
        val phase =
            if (sessions.isEmpty() && snap.phase == RrStatus.Phase.IDLE) {
                RrStatus.Phase.NOT_ATTACHED
            } else {
                snap.phase
            }
        val backend = sessions.firstOrNull()?.backend ?: snap.backend
        val phaseColor = RrUiColors.forPhase(phase, backend)
        statusDot.color = phaseColor
        statusCaption.text = RrConsoleFormatter.statusCaption(phase, backend)
        statusCaption.foreground = phaseColor
        applySegments(sessionsLabel, RrConsoleFormatter.sessionLines(sessions), JBFont.small())

        val history = RrReloadHistory.getInstance(project)
        val latest = history.latest()
        applySegments(lastResult, RrConsoleFormatter.lastResult(latest, history.lastMissingOutput, history.lastSteps))
        lastResult.background = RrUiColors.lastResultTint(latest?.status)
        lastResultCard.background = lastResult.background
        lastResultCard.border =
            JBUI.Borders.compound(
                JBUI.Borders.customLine(RrUiColors.forStatus(latest?.status), 0, 3, 0, 0),
                JBUI.Borders.empty(8, 12, 10, 12),
            )
        applySegments(events, RrConsoleFormatter.events(history.recent()))
        footerLabel.text = "Policy  ·  ${RrHotSwapPolicy.footerText()}"
    }

    private fun consolePane(): JTextPane {
        return JTextPane().apply {
            isEditable = false
            isOpaque = true
            background = RrUiColors.consoleBg
            foreground = RrUiColors.text
            font = consoleFont()
            caret.isVisible = false
            caret.isSelectionVisible = true
        }
    }

    companion object {
        internal fun consoleFont(): Font {
            return try {
                val scheme = EditorColorsManager.getInstance().globalScheme
                scheme.getFont(EditorFontType.CONSOLE_PLAIN) ?: scheme.getFont(EditorFontType.PLAIN)
            } catch (_: Throwable) {
                JBFont.create(Font(Font.MONOSPACED, Font.PLAIN, 13), false)
            }
        }

        internal fun applySegments(
            pane: JTextPane,
            segments: List<RrConsoleFormatter.Segment>,
            font: Font = consoleFont(),
        ) {
            val next = segments.joinToString("") { it.text }
            val scroll = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, pane) as? JScrollPane
            val bar = scroll?.verticalScrollBar
            val decision =
                RrConsoleViewport.decide(
                    currentText = pane.text,
                    nextText = next,
                    scrollValue = bar?.value ?: 0,
                    visibleAmount = bar?.visibleAmount ?: 0,
                    maximum = bar?.maximum ?: 0,
                )
            if (decision.skip) {
                return
            }
            pane.font = font
            val doc = pane.styledDocument
            doc.remove(0, doc.length)
            for (segment in segments) {
                insert(doc, pane, segment, font)
            }
            if (decision.followEnd) {
                pane.caretPosition = pane.document.length
            } else {
                val restore = decision.restoreValue ?: 0
                SwingUtilities.invokeLater {
                    bar?.value = restore
                }
            }
        }

        private fun insert(
            doc: StyledDocument,
            pane: JTextPane,
            segment: RrConsoleFormatter.Segment,
            font: Font,
        ) {
            val style = pane.addStyle(null, null)
            StyleConstants.setFontFamily(style, font.family)
            StyleConstants.setFontSize(style, font.size)
            StyleConstants.setForeground(style, colorFor(segment.kind))
            StyleConstants.setBold(
                style,
                segment.kind == RrConsoleFormatter.Kind.HEADER ||
                    segment.kind == RrConsoleFormatter.Kind.SUCCESS ||
                    segment.kind == RrConsoleFormatter.Kind.ERROR ||
                    segment.kind == RrConsoleFormatter.Kind.WARNING ||
                    segment.kind == RrConsoleFormatter.Kind.PARTIAL,
            )
            doc.insertString(doc.length, segment.text, style)
        }

        internal fun colorFor(kind: RrConsoleFormatter.Kind): java.awt.Color {
            return when (kind) {
                RrConsoleFormatter.Kind.SUCCESS -> RrUiColors.success
                RrConsoleFormatter.Kind.PARTIAL, RrConsoleFormatter.Kind.WARNING -> RrUiColors.partial
                RrConsoleFormatter.Kind.ERROR -> RrUiColors.error
                RrConsoleFormatter.Kind.ACCENT -> RrUiColors.accent
                RrConsoleFormatter.Kind.HEADER -> RrUiColors.header
                RrConsoleFormatter.Kind.TIMESTAMP, RrConsoleFormatter.Kind.META, RrConsoleFormatter.Kind.MUTED ->
                    RrUiColors.muted
                RrConsoleFormatter.Kind.DEFAULT -> RrUiColors.text
            }
        }
    }
}

private class StatusDot : JPanel() {
    var color: java.awt.Color = RrUiColors.muted
        set(value) {
            field = value
            repaint()
        }

    init {
        isOpaque = false
        preferredSize = Dimension(JBUI.scale(10), JBUI.scale(10))
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        g2.fillOval(x, y, size, size)
        g2.dispose()
    }
}

/** Toolbar action that paints its own icon and label so Reload / Attach / Detach / Restart stay distinct. */
private class LabeledToolAction(
    private val delegate: AnAction,
) : AnAction(
        delegate.templatePresentation.text,
        delegate.templatePresentation.description,
        delegate.templatePresentation.icon,
    ),
    CustomComponentAction,
    DumbAware {
    override fun getActionUpdateThread() = delegate.actionUpdateThread

    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
        delegate.actionPerformed(e)
    }

    override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
        delegate.update(e)
        e.presentation.icon = templatePresentation.icon
        e.presentation.text = templatePresentation.text
        e.presentation.description = templatePresentation.description
    }

    override fun createCustomComponent(
        presentation: com.intellij.openapi.actionSystem.Presentation,
        place: String,
    ): javax.swing.JComponent {
        return ActionButtonWithText(this, presentation, place, JBUI.size(22))
    }
}
