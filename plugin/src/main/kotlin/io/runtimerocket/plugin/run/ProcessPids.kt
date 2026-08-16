package io.runtimerocket.plugin.run

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler

/** `ProcessHandler.getPid()` exists on 243–262; reflect so a rename still falls back. */
fun ProcessHandler.rrPid(): Long? {
    for (name in arrayOf("getPid", "getNativePid")) {
        val method =
            try {
                javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            } catch (_: Exception) {
                null
            } ?: continue
        val value =
            try {
                method.invoke(this)
            } catch (_: Exception) {
                continue
            }
        when (value) {
            is Long -> return value
            is Int -> return value.toLong()
            is Number -> return value.toLong()
        }
    }
    return (this as? OSProcessHandler)?.process?.pid()
}
