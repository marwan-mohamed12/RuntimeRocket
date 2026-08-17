package io.runtimerocket.plugin.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RrEditorFocusTest {
    @Test
    fun explicitFileWins() {
        assertEquals("Foo.java", RrEditorFocus.pickFocusName("Foo.java", listOf("Bar.kt"), listOf("Baz.java")))
    }

    @Test
    fun selectedEditorWinsWhenToolWindowHasNoFile() {
        assertEquals("CartFacade.java", RrEditorFocus.pickFocusName(null, listOf("CartFacade.java"), listOf("Other.kt")))
    }

    @Test
    fun openJavaFileIsPreferredOverUnrelatedTabs() {
        assertEquals(
            "ProductService.java",
            RrEditorFocus.pickFocusName(null, emptyList(), listOf("README.md", "ProductService.java", "notes.txt")),
        )
    }

    @Test
    fun emptyEditorsYieldNull() {
        assertNull(RrEditorFocus.pickFocusName(null, emptyList(), emptyList()))
    }
}
