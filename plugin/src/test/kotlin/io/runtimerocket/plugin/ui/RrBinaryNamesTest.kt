package io.runtimerocket.plugin.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RrBinaryNamesTest {
    @Test
    fun nestedTypesUseDollarSeparator() {
        assertEquals("com.example.Outer", RrBinaryNames.fromQualifiedAndNesting("com.example.Outer", emptyList()))
        assertEquals(
            "com.example.Outer\$Inner",
            RrBinaryNames.fromQualifiedAndNesting("com.example.Outer", listOf("Inner")),
        )
        assertEquals(
            "com.example.Outer\$Inner\$Deep",
            RrBinaryNames.fromQualifiedAndNesting("com.example.Outer", listOf("Inner", "Deep")),
        )
    }
}
