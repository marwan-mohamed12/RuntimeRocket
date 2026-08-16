package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.RunProfile
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

    data class SessionToken(
        val token: String,
        val file: Path,
        val createdAt: Instant,
    )

    private val byKey = ConcurrentHashMap<String, SessionToken>()

    fun generate(): String {
        val raw = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(raw)
        return HexFormat.of().formatHex(raw)
    }

    fun writeSessionFile(configuration: RunProfile, directory: Path = tokenDirectory()): Path {
        val token = generate()
        Files.createDirectories(directory)
        RestrictedFiles.restrict(directory)
        val file = writeTokenFile(token, directory)
        byKey[key(configuration)] = SessionToken(token, file, Instant.now())
        return file
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

    fun forSession(configuration: RunProfile): String? = byKey[key(configuration)]?.token

    fun sessionToken(configuration: RunProfile): SessionToken? = byKey[key(configuration)]

    fun forget(configuration: RunProfile) {
        val removed = byKey.remove(key(configuration))
        if (removed != null) {
            try {
                Files.deleteIfExists(removed.file)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    fun tokenDirectory(): Path = Path.of(System.getProperty("java.io.tmpdir"), "runtimerocket", "tokens")

    internal fun key(configuration: RunProfile): String {
        return configuration.javaClass.name + ":" + configuration.name + ":" + System.identityHashCode(configuration)
    }
}
