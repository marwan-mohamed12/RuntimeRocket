package io.runtimerocket.plugin.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReloadNowActionTest {
    @Test
    fun reloadNowHasNoDefaultKeymap() {
        val xml = readPluginXml()
        assertTrue(xml.contains("id=\"rr.reloadNow\""), xml)
        val action = actionBlock(xml, "rr.reloadNow")
        assertFalse(action.contains("keyboard-shortcut"), action)
        assertFalse(action.contains("keymap"), action)
        assertTrue(xml.contains("class=\"io.runtimerocket.plugin.ui.ReloadNowAction\""), xml)
        assertTrue(action.contains("icon=\"/icons/rrReload.svg\""), action)
    }

    @Test
    fun attachActionIsRegisteredWithoutDefaultKeymap() {
        val xml = readPluginXml()
        assertTrue(xml.contains("id=\"rr.attach\""), xml)
        assertTrue(xml.contains("class=\"io.runtimerocket.plugin.run.AttachRuntimeRocketAction\""), xml)
        val action = actionBlock(xml, "rr.attach")
        assertFalse(action.contains("keyboard-shortcut"), action)
        assertTrue(action.contains("Attach RuntimeRocket"), action)
        assertTrue(action.contains("icon=\"/icons/rrAttach.svg\""), action)
        assertTrue(xml.contains("icon=\"/icons/rrReload.svg\""), xml)
        assertTrue(action.contains("rrAttach.svg"), action)
        assertTrue(!action.contains("rrReload.svg"), action)
    }

    private fun readPluginXml(): String {
        val stream =
            javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")
                ?: error("plugin.xml missing from test classpath")
        return stream.bufferedReader().use { it.readText() }
    }

    private fun actionBlock(xml: String, id: String): String {
        val start = xml.indexOf("<action id=\"$id\"")
        require(start >= 0) { "missing action $id in $xml" }
        val end = xml.indexOf("</action>", start)
        if (end < 0) {
            val selfClose = xml.indexOf("/>", start)
            return xml.substring(start, selfClose + 2)
        }
        return xml.substring(start, end + "</action>".length)
    }
}
