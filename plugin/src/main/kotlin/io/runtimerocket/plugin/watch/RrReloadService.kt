package io.runtimerocket.plugin.watch

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import io.runtimerocket.plugin.run.RrSessionManager
import io.runtimerocket.plugin.settings.RrApplicationSettings
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrReloadPresenter
import io.runtimerocket.plugin.ui.RrStatus
import io.runtimerocket.plugin.ui.RrUiRefresh
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ReloadResult
import java.nio.file.Files
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
    private val suppressAutoReload = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)
    private val pendingTrigger = AtomicReference<String?>(null)
    private val debounceLock = Any()
    private var gradleScanTask: ScheduledFuture<*>? = null
    private var watchTask: ScheduledFuture<*>? = null
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
            keepAttachedAfterCompileFailure()
            return
        }
        if (suppressAutoReload.get()) {
            restoreAfterBuild()
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
        if (paths.isEmpty() || compileCycle.compiling || !RrSessionManager.getInstance(project).hasActiveSession()) {
            return
        }
        if (!RrProjectSettings.getInstance(project).autoReloadOnSuccessfulCompile) {
            return
        }
        scheduleWatchScan()
    }

    fun reloadNow(file: VirtualFile?, moduleHint: Module? = null) {
        executor.execute { runOrchestratedReload(file, moduleHint) }
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
                    when (compileCycle.onBuildFinished(suppressAutoReload.get())) {
                        RrCompileCycle.BuildFinish.RESTORE_IDLE -> restoreAfterBuild()
                        RrCompileCycle.BuildFinish.ARM_VFS -> armGradleVfsWindow()
                        RrCompileCycle.BuildFinish.NOTHING -> {}
                    }
                }
            },
        )
        startOutputWatch()
    }

    internal fun runOrchestratedReload(file: VirtualFile?, moduleHint: Module? = null) {
        val history = history()
        history.beginSteps()
        val modules = resolveModulesForReload(file, moduleHint)
        saveDocuments()
        refreshOutputRoots()

        var step = RrReloadPlan.Step.HOT_RELOAD
        var includeDependents = false
        var preferDelegated = modules.isNotEmpty() && modules.all { ModuleOutputLocator.isGradle(it) }
        while (step != RrReloadPlan.Step.DONE) {
            history.addStep(RrReloadPlan.stepTitle(step))
            publishSteps()
            val outcome =
                when (step) {
                    RrReloadPlan.Step.HOT_RELOAD -> {
                        val reloaded = hotReload()
                        if (reloaded == RrReloadPlan.Outcome.EMPTY && modules.isEmpty()) {
                            stopWithoutModule(history)
                        } else {
                            reloaded
                        }
                    }
                    RrReloadPlan.Step.BUILD -> {
                        if (modules.isEmpty()) {
                            stopWithoutModule(history)
                        } else {
                            val built = buildModule(modules, includeDependents, preferDelegated)
                            if (!built.ok) {
                                history.addStep("   ${built.message ?: "compile failed"}")
                                publishSteps()
                                if (built.timedOut) {
                                    recordCompileFailure(built.message ?: "compile timed out")
                                    restoreIdle()
                                    RrReloadPlan.Outcome.TIMED_OUT
                                } else {
                                    RrReloadPlan.Outcome.COMPILE_FAILED
                                }
                            } else {
                                refreshOutputRoots()
                                hotReload()
                            }
                        }
                    }
                    RrReloadPlan.Step.DIAGNOSE -> {
                        if (modules.isEmpty()) {
                            stopWithoutModule(history)
                        } else {
                            saveDocuments()
                            refreshOutputRoots()
                            includeDependents = true
                            preferDelegated = true
                            val built = buildModule(modules, includeDependents = true, preferDelegated = true)
                            if (!built.ok) {
                                history.addStep("   ${built.message ?: "still failing"}")
                                publishSteps()
                                recordCompileFailure(built.message ?: "compile still has errors")
                                restoreIdle()
                                if (built.timedOut) {
                                    RrReloadPlan.Outcome.TIMED_OUT
                                } else {
                                    RrReloadPlan.Outcome.REAL_ERRORS
                                }
                            } else {
                                refreshOutputRoots()
                                hotReload()
                            }
                        }
                    }
                    RrReloadPlan.Step.DONE -> RrReloadPlan.Outcome.SUCCESS
                }
            val reason = RrReloadPlan.stopReason(outcome)
            if (reason != null) {
                history.addStep("   $reason")
                publishSteps()
            }
            step = RrReloadPlan.next(step, outcome)
        }
    }

    private fun stopWithoutModule(history: RrReloadHistory): RrReloadPlan.Outcome {
        history.addStep("   ${RrReloadPlan.NO_MODULE_DETAIL}")
        publishSteps()
        recordCompileFailure(RrReloadPlan.NO_MODULE_DETAIL)
        restoreIdle()
        return RrReloadPlan.Outcome.NO_MODULE
    }

    /**
     * Figures out which module(s) to build without requiring a focused editor tab.
     *
     * Priority: (1) an explicit module/file hint, including the selected editor; (2) modules
     * that own an unsaved document; (3) modules that own a VCS-modified file; (4) modules that
     * own any currently open editor file. If none of that resolves anything, every module in
     * the project is returned so Reload can still compile.
     */
    private fun resolveModulesForReload(hintFile: VirtualFile?, moduleHint: Module?): List<Module> {
        val hint =
            moduleHint
                ?: RrEditorFocus.preferredFile(project, hintFile)?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val unsaved = LinkedHashSet<Module>()
        ReadAction.compute<Unit, RuntimeException> {
            FileDocumentManager.getInstance().unsavedDocuments.forEach { document ->
                val vf = FileDocumentManager.getInstance().getFile(document) ?: return@forEach
                ModuleUtilCore.findModuleForFile(vf, project)?.let { unsaved.add(it) }
            }
        }
        val vcs = LinkedHashSet<Module>()
        try {
            ChangeListManager.getInstance(project).affectedFiles.forEach { vf ->
                ModuleUtilCore.findModuleForFile(vf, project)?.let { vcs.add(it) }
            }
        } catch (_: Exception) {
            // VCS not configured / not ready — fall through to open editors.
        }
        val open = LinkedHashSet<Module>()
        FileEditorManager.getInstance(project).openFiles.forEach { vf ->
            ModuleUtilCore.findModuleForFile(vf, project)?.let { open.add(it) }
        }
        return RrReloadTargets.pick(
            hint,
            unsaved,
            vcs,
            open,
            ModuleManager.getInstance(project).modules.toList(),
        )
    }

    private fun hotReload(): RrReloadPlan.Outcome {
        val manager = RrSessionManager.getInstance(project)
        if (!manager.hasActiveSession()) {
            RrStatus.notAttached(project)
            RrNotifier.notAttachedCompile(project)
            return RrReloadPlan.Outcome.NOT_ATTACHED
        }
        val startedAt = System.nanoTime()
        val result = runPush(context = null, trigger = ReloadRequest.TRIGGER_MANUAL, startedAt = startedAt)
        return when {
            result == null -> RrReloadPlan.Outcome.EMPTY
            else ->
                RrReloadPlan.outcomeOf(
                    result.status,
                    emptyDiff = false,
                    attached = manager.hasActiveSession(),
                )
        }
    }

    private fun buildModule(modules: List<Module>, includeDependents: Boolean, preferDelegated: Boolean): RrModuleBuilder.Outcome {
        suppressAutoReload.set(true)
        return try {
            RrModuleBuilder.make(project, modules, includeDependents, preferDelegated)
        } finally {
            suppressAutoReload.set(false)
            compileCycle.releaseCompileLock()
            restoreAfterBuild()
        }
    }

    private fun armGradleVfsWindow() {
        if (!RrProjectSettings.getInstance(project).autoReloadOnSuccessfulCompile) {
            compileCycle.disarmVfs()
            restoreAfterBuild()
            return
        }
        scheduleWatchScan(VFS_ARM_MS)
    }

    private fun startOutputWatch() {
        synchronized(debounceLock) {
            if (watchTask != null) {
                return
            }
            watchTask =
                executor.scheduleWithFixedDelay(
                    { scanOutputsIfIdle(ReloadRequest.TRIGGER_WATCH) },
                    WATCH_PERIOD_MS,
                    WATCH_PERIOD_MS,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    private fun scheduleWatchScan(delayMs: Long = WATCH_DEBOUNCE_MS) {
        synchronized(debounceLock) {
            gradleScanTask?.cancel(false)
            gradleScanTask =
                executor.schedule(
                    { scanOutputsIfIdle(ReloadRequest.TRIGGER_WATCH) },
                    delayMs,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    private fun scanOutputsIfIdle(trigger: String) {
        if (compileCycle.compiling || suppressAutoReload.get() || inFlight.get()) {
            return
        }
        if (!RrSessionManager.getInstance(project).hasActiveSession()) {
            return
        }
        if (!RrProjectSettings.getInstance(project).autoReloadOnSuccessfulCompile) {
            return
        }
        compileCycle.markReloadStarted()
        compileCycle.disarmVfs()
        pushDiff(context = null, trigger = trigger, startedAt = System.nanoTime())
    }

    private fun restoreAfterBuild() {
        val phase = RrStatus.get(project).phase
        if (phase == RrStatus.Phase.COMPILING) {
            restoreIdle()
        }
    }

    private fun keepAttachedAfterCompileFailure() {
        history().addStep("Compile failed — nothing reloaded")
        recordCompileFailure("Compile failed — nothing reloaded")
        restoreIdle()
        publishSteps()
    }

    private fun recordCompileFailure(message: String) {
        history().record(
            RrReloadHistory.Event(
                time = Instant.now(),
                classCount = 0,
                resourceCount = 0,
                status = ReloadResult.FAILED,
                durationMs = 0,
                latencyMs = 0,
                message = message,
                classes = emptyList(),
                adapters = emptyList(),
                trigger = ReloadRequest.TRIGGER_MANUAL,
            ),
        )
        ApplicationManager.getApplication().invokeLater {
            EditorNotifications.getInstance(project).updateAllNotifications()
            project.messageBus.syncPublisher(RrUiRefresh.TOPIC).refresh()
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

    private fun runPush(context: CompileContext?, trigger: String, startedAt: Long): ReloadResult? {
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
                return null
            }
            if (!RrSessionManager.getInstance(project).hasActiveSession()) {
                RrStatus.notAttached(project)
                RrNotifier.notAttachedCompile(project)
                return failedResult("no attached session")
            }
            val request = ReloadRequestFactory.fromDiff(peek.diff, trigger)
            return send(request, peek, startedAt)
        } catch (e: Exception) {
            publishFailure("reload failed: ${e.message ?: e.javaClass.simpleName}", startedAt)
            return failedResult(e.message)
        }
    }

    private fun send(request: ReloadRequest, peek: OutputSnapshot.Peek, startedAt: Long): ReloadResult {
        RrStatus.reloading(project, request.classes?.size ?: 0)
        val results =
            try {
                RrSessionManager.getInstance(project).sendReload(request)
            } catch (e: Exception) {
                publishFailure("agent communication failed: ${e.message ?: e.javaClass.simpleName}", startedAt)
                return failedResult(e.message)
            }
        val result = merge(results)
        val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (applied(result.status)) {
            snapshot.commit(peek.fingerprints)
            history().clearGutter(peek.diff.classes.map { it.binaryName })
        }
        record(result, request, latencyMs)
        render(result, latencyMs, request.classes?.size ?: peek.diff.classes.size)
        return result
    }

    private fun applied(status: String?): Boolean {
        return status == ReloadResult.SUCCESS || status == ReloadResult.PARTIAL
    }

    private fun merge(results: List<ReloadResult>): ReloadResult {
        if (results.isEmpty()) {
            return failedResult("no attached session")
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
            else -> {
                if (RrSessionManager.getInstance(project).hasActiveSession()) {
                    RrStatus.failed(project)
                } else {
                    RrStatus.notAttached(project)
                }
            }
        }
        RrNotifier.reloadResult(project, decision)
    }

    private fun publishFailure(message: String, startedAt: Long) {
        val result = failedResult(message)
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
        val settings = RrProjectSettings.getInstance(project)
        val located =
            ReadAction.compute<ModuleOutputLocator.LocatedOutputs, RuntimeException> {
                ModuleOutputLocator.locate(project, context, settings.includeTests)
            }
        val extras =
            settings.extraWatchDirs.mapNotNull { raw ->
                val path = Path.of(raw.trim()).toAbsolutePath().normalize()
                if (Files.isDirectory(path)) OutputRoot(path, "extra") else null
            }
        if (extras.isEmpty()) {
            return located
        }
        return ModuleOutputLocator.LocatedOutputs(located.roots + extras, located.missingModules)
    }

    private fun noteMissing(located: ModuleOutputLocator.LocatedOutputs) {
        history().lastMissingOutput = ModuleOutputLocator.formatMissingOutput(located)
    }

    private fun saveDocuments() {
        val app = ApplicationManager.getApplication()
        val run = { FileDocumentManager.getInstance().saveAllDocuments() }
        if (app.isDispatchThread) {
            run()
        } else {
            app.invokeAndWait(run)
        }
    }

    private fun refreshOutputRoots() {
        val roots = currentLocated(null).roots
        for (root in roots) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root.path.toFile())?.refresh(false, true)
        }
    }

    private fun publishSteps() {
        ApplicationManager.getApplication().invokeLater {
            project.messageBus.syncPublisher(RrUiRefresh.TOPIC).refresh()
        }
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
        const val WATCH_DEBOUNCE_MS = 400L
        const val WATCH_PERIOD_MS = 1_500L

        fun getInstance(project: Project): RrReloadService {
            return project.getService(RrReloadService::class.java)
        }

        internal fun failedResult(message: String?): ReloadResult {
            val result = ReloadResult()
            result.status = ReloadResult.FAILED
            result.message = message
            return result
        }
    }
}
