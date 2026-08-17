package io.runtimerocket.plugin.watch

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Incremental module build that prefers the same path as Build | Build Module. */
internal object RrModuleBuilder {
    data class Outcome(
        val ok: Boolean,
        val errors: Int = 0,
        val aborted: Boolean = false,
        val timedOut: Boolean = false,
        val message: String? = null,
    )

    fun make(
        project: Project,
        module: Module?,
        includeDependents: Boolean,
        preferDelegated: Boolean,
    ): Outcome {
        if (module == null) {
            return Outcome(ok = false, message = "no module in focus")
        }
        if (preferDelegated) {
            val delegated = delegatedBuild(project, module)
            if (delegated != null) {
                return delegated
            }
        }
        return jpsMake(project, module, includeDependents)
    }

    private fun delegatedBuild(project: Project, module: Module): Outcome? {
        return try {
            val latch = CountDownLatch(1)
            val box = AtomicReference<Outcome>()
            val run = {
                ProjectTaskManager.getInstance(project).build(module).onProcessed { result ->
                    val errors = if (result?.hasErrors() == true) 1 else 0
                    box.set(
                        Outcome(
                            ok = result != null && !result.hasErrors() && !result.isAborted,
                            errors = errors,
                            aborted = result?.isAborted == true,
                            message = if (result?.hasErrors() == true) "delegated build failed" else null,
                        ),
                    )
                    latch.countDown()
                }
                Unit
            }
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) {
                run()
            } else {
                app.invokeLater(run)
            }
            if (!latch.await(3, TimeUnit.MINUTES)) {
                return Outcome(ok = false, timedOut = true, message = "build timed out")
            }
            box.get() ?: Outcome(ok = false, message = "build produced no result")
        } catch (_: Throwable) {
            null
        }
    }

    private fun jpsMake(project: Project, module: Module, includeDependents: Boolean): Outcome {
        val latch = CountDownLatch(1)
        val box = AtomicReference<Outcome>()
        val compiler = CompilerManager.getInstance(project)
        val scope = compiler.createModulesCompileScope(arrayOf(module), includeDependents)
        val run = {
            compiler.make(scope) { aborted, errors, _, _ ->
                box.set(
                    Outcome(
                        ok = !aborted && errors == 0,
                        errors = errors,
                        aborted = aborted,
                        message =
                            when {
                                aborted -> "compile aborted"
                                errors > 0 -> "compile failed ($errors error(s))"
                                else -> null
                            },
                    ),
                )
                latch.countDown()
            }
            Unit
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            run()
        } else {
            app.invokeLater(run)
        }
        if (!latch.await(3, TimeUnit.MINUTES)) {
            return Outcome(ok = false, timedOut = true, message = "compile timed out")
        }
        return box.get() ?: Outcome(ok = false, message = "compile produced no result")
    }
}
