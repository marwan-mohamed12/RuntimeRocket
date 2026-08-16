package io.runtimerocket.plugin.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ModuleOutputLocatorTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun gradleWellKnownMainDirsExist() {
        Files.createDirectories(temp.resolve("build/classes/java/main"))
        Files.createDirectories(temp.resolve("build/classes/kotlin/main"))
        Files.createDirectories(temp.resolve("build/resources/main"))
        Files.createDirectories(temp.resolve("build/classes/java/test"))
        val found = ModuleOutputLocator.wellKnownOutputs(temp, includeTests = false, gradle = true, maven = false)
        val asText = found.map { temp.relativize(it).toString().replace('\\', '/') }
        assertTrue("build/classes/java/main" in asText, asText.toString())
        assertTrue("build/classes/kotlin/main" in asText, asText.toString())
        assertTrue("build/resources/main" in asText, asText.toString())
        assertFalse(asText.any { it.contains("/test") }, asText.toString())
    }

    @Test
    fun mavenTestOutputOnlyWhenRequested() {
        Files.createDirectories(temp.resolve("target/classes"))
        Files.createDirectories(temp.resolve("target/test-classes"))
        val main = ModuleOutputLocator.wellKnownOutputs(temp, includeTests = false, gradle = false, maven = true)
        val withTests = ModuleOutputLocator.wellKnownOutputs(temp, includeTests = true, gradle = false, maven = true)
        assertTrue(main.any { it.endsWith(Path.of("target/classes")) })
        assertFalse(main.any { it.endsWith(Path.of("target/test-classes")) })
        assertTrue(withTests.any { it.endsWith(Path.of("target/test-classes")) })
    }

    @Test
    fun mavenSystemIdIsUppercase() {
        assertEquals("MAVEN", ModuleOutputLocator.MAVEN.id)
        assertEquals("GRADLE", ModuleOutputLocator.GRADLE.id)
    }

    @Test
    fun missingOutputIsReportedPerModule() {
        assertEquals(
            "no compiler output found for module kmp",
            ModuleOutputLocator.formatMissingOutput(listOf("kmp")),
        )
        assertEquals(
            "no compiler output found for modules app, kmp",
            ModuleOutputLocator.formatMissingOutput(listOf("app", "kmp")),
        )
        assertEquals("no compiler output found", ModuleOutputLocator.formatMissingOutput(emptyList(), noRoots = true))
        assertEquals(null, ModuleOutputLocator.formatMissingOutput(emptyList(), noRoots = false))
    }

    @Test
    fun doesNotGuessKmpLayouts() {
        Files.createDirectories(temp.resolve("build/classes/kotlin/jvm/main"))
        Files.createDirectories(temp.resolve("build/tmp/kapt3/classes/main"))
        val found = ModuleOutputLocator.wellKnownOutputs(temp, includeTests = true, gradle = true, maven = false)
        assertTrue(found.isEmpty(), found.toString())
        assertFalse(ModuleOutputLocator.wellKnownRelativeDirs(true, gradle = true, maven = false).any { it.contains("jvm") })
    }
}
