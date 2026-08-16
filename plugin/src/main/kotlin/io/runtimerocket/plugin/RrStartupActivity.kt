package io.runtimerocket.plugin

import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import io.runtimerocket.plugin.run.RrHotSwapPolicy
import io.runtimerocket.plugin.watch.RrCompilationStatusListener
import io.runtimerocket.plugin.watch.RrOutputVfsListener
import io.runtimerocket.plugin.watch.RrReloadService

class RrStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        RrHotSwapPolicy.install(project)
        RrReloadService.getInstance(project).installBuildListener()
        val compileListener = RrCompilationStatusListener()
        val compilerManager = CompilerManager.getInstance(project)
        compilerManager.addCompilationStatusListener(compileListener)
        Disposer.register(project) {
            compilerManager.removeCompilationStatusListener(compileListener)
        }
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, RrOutputVfsListener(project))
    }
}
