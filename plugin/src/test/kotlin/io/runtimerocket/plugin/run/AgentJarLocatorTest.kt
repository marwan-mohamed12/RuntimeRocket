package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AgentJarLocatorTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun sameSizeDifferentBytesAreNotSameContent() {
        val left = temp.resolve("a.jar")
        val right = temp.resolve("b.jar")
        Files.writeString(left, "xxxx")
        Files.writeString(right, "yyyy")
        assertFalse(AgentJarLocator.sameContent(left, right))
        Files.writeString(right, "xxxx")
        assertTrue(AgentJarLocator.sameContent(left, right))
    }
}
