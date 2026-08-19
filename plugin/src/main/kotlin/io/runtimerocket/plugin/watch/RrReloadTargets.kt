package io.runtimerocket.plugin.watch

/** Pure next-target rules for Reload when the tool window has no module in focus. */
internal object RrReloadTargets {
    fun <T> pick(
        hint: T?,
        unsaved: Collection<T>,
        vcs: Collection<T>,
        open: Collection<T>,
        project: Collection<T>,
    ): List<T> {
        if (hint != null) {
            return listOf(hint)
        }
        if (unsaved.isNotEmpty()) {
            return unsaved.distinct()
        }
        if (vcs.isNotEmpty()) {
            return vcs.distinct()
        }
        if (open.isNotEmpty()) {
            return open.distinct()
        }
        return project.distinct()
    }
}
