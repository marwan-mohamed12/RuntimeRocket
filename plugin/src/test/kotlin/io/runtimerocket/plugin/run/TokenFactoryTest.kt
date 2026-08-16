package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.NopProcessHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
    fun putOnParametersWritesLaunchEnv() {
        val launch = TokenFactory.newLaunch(temp)
        val params = JavaParameters()
        TokenFactory.putOnParameters(params, launch)
        assertEquals(launch.launchId, params.env[TokenFactory.ENV_LAUNCH])
        TokenFactory.forget(launch.launchId)
    }
}
