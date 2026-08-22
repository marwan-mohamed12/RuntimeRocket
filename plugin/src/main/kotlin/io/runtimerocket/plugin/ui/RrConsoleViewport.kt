package io.runtimerocket.plugin.ui

/** Decides whether a tool-window console rewrite should skip, follow the end, or restore scroll. */
internal object RrConsoleViewport {
    const val BOTTOM_SLOP = 8

    data class Decision(
        val skip: Boolean,
        val followEnd: Boolean,
        val restoreValue: Int? = null,
    )

    fun decide(
        currentText: String,
        nextText: String,
        scrollValue: Int,
        visibleAmount: Int,
        maximum: Int,
    ): Decision {
        if (currentText == nextText) {
            return Decision(skip = true, followEnd = false)
        }
        val atBottom = scrollValue >= maximum - visibleAmount - BOTTOM_SLOP
        return if (atBottom) {
            Decision(skip = false, followEnd = true)
        } else {
            Decision(skip = false, followEnd = false, restoreValue = scrollValue)
        }
    }
}
