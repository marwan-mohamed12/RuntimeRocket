package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
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

    @Volatile
    private var lastLaunch: SessionToken? = null

    fun generate(): String {
        val raw = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(raw)
        return HexFormat.of().formatHex(raw)
    }

    fun newLaunch(directory: Path = tokenDirectory()): SessionToken {
        val token = generate()
        ensureDirectory(directory)
        RestrictedFiles.restrict(directory)
        val file = writeTokenFile(token, directory)
        val session = SessionToken(UUID.randomUUID().toString(), token, file, Instant.now())
        byLaunchId[session.launchId] = session
        lastLaunch = session
        return session
    }

    fun putOnParameters(params: JavaParameters, launch: SessionToken) {
        params.addEnv(ENV_LAUNCH, launch.launchId)
    }

    fun writeTokenFile(token: String, directory: Path): Path {
        ensureDirectory(directory)
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
        val launchId = handler.getUserData(LAUNCH_KEY)
        if (launchId != null) {
            byLaunchId[launchId]?.let { return it }
        }
        val recent = lastLaunch
        if (recent != null && java.time.Duration.between(recent.createdAt, Instant.now()).seconds < 120) {
            bind(handler, recent.launchId)
            return recent
        }
        return null
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
        if (lastLaunch?.launchId == launchId) {
            lastLaunch = null
        }
        if (removed != null) {
            try {
                Files.deleteIfExists(removed.file)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    /** IDE system dir — never `%TEMP%\runtimerocket`, which may be a Hybris handshake junction. */
    fun tokenDirectory(): Path = Path.of(PathManager.getSystemPath(), "runtimerocket", "tokens")

    /**
     * Create [directory] without [Files.createDirectories] / [Files.createDirectory]
     * on an existing parent. IntelliJ's NIO provider and the JDK both treat a
     * Windows junction as "not a directory" (`NOFOLLOW_LINKS`), so creating
     * `%TEMP%\runtimerocket\tokens` throws [FileAlreadyExistsException] on the
     * junction itself. `File.mkdir` uses Win32 and follows the junction.
     */
    internal fun ensureDirectory(directory: Path) {
        if (isUsableDir(directory)) {
            return
        }
        if (isUsableFile(directory)) {
            Files.deleteIfExists(directory)
        }
        val io = directory.toFile()
        if (mkdirLeaf(io, directory)) {
            return
        }
        val parent = directory.parent
        if (parent != null) {
            if (isUsableFile(parent)) {
                Files.deleteIfExists(parent)
            }
            if (!isUsableDir(parent)) {
                if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                    val real =
                        try {
                            parent.toRealPath()
                        } catch (_: Exception) {
                            null
                        }
                    if (real != null && isUsableDir(real)) {
                        ensureDirectory(real.resolve(directory.fileName.toString()))
                        return
                    }
                } else {
                    ensureDirectory(parent)
                }
            }
        }
        if (mkdirLeaf(io, directory) || io.mkdirs() || isUsableDir(directory)) {
            return
        }
        throw FileAlreadyExistsException(directory.toString())
    }

    private fun isUsableDir(path: Path): Boolean = path.toFile().isDirectory || Files.isDirectory(path)

    private fun isUsableFile(path: Path): Boolean = path.toFile().isFile || Files.isRegularFile(path)

    private fun mkdirLeaf(io: File, path: Path): Boolean = io.mkdir() || isUsableDir(path)

    internal fun extractLaunchId(handler: ProcessHandler): String? {
        return ProcessLaunchIds.launchId(handler)
    }
}
