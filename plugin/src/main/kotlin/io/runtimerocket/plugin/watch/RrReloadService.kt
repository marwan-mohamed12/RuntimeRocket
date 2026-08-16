package io.runtimerocket.plugin.watch

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import io.runtimerocket.plugin.run.RrSessionManager
import io.runtimerocket.plugin.settings.RrApplicationSettings
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrReloadPresenter
import io.runtimerocket.plugin.ui.RrStatus
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ReloadResult
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class RrReloadService(private val project: Project) {
    private val snapshot = OutputSnapshot()
    private val snapshotted = AtomicBoolean(false)
    private val forceAfterCompile = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)
    private val pendingTrigger = AtomicReference<String?>(null)
    private val debounceLock = Any()
    private var gradleScanTask: ScheduledFuture<*>? = null
    internal val compileCycle = RrCompileCycle()
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "rr-reload").apply { isDaemon = true }
        }

    fun baseline(context: CompileContext? = null) {
        val located = currentLocated(context)
        snapshot.baseline(located.roots)
        snapshotted.set(true)
        noteMissing(located)
    }

    fun onCompileFinished(aborted: Boolean, errors: Int, context: CompileContext) {
        val failed = aborted || errors > 0
        compileCycle.onCompileFinished(failed)
        cancelScheduledWork()
        if (failed) {
            forceAfterCompile.set(false)
            RrStatus.idle(project, "Compile failed — nothing reloaded")
            return
        }
        val force = forceAfterCompile.getAndSet(false)
        val settings = RrProjectSettings.getInstance(project)
        if (!settings.autoReloadOnSuccessfulCompile && !force) {
            return
        }
        val trigger = if (force) ReloadRequest.TRIGGER_MANUAL else ReloadRequest.TRIGGER_COMPILE
        compileCycle.markReloadStarted()
        executor.execute { pushDiff(context, trigger, startedAt = System.nanoTime()) }
    }

    fun onOutputChanged(paths: Collection<Path>) {
        // Gradle writes during compile are discarded here on purpose. When the
        // compile listener does not fire, ARM_VFS scans outputs after a settle delay.
        if (paths.isEmpty() || !compileCycle.shouldScheduleVfs()) {
            return
        }
    }

    fun reloadNow(file: VirtualFile?) {
        val compiler = CompilerManager.getInstance(project)
        val module = file?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val scope =
            if (module != null) {
                compiler.createModulesCompileScope(arrayOf(module), false)
            } else {
                compiler.createProjectCompileScope(project)
            }
        if (!compiler.isUpToDate(scope)) {
            forceAfterCompile.set(true)
            compiler.compile(scope, null)
            return
        }
        compileCycle.markReloadStarted()
        executor.execute { pushDiff(context = null, trigger = ReloadRequest.TRIGGER_MANUAL, startedAt = System.nanoTime()) }
    }

    fun installBuildListener() {
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            BuildManagerListener.TOPIC,
            object : BuildManagerListener {
                override fun buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean) {
                    if (project != this@RrReloadService.project) {
                        return
                    }
                    compileCycle.onBuildStarted()
                    cancelScheduledWork()
                    RrStatus.compiling(project)
                }

                override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
                    if (project != this@RrReloadService.project) {
                        return
                    }
                    when (compileCycle.onBuildFinished()) {
                        RrCompileCycle.BuildFinish.RESTORE_IDLE -> restoreAfterBuild()
                        RrCompileCycle.BuildFinish.ARM_VFS -> armGradleVfsWindow()
                        RrCompileCycle.BuildFinish.NOTHING -> {}
                    }
                }
            },
        )
    }

    private fun armGradleVfsWindow() {
        if (!RrProjectSettings.getInstance(project).autoReloadOnSuccessfulCompile) {
            compileCycle.disarmVfs()
            restoreAfterBuild()
            return
        }
        synchronized(debounceLock) {
            gradleScanTask?.cancel(false)
            gradleScanTask =
                executor.schedule(
                    {
                        compileCycle.markReloadStarted()
                        compileCycle.disarmVfs()
                        pushDiff(context = null, trigger = ReloadRequest.TRIGGER_COMPILE, startedAt = System.nanoTime())
                    },
                    VFS_ARM_MS,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    private fun restoreAfterBuild() {
        val phase = RrStatus.get(project).phase
        if (phase == RrStatus.Phase.COMPILING) {
            restoreIdle()
        }
    }

    private fun pushDiff(context: CompileContext?, trigger: String, startedAt: Long) {
        if (!inFlight.compareAndSet(false, true)) {
            pending.set(true)
            pendingTrigger.set(trigger)
            return
        }
        try {
            do {
                pending.set(false)
                val usedTrigger = pendingTrigger.getAndSet(null) ?: trigger
                runPush(context, usedTrigger, startedAt)
            } while (pending.getAndSet(false))
        } finally {
            inFlight.set(false)
            if (pending.getAndSet(false)) {
                val retryTrigger = pendingTrigger.getAndSet(null) ?: trigger
                executor.execute { pushDiff(context, retryTrigger, System.nanoTime()) }
            }
        }
    }

    private fun runPush(context: CompileContext?, trigger: String, startedAt: Long) {
        try {
            val located = currentLocated(context)
            if (!snapshotted.get()) {
                snapshot.baseline(located.roots)
                snapshotted.set(true)
            }
            noteMissing(located)
            val peek = snapshot.peek(located.roots)
            if (peek.diff.isEmpty()) {
                restoreIdle()
                return
            }
            if (!RrSessionManager.getInstance(project).hasActiveSession()) {
                RrStatus.notAttached(project)
                RrNotifier.notAttachedCompile(project)
                return
            }
            val request = ReloadRequestFactory.fromDiff(peek.diff, trigger)
            send(request, peek, startedAt)
        } catch (e: Exception) {
            publishFailure("reload failed: ${e.message ?: e.javaClass.simpleName}", startedAt)
        }
    }

    private fun send(request: ReloadRequest, peek: OutputSnapshot.Peek, startedAt: Long) {
        RrStatus.reloading(project, request.classes?.size ?: 0)
        val results =
            try {
                RrSessionManager.getInstance(project).sendReload(request)
            } catch (e: Exception) {
                publishFailure("agent communication failed: ${e.message ?: e.javaClass.simpleName}", startedAt)
                return
            }
        val result = merge(results)
        val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (applied(result.status)) {
            snapshot.commit(peek.fingerprints)
            history().clearGutter(peek.diff.classes.map { it.binaryName })
        }
        record(result, request, latencyMs)
        render(result, latencyMs, request.classes?.size ?: peek.diff.classes.size)
    }

    private fun applied(status: String?): Boolean {
        return status == ReloadResult.SUCCESS || status == ReloadResult.PARTIAL
    }

    private fun merge(results: List<ReloadResult>): ReloadResult {
        if (results.isEmpty()) {
            val empty = ReloadResult()
            empty.status = ReloadResult.FAILED
            empty.message = "no attached session"
            return empty
        }
        val worst = results.maxBy { rank(it.status) }
        if (results.size == 1) {
            return worst
        }
        val merged = ReloadResult()
        merged.status = worst.status
        merged.durationMs = results.maxOf { it.durationMs }
        merged.message = worst.message
        merged.classes = results.flatMap { it.classes ?: emptyList() }
        merged.adapters = results.flatMap { it.adapters ?: emptyList() }
        return merged
    }

    private fun rank(status: String?): Int {
        return when (status) {
            ReloadResult.SUCCESS -> 0
            ReloadResult.PARTIAL -> 1
            ReloadResult.FAILED -> 2
            ReloadResult.RESTART_REQUIRED -> 3
            else -> 2
        }
    }

    private fun record(result: ReloadResult, request: ReloadRequest, latencyMs: Long) {
        history().record(
            RrReloadHistory.Event(
                time = Instant.now(),
                classCount = request.classes?.size ?: 0,
                resourceCount = request.resources?.size ?: 0,
                status = result.status ?: ReloadResult.FAILED,
                durationMs = result.durationMs,
                latencyMs = latencyMs,
                message = result.message,
                classes = result.classes ?: emptyList(),
                adapters = result.adapters ?: emptyList(),
                trigger = request.trigger ?: "",
            ),
        )
        ApplicationManager.getApplication().invokeLater {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    private fun render(result: ReloadResult, latencyMs: Long, classCount: Int) {
        val decision =
            RrReloadPresenter.present(
                result,
                reloadLatencyMs = latencyMs,
                showSuccessBalloon = RrApplicationSettings.getInstance().showSuccessBalloons,
                classCount = classCount,
            )
        when (result.status) {
            ReloadResult.SUCCESS -> RrStatus.success(project, latencyMs)
            ReloadResult.PARTIAL -> RrStatus.partial(project, latencyMs)
            ReloadResult.RESTART_REQUIRED -> RrStatus.restartRequired(project)
            else -> RrStatus.failed(project)
        }
        RrNotifier.reloadResult(project, decision)
    }

    private fun publishFailure(message: String, startedAt: Long) {
        val result = ReloadResult()
        result.status = ReloadResult.FAILED
        result.message = message
        val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        val request = ReloadRequest()
        request.trigger = ReloadRequest.TRIGGER_COMPILE
        record(result, request, latencyMs)
        render(result, latencyMs, 0)
    }

    private fun restoreIdle() {
        val sessions = RrSessionManager.getInstance(project).activeSessions()
        if (sessions.isEmpty()) {
            RrStatus.notAttached(project)
        } else {
            RrStatus.attached(project, sessions.first().backend)
        }
    }

    private fun currentLocated(context: CompileContext?): ModuleOutputLocator.LocatedOutputs {
        val includeTests = RrProjectSettings.getInstance(project).includeTests
        return ReadAction.compute<ModuleOutputLocator.LocatedOutputs, RuntimeException> {
            ModuleOutputLocator.locate(project, context, includeTests)
        }
    }

    private fun noteMissing(located: ModuleOutputLocator.LocatedOutputs) {
        history().lastMissingOutput = ModuleOutputLocator.formatMissingOutput(located)
    }

    private fun cancelScheduledWork() {
        synchronized(debounceLock) {
            gradleScanTask?.cancel(false)
            gradleScanTask = null
        }
    }

    private fun history(): RrReloadHistory = RrReloadHistory.getInstance(project)

    companion object {
        const val VFS_ARM_MS = 400L

        fun getInstance(project: Project): RrReloadService {
            return project.getService(RrReloadService::class.java)
        }
    }
}
