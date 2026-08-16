package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class RrVmArgumentsTest {
    private val agent = Path.of("C:", "Program Files", "rr", "runtimerocket-agent.jar")
    private val tokenFile = Path.of("C:", "Users", "foo bar", "token.dat")

    @Test
    fun injectsTokenFileNotRawTokenAndAddOpens() {
        val args = RrVmArguments.build(agent, tokenFile, "info", enhanced = false)
        val agentArg = args.single { it.startsWith("-javaagent:") }

        assertTrue(agentArg.contains("tokenFile="), agentArg)
        assertFalse(RrVmArguments.rawTokenOnCommandLine(agentArg), agentArg)
        assertFalse(RrVmArguments.containsRawToken(args), args.toString())
        assertTrue(args.contains(RrVmArguments.ADD_OPENS_LANG), args.toString())
        assertTrue(args.contains(RrVmArguments.ADD_OPENS_REFLECT), args.toString())
        assertEquals(3, args.size)
    }

    @Test
    fun addsEnhancedFlagOnlyWhenDetectorTrue() {
        val on = RrVmArguments.build(agent, tokenFile, "debug", enhanced = true)
        val off = RrVmArguments.build(agent, tokenFile, "debug", enhanced = false)

        assertTrue(on.contains(RrVmArguments.ENHANCED_REDEFINITION), on.toString())
        assertFalse(off.contains(RrVmArguments.ENHANCED_REDEFINITION), off.toString())
        assertTrue(on.any { it.contains("tokenFile=") })
        assertFalse(RrVmArguments.containsRawToken(on))
        assertFalse(RrVmArguments.containsRawToken(off))
    }

    @Test
    fun javaAgentOptionsDoNotIncludeTokenEquals() {
        val arg = RrVmArguments.javaAgent(agent, tokenFile, "info")
        val options = arg.substringAfter('=', "")
        val keys = options.split(',').map { it.substringBefore('=').trim() }
        assertTrue(keys.contains("tokenFile"), keys.toString())
        assertFalse(keys.contains("token"), keys.toString())
        assertTrue(keys.contains("log"), keys.toString())
    }
}
