package io.runtimerocket.plugin.run

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.sun.tools.attach.VirtualMachine
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrIcons
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrStatus
import io.runtimerocket.plugin.ui.RrUiRefresh
import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.plugin.watch.RrReloadService
import io.runtimerocket.protocol.ReloadResult
import java.time.Duration
import java.time.Instant

class AttachRuntimeRocketAction : AnAction(
    "Attach",
    "Late-attach the RuntimeRocket agent to a running local JVM",
    RrIcons.Attach,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val pid = promptForPid(project) ?: return
        attachInBackground(project, pid)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    companion object {
        internal val handshakeTimeout: Duration = Duration.ofSeconds(30)

        internal fun promptForPid(project: Project): String? {
            val self = ProcessHandle.current().pid().toString()
            val choices =
                try {
                    RrJvmClassifier.chooserLines(RrJvmClassifier.choices(VirtualMachine.list(), self)).toTypedArray()
                } catch (_: Throwable) {
                    emptyArray()
                }
            val initial = RrJvmClassifier.defaultSelection(choices.toList())
            val selected =
                Messages.showEditableChooseDialog(
                    "Pick the application JVM.\nHybris is labeled “Hybris / Tomcat”. IntelliJ and Gradle daemons are hidden.",
                    RrLateAttach.ACTION_TEXT,
                    Messages.getQuestionIcon(),
                    choices,
                    initial,
                    null,
                )
            return selected?.trim()?.takeIf { it.isNotEmpty() }
        }

        internal fun attachInBackground(project: Project, pidRaw: String) {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Attaching RuntimeRocket", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.text = "Loading agent into pid $pidRaw"
                        val result = attachAndHandshake(project, pidRaw)
                        ApplicationManager.getApplication().invokeLater {
                            present(project, result)
                        }
                    }
                },
            )
        }

        internal fun attachAndHandshake(
            project: Project,
            pidRaw: String,
            attachFn: (String) -> RrLateAttach.TargetVm = { pid ->
                object : RrLateAttach.TargetVm {
                    private val vm = VirtualMachine.attach(pid)

                    override fun loadAgent(agentJar: String, args: String) {
                        vm.loadAgent(agentJar, args)
                    }

                    override fun close() {
                        vm.detach()
                    }
                }
            },
            handshakeClient: HandshakeClient = HandshakeClient(),
            timeout: Duration = handshakeTimeout,
        ): RrLateAttach.Result {
            val parsed = RrLateAttach.parsePid(pidRaw)
            if (parsed == null) {
                return RrLateAttach.Result(ok = false, pid = pidRaw.trim(), error = RrLateAttach.invalidPidMessage(pidRaw))
            }
            val pid = parsed.toLong()
            val manager = RrSessionManager.getInstance(project)
            val existingSession = manager.sessionByPid(pid)
            val existingHandshake = handshakeClient.findByPid(pid)
            when (RrAttachStrategy.decide(existingSession != null, existingHandshake != null)) {
                RrAttachStrategy.Path.REUSE_SESSION -> {
                    val session = existingSession!!
                    return if (manager.reconnect(session)) {
                        RrReloadService.getInstance(project).baseline()
                        RrLateAttach.Result(ok = true, pid = parsed, handshake = session.handshake, token = session.token)
                    } else {
                        connectFromHandshake(project, parsed, existingHandshake ?: session.handshake)
                    }
                }
                RrAttachStrategy.Path.RECONNECT_HANDSHAKE -> {
                    return connectFromHandshake(project, parsed, existingHandshake!!)
                }
                RrAttachStrategy.Path.LOAD_AGENT -> {}
            }
            val settings = RrProjectSettings.getInstance(project)
            val launch =
                try {
                    TokenFactory.newLaunch()
                } catch (e: Exception) {
                    return RrLateAttach.Result(
                        ok = false,
                        pid = parsed,
                        error = "Failed to attach RuntimeRocket to pid $parsed: ${describeAttachSetupError(e)}",
                    )
                }
            val agentJar =
                try {
                    AgentJarLocator.ensureUnpacked()
                } catch (e: Exception) {
                    TokenFactory.forget(launch.launchId)
                    return RrLateAttach.Result(
                        ok = false,
                        pid = parsed,
                        error = "Failed to attach RuntimeRocket to pid $parsed: ${e.message ?: e.javaClass.simpleName}",
                    )
                }
            val loaded = RrLateAttach.attach(parsed, agentJar, launch.file, settings.logLevel, attachFn)
            if (!loaded.ok) {
                TokenFactory.forget(launch.launchId)
                val afterFail = handshakeClient.findByPid(pid)
                if (RrAttachStrategy.alreadyStarted(loaded.error) && afterFail != null) {
                    return connectFromHandshake(project, parsed, afterFail)
                }
                if (RrAttachStrategy.shouldReuseExistingHandshake(null, afterFail)) {
                    return connectFromHandshake(project, parsed, afterFail!!)
                }
                return loaded
            }
            val handshake =
                handshakeClient.await(
                    expectedPid = pid,
                    expectedToken = launch.token,
                    startedAfter = launch.createdAt.minusSeconds(2),
                    timeout = timeout,
                )
            if (RrAttachStrategy.shouldReuseExistingHandshake(handshake, handshakeClient.findByPid(pid))) {
                TokenFactory.forget(launch.launchId)
                return connectFromHandshake(project, parsed, handshakeClient.findByPid(pid)!!)
            }
            if (handshake == null) {
                TokenFactory.forget(launch.launchId)
                return RrLateAttach.Result(
                    ok = false,
                    pid = parsed,
                    error = "RuntimeRocket agent loaded but handshake timed out for pid $parsed.",
                )
            }
            return connectFromHandshake(project, parsed, handshake, launch.token)
        }

        internal fun connectFromHandshake(
            project: Project,
            pid: String,
            handshake: HandshakeDocument,
            token: String = handshake.token,
        ): RrLateAttach.Result {
            val session = RrSession(token, handshake, processHandler = null)
            return try {
                val reload = RrReloadService.getInstance(project)
                reload.baseline()
                RrSessionManager.getInstance(project).connect(session)
                RrLateAttach.Result(ok = true, pid = pid, handshake = handshake, token = token)
            } catch (e: Exception) {
                RrLateAttach.Result(
                    ok = false,
                    pid = pid,
                    error = "Handshake failed: ${e.message ?: e.javaClass.simpleName}",
                    handshake = handshake,
                )
            }
        }

        internal fun present(project: Project, result: RrLateAttach.Result) {
            if (!result.ok) {
                val error = result.error?.takeIf { it.isNotBlank() } ?: "Failed to attach RuntimeRocket to pid ${result.pid}."
                applyFailureStatus(project)
                RrNotifier.attachFailed(project, error)
                Messages.showErrorDialog(project, error, RrLateAttach.ACTION_TEXT)
                return
            }
            val handshake = result.handshake
            val backend = handshake?.backend.orEmpty()
            RrStatus.attached(project, backend)
            RrNotifier.attached(project, backend)
            displayLateAttachNotes(project, handshake)
        }

        internal fun applyFailureStatus(project: Project) {
            val manager = RrSessionManager.getInstance(project)
            val backend = failureKeepsAttached(manager.hasActiveSession(), manager.activeSessions().firstOrNull()?.backend)
            if (backend == null) {
                RrStatus.notAttached(project)
            } else {
                RrStatus.attached(project, backend)
            }
        }

        internal fun failureKeepsAttached(hasActiveSession: Boolean, existingBackend: String?): String? {
            return if (hasActiveSession) existingBackend.orEmpty() else null
        }

        internal fun describeAttachSetupError(error: Exception): String {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            return if (error is java.nio.file.FileAlreadyExistsException) {
                "cannot create $detail (a file or Windows junction is already at that path)"
            } else {
                "${error.javaClass.simpleName}: $detail"
            }
        }

        internal fun displayLateAttachNotes(project: Project, handshake: HandshakeDocument?): LateAttachNotes.Display? {
            val decision = LateAttachNotes.displayDecision(handshake) ?: return null
            RrNotifier.lateAttachSpringInactive(project, decision.balloon)
            RrReloadHistory.getInstance(project).record(
                RrReloadHistory.Event(
                    time = Instant.now(),
                    classCount = 0,
                    resourceCount = 0,
                    status = decision.status,
                    durationMs = 0,
                    latencyMs = 0,
                    message = decision.historyMessage,
                    classes = emptyList(),
                    adapters = emptyList(),
                    trigger = "late-attach",
                ),
            )
            project.messageBus.syncPublisher(RrUiRefresh.TOPIC).refresh()
            return decision
        }
    }
}
