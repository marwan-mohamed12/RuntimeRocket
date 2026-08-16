package io.runtimerocket.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

object PluginVersion {
    const val FALLBACK = "0.1.0-SNAPSHOT"
    const val PLUGIN_ID = "io.runtimerocket"

    fun current(): String {
        return try {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
            val version = plugin?.version
            if (version.isNullOrBlank()) FALLBACK else version
        } catch (_: Throwable) {
            FALLBACK
        }
    }

    /** 0.1.x plugin talks to 0.1.y agent. */
    fun compatibleWithAgent(agentVersion: String?): Boolean {
        if (agentVersion.isNullOrBlank()) {
            return false
        }
        val plugin = parseMinor(current())
        val agent = parseMinor(agentVersion)
        return plugin != null && plugin == agent
    }

    private fun parseMinor(version: String): Pair<String, String>? {
        val parts = version.split('.', limit = 3)
        if (parts.size < 2) {
            return null
        }
        return parts[0] to parts[1]
    }
}
