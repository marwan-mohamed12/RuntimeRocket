package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RrHybrisProjectTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun detectsLocalextensionsAndPlatform() {
        assertFalse(RrHybrisProject.isHybrisRoot(temp))
        Files.createDirectories(temp.resolve("config"))
        Files.writeString(temp.resolve("config").resolve("localextensions.xml"), "<hybrisconfig/>")
        assertTrue(RrHybrisProject.isHybrisRoot(temp))
    }

    @Test
    fun detectsExtensioninfo() {
        Files.writeString(temp.resolve("extensioninfo.xml"), "<extensioninfo/>")
        assertTrue(RrHybrisProject.isHybrisRoot(temp))
    }

    @Test
    fun handshakeDirIsListedWhenPresent() {
        val handshake = temp.resolve("hybris").resolve("temp").resolve("hybris").resolve("runtimerocket")
        Files.createDirectories(handshake)
        val found = RrHybrisProject.handshakeDirs(temp)
        assertTrue(found.any { it == handshake.toAbsolutePath().normalize() || it.normalize() == handshake.normalize() }, found.toString())
    }
}
