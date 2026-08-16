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
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrStatus
import io.runtimerocket.plugin.ui.RrUiRefresh
import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.plugin.watch.RrReloadService
import io.runtimerocket.protocol.ReloadResult
import java.time.Duration
import java.time.Instant

class AttachRuntimeRocketAction : AnAction(RrLateAttach.ACTION_TEXT), DumbAware {
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
                    VirtualMachine.list()
                        .filter { it.id() != self }
                        .map { RrLateAttach.formatVmChoice(it) }
                        .toTypedArray()
                } catch (_: Throwable) {
                    emptyArray()
                }
            val initial = choices.firstOrNull().orEmpty()
            val selected =
                Messages.showEditableChooseDialog(
                    "Select a local JVM or enter a process id",
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
            val settings = RrProjectSettings.getInstance(project)
            val launch = TokenFactory.newLaunch()
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
                return loaded
            }
            val handshake =
                handshakeClient.await(
                    expectedPid = parsed.toLong(),
                    expectedToken = launch.token,
                    startedAfter = launch.createdAt.minusSeconds(2),
                    timeout = timeout,
                )
            if (handshake == null) {
                TokenFactory.forget(launch.launchId)
                return RrLateAttach.Result(
                    ok = false,
                    pid = parsed,
                    error = "RuntimeRocket agent loaded but handshake timed out for pid $parsed.",
                )
            }
            val session = RrSession(launch.token, handshake, processHandler = null)
            return try {
                val manager = RrSessionManager.getInstance(project)
                manager.connect(session)
                RrReloadService.getInstance(project).baseline()
                RrLateAttach.Result(ok = true, pid = parsed, handshake = handshake, token = launch.token)
            } catch (e: Exception) {
                TokenFactory.forget(launch.launchId)
                RrLateAttach.Result(
                    ok = false,
                    pid = parsed,
                    error = "Handshake failed: ${e.message ?: e.javaClass.simpleName}",
                    handshake = handshake,
                )
            }
        }

        internal fun present(project: Project, result: RrLateAttach.Result) {
            if (!result.ok) {
                val error = result.error?.takeIf { it.isNotBlank() } ?: "Failed to attach RuntimeRocket to pid ${result.pid}."
                RrStatus.notAttached(project)
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

        internal fun displayLateAttachNotes(project: Project, handshake: HandshakeDocument?) {
            val note = LateAttachNotes.springInactive(LateAttachNotes.collect(handshake = handshake)) ?: return
            RrNotifier.lateAttachSpringInactive(project, note)
            RrReloadHistory.getInstance(project).record(
                RrReloadHistory.Event(
                    time = Instant.now(),
                    classCount = 0,
                    resourceCount = 0,
                    status = ReloadResult.PARTIAL,
                    durationMs = 0,
                    latencyMs = 0,
                    message = note,
                    classes = emptyList(),
                    adapters = emptyList(),
                    trigger = "late-attach",
                ),
            )
            project.messageBus.syncPublisher(RrUiRefresh.TOPIC).refresh()
        }
    }
}
