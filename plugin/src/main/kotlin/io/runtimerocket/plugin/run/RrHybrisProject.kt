package io.runtimerocket.plugin.run

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** Detects an SAP Commerce (Hybris) workspace and its handshake / output locations. */
object RrHybrisProject {
    private val MARKERS =
        listOf(
            Path.of("config", "localextensions.xml"),
            Path.of("hybris", "config", "localextensions.xml"),
            Path.of("core-customize", "hybris", "config", "localextensions.xml"),
            Path.of("hybris", "bin", "platform", "build.xml"),
            Path.of("bin", "platform", "build.xml"),
            Path.of("extensioninfo.xml"),
        )

    private val HANDSHAKE_RELATIVE =
        listOf(
            Path.of("hybris", "temp", "hybris", "runtimerocket"),
            Path.of("temp", "hybris", "runtimerocket"),
            Path.of("core-customize", "hybris", "temp", "hybris", "runtimerocket"),
            Path.of("hybris", "temp", "runtimerocket"),
        )

    fun isHybris(project: Project): Boolean {
        val base = project.basePath ?: return false
        return isHybrisRoot(Path.of(base))
    }

    fun isHybrisRoot(root: Path): Boolean {
        return MARKERS.any { Files.isRegularFile(root.resolve(it)) }
    }

    fun handshakeDirs(project: Project): List<Path> {
        val base = project.basePath ?: return emptyList()
        return handshakeDirs(Path.of(base))
    }

    fun handshakeDirs(root: Path): List<Path> {
        return HANDSHAKE_RELATIVE.map { root.resolve(it) }.filter { Files.isDirectory(it) }
    }
}
