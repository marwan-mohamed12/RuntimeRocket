package io.runtimerocket.plugin.watch

import java.nio.file.Path

data class OutputRoot(
    val path: Path,
    val moduleName: String = "",
)
