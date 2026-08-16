package io.runtimerocket.plugin.run

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.configurations.RunProfile
import io.runtimerocket.plugin.settings.RrProjectSettings
import java.nio.file.Path

/** One-time per-project JBR offer (KD13). Never writes a run-config JRE unless the user consents. */
object RrJbrConsent {
    const val ACTION_TEXT = "Use bundled JetBrains Runtime for this configuration"
    const val DIALOG_TITLE = "RuntimeRocket"
    const val DIALOG_MESSAGE =
        "Use bundled JetBrains Runtime for this configuration? This enables enhanced HotSwap (add methods/fields)."
    const val CONTINUE_TEXT = "Continue with method-body only"

    data class JrePathHolder(var jrePath: String? = null)

    fun shouldOffer(
        asked: Boolean,
        enhanced: Boolean,
        preferEnhanced: Boolean,
        jbrAvailable: Boolean,
    ): Boolean {
        return !asked && !enhanced && preferEnhanced && jbrAvailable
    }

    fun shouldOffer(settings: RrProjectSettings, enhanced: Boolean, jbrAvailable: Boolean): Boolean {
        return shouldOffer(settings.jbrConsentAsked, enhanced, settings.preferEnhanced, jbrAvailable)
    }

    fun markAsked(settings: RrProjectSettings) {
        settings.jbrConsentAsked = true
    }

    fun accept(settings: RrProjectSettings) {
        settings.jbrConsentAsked = true
        settings.jbrConsentAccepted = true
    }

    fun decline(settings: RrProjectSettings) {
        settings.jbrConsentAsked = true
        settings.jbrConsentAccepted = false
    }

    /**
     * Sets [JrePathHolder.jrePath] to [jbrHome] only after explicit consent.
     * Returns false and leaves the holder unchanged when consent is missing or no JBR is available.
     */
    fun applyBundledJbr(target: JrePathHolder, consentAccepted: Boolean, jbrHome: Path?): Boolean {
        if (!consentAccepted || jbrHome == null) {
            return false
        }
        target.jrePath = jbrHome.toString()
        return true
    }

    fun applyToRunProfile(profile: RunProfile?, consentAccepted: Boolean, jbrHome: Path?): Boolean {
        val java = profile as? CommonJavaRunConfigurationParameters ?: return false
        val holder = JrePathHolder(java.alternativeJrePath)
        if (!applyBundledJbr(holder, consentAccepted, jbrHome)) {
            return false
        }
        java.alternativeJrePath = holder.jrePath
        java.isAlternativeJrePathEnabled = true
        return true
    }
}
