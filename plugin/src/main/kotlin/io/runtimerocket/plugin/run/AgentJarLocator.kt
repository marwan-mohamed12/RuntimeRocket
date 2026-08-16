package io.runtimerocket.plugin.run

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object AgentJarLocator {
    const val RESOURCE = "/io/runtimerocket/plugin/agent/runtimerocket-agent.jar"

    @Synchronized
    fun ensureUnpacked(): Path {
        val dest = Path.of(PathManager.getSystemPath(), "runtimerocket", "agent", "runtimerocket-agent.jar")
        return unpackTo(dest)
    }

    fun unpackTo(destination: Path, resource: String = RESOURCE): Path {
        Files.createDirectories(destination.parent)
        val stream =
            AgentJarLocator::class.java.getResourceAsStream(resource)
                ?: throw IllegalStateException("bundled agent missing: $resource")
        stream.use { input ->
            val tmp = destination.resolveSibling(destination.fileName.toString() + ".tmp")
            try {
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
                if (Files.exists(destination) && sameContent(tmp, destination)) {
                    Files.deleteIfExists(tmp)
                    return destination
                }
                try {
                    Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
        return destination
    }

    private fun sameContent(left: Path, right: Path): Boolean {
        return Files.size(left) == Files.size(right)
    }
}
