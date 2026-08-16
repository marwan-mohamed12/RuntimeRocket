package io.runtimerocket.plugin.run

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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

    internal fun sameContent(left: Path, right: Path): Boolean {
        return digest(left).contentEquals(digest(right))
    }

    private fun digest(path: Path): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) {
                    break
                }
                md.update(buf, 0, n)
            }
        }
        return md.digest()
    }
}
