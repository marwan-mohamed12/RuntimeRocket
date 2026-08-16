package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JbrDetectorTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun pathContainingJbrIsEnhancedCapable() {
        val home = temp.resolve("jbr-21.0.5")
        Files.createDirectories(home)
        assertTrue(JbrDetector.isEnhancedCapable(home, null))
    }

    @Test
    fun pathContainingJetBrainsIsEnhancedCapable() {
        val home = temp.resolve("JetBrainsRuntime")
        Files.createDirectories(home)
        assertTrue(JbrDetector.isEnhancedCapable(home, null))
    }

    @Test
    fun versionStringDcevmIsEnhancedCapable() {
        val home = temp.resolve("jdk-21")
        Files.createDirectories(home)
        assertTrue(JbrDetector.isEnhancedCapable(home, "Dynamic Code Evolution 21"))
    }

    @Test
    fun releaseImplementorJetBrainsIsEnhancedCapable() {
        val home = temp.resolve("jdk")
        Files.createDirectories(home)
        Files.writeString(
            home.resolve("release"),
            """
            IMPLEMENTOR="JetBrains s.r.o."
            JAVA_VERSION="21.0.5"
            """.trimIndent(),
        )
        assertTrue(JbrDetector.isEnhancedCapable(home, "21.0.5"))
    }

    @Test
    fun temurinReleaseIsNotEnhancedCapable() {
        val home = temp.resolve("temurin-21")
        Files.createDirectories(home)
        Files.writeString(
            home.resolve("release"),
            """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_VERSION="21.0.5"
            """.trimIndent(),
        )
        assertFalse(JbrDetector.isEnhancedCapable(home, "21.0.5"))
    }

    @Test
    fun jvmLibraryPlusJbrMarkerIsEnhancedCapable() {
        val home = temp.resolve("custom-runtime")
        val server = home.resolve("lib").resolve("server")
        Files.createDirectories(server)
        Files.writeString(server.resolve("jvm.dll"), "stub")
        Files.writeString(home.resolve("release"), "IMPLEMENTOR=\"JetBrains s.r.o.\"\n")
        assertTrue(JbrDetector.isEnhancedCapable(home, null))
        assertTrue(JbrDetector.hasJvmLibrary(home))
    }

    @Test
    fun nullHomeWithoutVersionIsNotEnhanced() {
        assertFalse(JbrDetector.isEnhancedCapable(null, null))
        assertFalse(JbrDetector.isEnhancedCapable(null, "21.0.5"))
    }
}
