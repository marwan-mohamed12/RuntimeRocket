package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler

/** Reads the per-launch id from handler user data or the process command-line environment. */
object ProcessLaunchIds {
    fun launchId(handler: ProcessHandler): String? {
        handler.getUserData(TokenFactory.LAUNCH_KEY)?.let { return it }
        val env = environmentOf(handler) ?: return null
        return env[TokenFactory.ENV_LAUNCH]
    }

    internal fun environmentOf(handler: ProcessHandler): Map<String, String>? {
        try {
            val method = handler.javaClass.methods.firstOrNull { it.name == "getCommandLine" && it.parameterCount == 0 }
            val cmd = method?.invoke(handler)
            if (cmd is GeneralCommandLine) {
                return cmd.environment
            }
        } catch (_: Exception) {
            // fall through to field walk
        }
        var type: Class<*>? = handler.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField("myCommandLine")
                field.isAccessible = true
                val cmd = field.get(handler)
                if (cmd is GeneralCommandLine) {
                    return cmd.environment
                }
            } catch (_: Exception) {
                // try superclass
            }
            type = type.superclass
        }
        return null
    }
}
