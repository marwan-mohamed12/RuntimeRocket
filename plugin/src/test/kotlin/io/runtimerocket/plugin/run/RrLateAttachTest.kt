package io.runtimerocket.plugin.run

import com.sun.tools.attach.AgentLoadException
import com.sun.tools.attach.AttachNotSupportedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class RrLateAttachTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun invalidPidProducesUserVisibleErrorAndDoesNotAttach() {
        var attached = false
        val result =
            RrLateAttach.attach(
                pidRaw = "not-a-pid",
                agentJar = temp.resolve("agent.jar"),
                tokenFile = temp.resolve("t.token"),
                logLevel = "info",
                attachFn = {
                    attached = true
                    throwingVm(IllegalStateException("should not attach"))
                },
            )
        assertFalse(result.ok)
        assertFalse(attached)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Invalid process id"), result.error)
        assertTrue(result.error!!.isNotBlank())
    }

    @Test
    fun emptyPidProducesUserVisibleError() {
        val result =
            RrLateAttach.attach(
                pidRaw = "   ",
                agentJar = temp.resolve("agent.jar"),
                tokenFile = temp.resolve("t.token"),
                logLevel = "info",
                attachFn = { throwingVm(IllegalStateException("should not attach")) },
            )
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("Invalid process id"), result.error)
    }

    @Test
    fun attachDeniedProducesUserVisibleError() {
        val result = failingAttach(AttachNotSupportedException("attach denied by OS"))
        assertFalse(result.ok)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Attach denied"), result.error)
        assertFalse(result.error!!.isBlank())
    }

    @Test
    fun wrongUserProducesUserVisibleError() {
        val result = failingAttach(IOException("Operation not permitted: wrong user"))
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("wrong user"), result.error)
        assertTrue(result.error!!.contains("Attach denied"), result.error)
    }

    @Test
    fun notHotSpotProducesUserVisibleError() {
        val result = failingAttach(AttachNotSupportedException("no providers installed"))
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("not a HotSpot"), result.error)
    }

    @Test
    fun processGoneProducesUserVisibleError() {
        val result = failingAttach(IOException("No such process: 4242"))
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("Process gone"), result.error)
        assertTrue(result.error!!.contains("4242"), result.error)
    }

    @Test
    fun agentLoadFailureIsNotSwallowed() {
        val result = failingAttach(AgentLoadException("agent failed to initialize"))
        assertFalse(result.ok)
        assertTrue(result.error!!.isNotBlank())
        assertTrue(
            result.error!!.contains("Attach denied") || result.error!!.contains("Failed to attach"),
            result.error,
        )
    }

    @Test
    fun successfulAttachLoadsAgentWithTokenFileArgs() {
        val agent = temp.resolve("runtimerocket-agent.jar")
        val token = temp.resolve("session.token")
        Files.writeString(agent, "jar")
        Files.writeString(token, "tok")
        var loadedJar: String? = null
        var loadedArgs: String? = null
        val result =
            RrLateAttach.attach(
                pidRaw = "12345 — my.App",
                agentJar = agent,
                tokenFile = token,
                logLevel = "debug",
                attachFn = { pid ->
                    assertEquals("12345", pid)
                    object : RrLateAttach.TargetVm {
                        override fun loadAgent(agentJar: String, args: String) {
                            loadedJar = agentJar
                            loadedArgs = args
                        }

                        override fun close() {}
                    }
                },
            )
        assertTrue(result.ok)
        assertNull(result.error)
        assertEquals(agent.toAbsolutePath().toString(), loadedJar)
        assertEquals(RrLateAttach.agentArgs(token, "debug"), loadedArgs)
        assertTrue(loadedArgs!!.contains("tokenFile="))
        assertFalse(RrVmArguments.rawTokenOnCommandLine("-javaagent:x=$loadedArgs"), loadedArgs)
    }

    @Test
    fun parsePidReadsLeadingDigitsFromChooserLine() {
        assertEquals("99", RrLateAttach.parsePid("99 — org.example.Main"))
        assertNull(RrLateAttach.parsePid("abc"))
        assertEquals("7", RrLateAttach.parsePid("7"))
    }

    private fun failingAttach(error: Throwable): RrLateAttach.Result {
        return RrLateAttach.attach(
            pidRaw = "4242",
            agentJar = temp.resolve("agent.jar"),
            tokenFile = temp.resolve("t.token"),
            logLevel = "info",
            attachFn = { throwingVm(error) },
        )
    }

    private fun throwingVm(error: Throwable): RrLateAttach.TargetVm {
        return object : RrLateAttach.TargetVm {
            override fun loadAgent(agentJar: String, args: String) {
                throw error
            }

            override fun close() {}
        }
    }
}
