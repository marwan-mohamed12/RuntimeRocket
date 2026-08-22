package io.runtimerocket.plugin.run

/**
 * How the plugin relaunches a run configuration. Reusing the stored
 * [com.intellij.execution.runners.ExecutionEnvironment] with
 * [com.intellij.execution.runners.ExecutionUtil.restart] re-enters
 * `restartRunProfile` on the same instance and the process never stops
 * relaunching.
 */
object RrProcessRestart {
    enum class Strategy {
        FRESH_ENVIRONMENT,
        REUSE_STORED_ENVIRONMENT,
    }

    fun strategy(hasRunnerSettings: Boolean): Strategy {
        return if (hasRunnerSettings) Strategy.FRESH_ENVIRONMENT else Strategy.REUSE_STORED_ENVIRONMENT
    }
}
