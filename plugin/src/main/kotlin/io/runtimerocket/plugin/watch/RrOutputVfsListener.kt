package io.runtimerocket.plugin.watch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.runtimerocket.plugin.settings.RrProjectSettings
import java.nio.file.Path

class RrOutputVfsListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val settings = RrProjectSettings.getInstance(project)
        val roots = ModuleOutputLocator.paths(project, context = null, includeTests = settings.includeTests)
        if (roots.isEmpty()) {
            return
        }
        val normalizedRoots = roots.map { it.path.toAbsolutePath().normalize() }
        val changed = LinkedHashSet<Path>()
        for (event in events) {
            val path = event.path
            if (path.isBlank()) {
                continue
            }
            val nio = Path.of(path).toAbsolutePath().normalize()
            if (normalizedRoots.any { nio.startsWith(it) } &&
                (OutputSnapshot.isClassFile(nio) || OutputSnapshot.isWatchedResource(nio))
            ) {
                changed.add(nio)
            }
        }
        if (changed.isNotEmpty()) {
            RrReloadService.getInstance(project).onOutputChanged(changed)
        }
    }
}
