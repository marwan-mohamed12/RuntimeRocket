package io.runtimerocket.plugin.settings

import io.runtimerocket.plugin.run.RrRunConfigSupport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrProjectSettingsTest {
    @Test
    fun includeTestsFalseDoesNotEnableJunit() {
        val settings = RrProjectSettings()
        settings.includeTests = false
        assertFalse(RrRunConfigSupport.isPatchableType(RrRunConfigSupport.JUNIT_TYPE_ID, includeTests = false))
        assertFalse(settings.decideEnabled(RrRunConfigSupport.JUNIT_TYPE_ID, userEnabled = null))
    }

    @Test
    fun includeTestsTrueEnablesJunitWithoutUserData() {
        val settings = RrProjectSettings()
        settings.includeTests = true
        assertTrue(RrRunConfigSupport.isPatchableType(RrRunConfigSupport.JUNIT_TYPE_ID, includeTests = true))
        assertTrue(settings.decideEnabled(RrRunConfigSupport.JUNIT_TYPE_ID, userEnabled = null))
    }

    @Test
    fun includeTestsTrueStillHonorsExplicitOff() {
        val settings = RrProjectSettings()
        settings.includeTests = true
        assertFalse(settings.decideEnabled(RrRunConfigSupport.JUNIT_TYPE_ID, userEnabled = false))
    }

    @Test
    fun projectDisabledBlocksJunitEvenWhenIncludeTests() {
        val settings = RrProjectSettings()
        settings.enabled = false
        settings.includeTests = true
        assertFalse(settings.decideEnabled(RrRunConfigSupport.JUNIT_TYPE_ID, userEnabled = null))
    }

    @Test
    fun hybrisApplicationDefaultsOffUnlessExplicitlyEnabled() {
        val settings = RrProjectSettings()
        assertFalse(settings.decideEnabled(RrRunConfigSupport.APPLICATION_TYPE_ID, userEnabled = null, hybris = true))
        assertTrue(settings.decideEnabled(RrRunConfigSupport.APPLICATION_TYPE_ID, userEnabled = true, hybris = true))
        assertTrue(settings.decideEnabled(RrRunConfigSupport.APPLICATION_TYPE_ID, userEnabled = null, hybris = false))
    }

    @Test
    fun jbrConsentDefaultsFalse() {
        val settings = RrProjectSettings()
        assertFalse(settings.jbrConsentAccepted)
        assertFalse(settings.jbrConsentAsked)
    }
}
