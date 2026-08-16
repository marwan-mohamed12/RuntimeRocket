package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.JavaParameters
import io.runtimerocket.plugin.settings.RrProjectSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RrJbrConsentTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun consentDefaultsFalseAndIsRequiredBeforeApplyingJbr() {
        val settings = RrProjectSettings()
        assertFalse(settings.jbrConsentAccepted)
        assertFalse(settings.jbrConsentAsked)

        val holder = RrJbrConsent.JrePathHolder("temurin-21")
        val jbr = temp.resolve("jbr-21")
        Files.createDirectories(jbr)
        assertFalse(RrJbrConsent.applyBundledJbr(holder, consentAccepted = false, jbrHome = jbr))
        assertEquals("temurin-21", holder.jrePath)
    }

    @Test
    fun applyBundledJbrSetsPathOnlyAfterConsent() {
        val holder = RrJbrConsent.JrePathHolder("temurin-21")
        val jbr = temp.resolve("bundled-jbr")
        Files.createDirectories(jbr)

        assertFalse(RrJbrConsent.applyBundledJbr(holder, consentAccepted = false, jbrHome = jbr))
        assertEquals("temurin-21", holder.jrePath)

        assertTrue(RrJbrConsent.applyBundledJbr(holder, consentAccepted = true, jbrHome = jbr))
        assertEquals(jbr.toString(), holder.jrePath)
    }

    @Test
    fun applyBundledJbrWithoutJbrHomeDoesNotChangePath() {
        val holder = RrJbrConsent.JrePathHolder("temurin-21")
        assertFalse(RrJbrConsent.applyBundledJbr(holder, consentAccepted = true, jbrHome = null))
        assertEquals("temurin-21", holder.jrePath)
    }

    @Test
    fun offerIsOncePerProjectAndSkippedWhenAlreadyAsked() {
        val settings = RrProjectSettings()
        settings.preferEnhanced = true
        assertTrue(RrJbrConsent.shouldOffer(settings, enhanced = false, jbrAvailable = true))
        RrJbrConsent.markAsked(settings)
        assertTrue(settings.jbrConsentAsked)
        assertFalse(settings.jbrConsentAccepted)
        assertFalse(RrJbrConsent.shouldOffer(settings, enhanced = false, jbrAvailable = true))
    }

    @Test
    fun declineRemembersAndDoesNotNag() {
        val settings = RrProjectSettings()
        settings.preferEnhanced = true
        RrJbrConsent.decline(settings)
        assertTrue(settings.jbrConsentAsked)
        assertFalse(settings.jbrConsentAccepted)
        assertFalse(RrJbrConsent.shouldOffer(settings, enhanced = false, jbrAvailable = true))
    }

    @Test
    fun acceptDoesNotWriteJreUntilApplyIsCalled() {
        val settings = RrProjectSettings()
        val holder = RrJbrConsent.JrePathHolder("temurin-21")
        RrJbrConsent.accept(settings)
        assertTrue(settings.jbrConsentAccepted)
        assertEquals("temurin-21", holder.jrePath)
    }

    @Test
    fun patcherNeverWritesRunConfigJdk() {
        val agent = temp.resolve("runtimerocket-agent.jar")
        val token = temp.resolve("session.token")
        Files.writeString(agent, "jar")
        Files.writeString(token, "tok")

        val params = JavaParameters()
        assertNull(params.jdk)
        RrJavaProgramPatcher.applyTo(params, agent, token, "info", enhanced = false)
        assertNull(params.jdk, "patcher must not swap the JDK; only the consent action writes jrePath")
        assertTrue(params.vmParametersList.parameters.any { it.startsWith("-javaagent:") })
    }

    @Test
    fun applyToRunProfileWithoutProfileDoesNotThrowOrWrite() {
        val jbr = temp.resolve("jbr")
        Files.createDirectories(jbr)
        assertFalse(RrJbrConsent.applyToRunProfile(null, consentAccepted = true, jbrHome = jbr))
    }
}
