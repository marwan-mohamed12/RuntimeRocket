package io.runtimerocket.plugin.watch

/**
 * Coordinates compile vs VFS so a failed or in-flight compile cannot schedule a reload.
 * VFS is only a Gradle safety net when [CompilationStatusListener] did not fire.
 */
internal class RrCompileCycle {
    @Volatile
    var compiling: Boolean = false
        private set

    @Volatile
    var listenerHandled: Boolean = false
        private set

    @Volatile
    var vfsArmed: Boolean = false
        private set

    @Volatile
    var reloadStarted: Boolean = false

    fun onBuildStarted() {
        compiling = true
        listenerHandled = false
        vfsArmed = false
        reloadStarted = false
    }

    /** @return true when the compile listener should push a reload. */
    fun onCompileFinished(failed: Boolean): Boolean {
        compiling = false
        listenerHandled = true
        vfsArmed = false
        return !failed
    }

    fun shouldScheduleVfs(): Boolean {
        if (compiling || listenerHandled) {
            return false
        }
        return vfsArmed
    }

    fun onBuildFinished(suppressAutoReload: Boolean = false): BuildFinish {
        compiling = false
        if (listenerHandled) {
            vfsArmed = false
            return if (reloadStarted) BuildFinish.NOTHING else BuildFinish.RESTORE_IDLE
        }
        if (suppressAutoReload) {
            vfsArmed = false
            return BuildFinish.RESTORE_IDLE
        }
        vfsArmed = true
        return BuildFinish.ARM_VFS
    }

    fun releaseCompileLock() {
        compiling = false
    }

    /** Gradle path: scan outputs after settle; do not wait for later VFS events. */
    fun shouldScanOutputs(): Boolean = vfsArmed && !listenerHandled && !compiling

    fun markReloadStarted() {
        reloadStarted = true
    }

    fun disarmVfs() {
        vfsArmed = false
    }

    enum class BuildFinish {
        RESTORE_IDLE,
        ARM_VFS,
        NOTHING,
    }
}
