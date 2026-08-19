package io.runtimerocket.plugin.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrReloadTargetsTest {
    @Test
    fun explicitHintWins() {
        assertEquals(
            listOf("hint"),
            RrReloadTargets.pick("hint", listOf("unsaved"), listOf("vcs"), listOf("open"), listOf("project")),
        )
    }

    @Test
    fun unsavedBeatsVcsAndOpenWhenNothingIsFocused() {
        assertEquals(
            listOf("cart", "product"),
            RrReloadTargets.pick(null, listOf("cart", "product"), listOf("vcs"), listOf("open"), listOf("project")),
        )
    }

    @Test
    fun vcsBeatsOpenEditorsWhenNothingIsUnsaved() {
        assertEquals(
            listOf("changed"),
            RrReloadTargets.pick(null, emptyList(), listOf("changed"), listOf("open"), listOf("project")),
        )
    }

    @Test
    fun openEditorsAreUsedBeforeWholeProject() {
        assertEquals(
            listOf("open"),
            RrReloadTargets.pick(null, emptyList(), emptyList(), listOf("open"), listOf("project")),
        )
    }

    @Test
    fun emptySignalsFallBackToProjectModules() {
        assertEquals(
            listOf("core", "storefront"),
            RrReloadTargets.pick(null, emptyList(), emptyList(), emptyList(), listOf("core", "storefront")),
        )
        assertTrue(RrReloadTargets.pick(null, emptyList<String>(), emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
