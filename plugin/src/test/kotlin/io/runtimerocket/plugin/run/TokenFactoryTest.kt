package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.NopProcessHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TokenFactoryTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun parallelLaunchesHaveDistinctKeys() {
        val first = TokenFactory.newLaunch(temp)
        val second = TokenFactory.newLaunch(temp)
        assertNotEquals(first.launchId, second.launchId)
        assertNotEquals(first.token, second.token)
        assertEquals(first.token, TokenFactory.lookup(first.launchId)?.token)
        TokenFactory.forget(first.launchId)
        assertNull(TokenFactory.lookup(first.launchId))
        assertEquals(second.token, TokenFactory.lookup(second.launchId)?.token)
        TokenFactory.forget(second.launchId)
    }

    @Test
    fun forgetByProcessHandlerDoesNotDropOtherLaunch() {
        val first = TokenFactory.newLaunch(temp)
        val second = TokenFactory.newLaunch(temp)
        val handler = NopProcessHandler()
        TokenFactory.bind(handler, first.launchId)
        assertEquals(first.token, TokenFactory.tokenFor(handler)?.token)
        TokenFactory.forget(handler)
        assertNull(TokenFactory.lookup(first.launchId))
        assertEquals(second.token, TokenFactory.lookup(second.launchId)?.token)
        TokenFactory.forget(second.launchId)
    }

    @Test
    fun tokenForFallsBackToRecentLaunchWhenHandlerHasNoLaunchId() {
        val launch = TokenFactory.newLaunch(temp)
        val handler = NopProcessHandler()
        assertEquals(launch.token, TokenFactory.tokenFor(handler)?.token)
        TokenFactory.forget(launch.launchId)
        val later = NopProcessHandler()
        assertNull(TokenFactory.tokenFor(later))
    }

    @Test
    fun putOnParametersWritesLaunchEnv() {
        val launch = TokenFactory.newLaunch(temp)
        val params = JavaParameters()
        TokenFactory.putOnParameters(params, launch)
        assertEquals(launch.launchId, params.env[TokenFactory.ENV_LAUNCH])
        TokenFactory.forget(launch.launchId)
    }

    @Test
    fun tokenDirectoryIsNotUnderHandshakeTemp() {
        val handshake = Path.of(System.getProperty("java.io.tmpdir"), "runtimerocket")
        val tokens = TokenFactory.tokenDirectory().normalize()
        assertFalse(tokens.startsWith(handshake.normalize()), tokens.toString())
    }

    @Test
    fun newLaunchReplacesLeftoverFileAtHandshakeDir() {
        val handshake = temp.resolve("runtimerocket")
        Files.writeString(handshake, "not-a-directory")
        val launch = TokenFactory.newLaunch(handshake.resolve("tokens"))
        assertTrue(Files.isDirectory(handshake))
        assertTrue(Files.isRegularFile(launch.file))
        TokenFactory.forget(launch.launchId)
    }

    @Test
    fun newLaunchWhenHandshakeDirAlreadyExists() {
        val tokens = Files.createDirectories(temp.resolve("runtimerocket").resolve("tokens"))
        val launch = TokenFactory.newLaunch(tokens)
        assertTrue(Files.isRegularFile(launch.file))
        TokenFactory.forget(launch.launchId)
    }

    @Test
    fun newLaunchWhenHandshakeDirIsLink() {
        val real = Files.createDirectories(temp.resolve("real-handshake"))
        val link = temp.resolve("runtimerocket")
        assumeTrue(linkDirectory(link, real), "directory links are not permitted")
        try {
            val launch = TokenFactory.newLaunch(link.resolve("tokens"))
            assertTrue(Files.isDirectory(link.resolve("tokens")))
            assertTrue(Files.isRegularFile(launch.file))
            TokenFactory.forget(launch.launchId)
        } finally {
            Files.deleteIfExists(link)
        }
    }

    private fun linkDirectory(link: Path, target: Path): Boolean {
        try {
            Files.createSymbolicLink(link, target)
            return true
        } catch (_: Exception) {
            // Windows: junctions do not need SeCreateSymbolicLinkPrivilege.
        }
        return try {
            val code =
                ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            code == 0 && Files.isDirectory(link)
        } catch (_: Exception) {
            false
        }
    }
}
