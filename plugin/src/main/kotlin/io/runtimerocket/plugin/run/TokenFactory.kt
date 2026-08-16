package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Key
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Session tokens live in a 0600 file. The raw token is never placed on the command line. */
object TokenFactory {
    private const val TOKEN_BYTES = 32
    const val ENV_LAUNCH = "RUNTIMEROCKET_LAUNCH"
    val LAUNCH_KEY: Key<String> = Key.create("io.runtimerocket.launchId")

    data class SessionToken(
        val launchId: String,
        val token: String,
        val file: Path,
        val createdAt: Instant,
    )

    private val byLaunchId = ConcurrentHashMap<String, SessionToken>()

    fun generate(): String {
        val raw = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(raw)
        return HexFormat.of().formatHex(raw)
    }

    fun newLaunch(directory: Path = tokenDirectory()): SessionToken {
        val token = generate()
        Files.createDirectories(directory)
        RestrictedFiles.restrict(directory)
        val file = writeTokenFile(token, directory)
        val session = SessionToken(UUID.randomUUID().toString(), token, file, Instant.now())
        byLaunchId[session.launchId] = session
        return session
    }

    fun putOnParameters(params: JavaParameters, launch: SessionToken) {
        params.addEnv(ENV_LAUNCH, launch.launchId)
    }

    fun writeTokenFile(token: String, directory: Path): Path {
        Files.createDirectories(directory)
        RestrictedFiles.restrict(directory)
        val file = directory.resolve(UUID.randomUUID().toString() + ".token")
        try {
            Files.writeString(file, token, StandardCharsets.UTF_8)
            RestrictedFiles.restrict(file)
            return file
        } catch (e: Exception) {
            try {
                Files.deleteIfExists(file)
            } catch (_: Exception) {
                // best-effort
            }
            throw e
        }
    }

    fun lookup(launchId: String): SessionToken? = byLaunchId[launchId]

    fun bind(handler: ProcessHandler, launchId: String) {
        handler.putUserData(LAUNCH_KEY, launchId)
    }

    fun bindFromProcess(handler: ProcessHandler) {
        val launchId = handler.getUserData(LAUNCH_KEY) ?: extractLaunchId(handler) ?: return
        handler.putUserData(LAUNCH_KEY, launchId)
    }

    fun tokenFor(handler: ProcessHandler): SessionToken? {
        bindFromProcess(handler)
        val launchId = handler.getUserData(LAUNCH_KEY) ?: return null
        return byLaunchId[launchId]
    }

    fun forget(handler: ProcessHandler) {
        val launchId = handler.getUserData(LAUNCH_KEY) ?: extractLaunchId(handler)
        handler.putUserData(LAUNCH_KEY, null)
        if (launchId != null) {
            forget(launchId)
        }
    }

    fun forget(launchId: String) {
        val removed = byLaunchId.remove(launchId)
        if (removed != null) {
            try {
                Files.deleteIfExists(removed.file)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    fun tokenDirectory(): Path = Path.of(System.getProperty("java.io.tmpdir"), "runtimerocket", "tokens")

    internal fun extractLaunchId(handler: ProcessHandler): String? {
        return ProcessLaunchIds.launchId(handler)
    }
}
