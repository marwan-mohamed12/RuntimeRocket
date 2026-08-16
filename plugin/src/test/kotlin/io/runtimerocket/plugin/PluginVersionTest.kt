package io.runtimerocket.plugin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginVersionTest {
    @Test
    fun compatibleOnSameMinor() {
        assertTrue(PluginVersion.compatibleWithAgent("0.1.0-SNAPSHOT"))
        assertTrue(PluginVersion.compatibleWithAgent("0.1.9"))
        assertFalse(PluginVersion.compatibleWithAgent("0.2.0"))
        assertFalse(PluginVersion.compatibleWithAgent("1.1.0"))
        assertFalse(PluginVersion.compatibleWithAgent(null))
        assertFalse(PluginVersion.compatibleWithAgent(""))
    }
}
