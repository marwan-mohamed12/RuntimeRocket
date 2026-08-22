package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RrJavaProgramPatcherTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun applyToInjectsTokenFileAddOpensAndEnhancedFlag() {
        val agent = temp.resolve("runtimerocket-agent.jar")
        val token = temp.resolve("session.token")
        Files.writeString(agent, "jar")
        Files.writeString(token, "0123456789abcdef")

        val params = JavaParameters()
        RrJavaProgramPatcher.applyTo(params, agent, token, "info", enhanced = true)
        val args = params.vmParametersList.parameters

        val agentArg = args.single { it.startsWith("-javaagent:") }
        assertFalse(agentArg.startsWith("-javaagent:\""), agentArg)
        assertFalse('"' in agentArg, agentArg)
        assertTrue(agentArg.contains("tokenFile="), agentArg)
        assertTrue(agentArg.contains("watch=false"), agentArg)
        assertFalse(RrVmArguments.rawTokenOnCommandLine(agentArg), agentArg)
        assertFalse(args.any { it.startsWith("-Drr.token=") }, args.toString())
        assertTrue(args.contains(RrVmArguments.ADD_OPENS_LANG), args.toString())
        assertTrue(args.contains(RrVmArguments.ADD_OPENS_REFLECT), args.toString())
        assertTrue(args.contains(RrVmArguments.ENHANCED_REDEFINITION), args.toString())
    }

    @Test
    fun applyToOmitsEnhancedWhenDetectorFalse() {
        val agent = temp.resolve("runtimerocket-agent.jar")
        val token = temp.resolve("session.token")
        Files.writeString(agent, "jar")
        Files.writeString(token, "tok")

        val params = JavaParameters()
        RrJavaProgramPatcher.applyTo(params, agent, token, "warn", enhanced = false)
        val args = params.vmParametersList.parameters
        assertTrue(args.any { it.contains("tokenFile=") })
        assertFalse(args.contains(RrVmArguments.ENHANCED_REDEFINITION), args.toString())
    }
}
