package io.runtimerocket.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.runtimerocket.plugin.run.RrHotSwapPolicy

class RrStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        RrHotSwapPolicy.install(project)
    }
}
