package io.runtimerocket.plugin.run

import com.sun.tools.attach.AttachNotSupportedException
import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import java.nio.file.Path

/** Late-attach via [VirtualMachine.attach]. Failures always produce a user-visible error string. */
object RrLateAttach {
    const val ACTION_TEXT = "Attach RuntimeRocket"

    data class Result(
        val ok: Boolean,
        val pid: String,
        val error: String? = null,
        val handshake: HandshakeDocument? = null,
        val token: String? = null,
    )

    interface TargetVm : AutoCloseable {
        fun loadAgent(agentJar: String, args: String)
    }

    fun parsePid(raw: String?): String? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val token = raw.trim().takeWhile { it.isDigit() }
        return token.takeIf { it.isNotEmpty() && it.toLongOrNull() != null }
    }

    fun invalidPidMessage(raw: String?): String {
        return "Invalid process id: ${raw?.trim().orEmpty().ifBlank { "(empty)" }}. Enter a local JVM pid."
    }

    fun agentArgs(tokenFile: Path, logLevel: String): String {
        return "tokenFile=${tokenFile.toAbsolutePath()},log=$logLevel,watch=false"
    }

    fun formatVmChoice(descriptor: VirtualMachineDescriptor): String {
        val name = descriptor.displayName().ifBlank { "JVM" }
        return RrJvmClassifier.label(descriptor.id(), name)
    }

    fun attach(
        pidRaw: String?,
        agentJar: Path,
        tokenFile: Path,
        logLevel: String,
        attachFn: (String) -> TargetVm = { pid -> ToolsAttachVm(VirtualMachine.attach(pid)) },
    ): Result {
        val pid = parsePid(pidRaw)
        if (pid == null) {
            return Result(ok = false, pid = pidRaw?.trim().orEmpty(), error = invalidPidMessage(pidRaw))
        }
        return try {
            attachFn(pid).use { vm ->
                vm.loadAgent(agentJar.toAbsolutePath().toString(), agentArgs(tokenFile, logLevel))
            }
            Result(ok = true, pid = pid)
        } catch (e: Throwable) {
            Result(ok = false, pid = pid, error = describeError(e, pid))
        }
    }

    fun describeError(error: Throwable, pid: String): String {
        val messages =
            generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
        val lower = messages.lowercase()
        return when {
            isProcessGone(lower) -> "Process gone: pid $pid is not running."
            isWrongUser(lower) ->
                "Attach denied: wrong user. The target JVM must be started by the same user as the IDE."
            lower.contains("denied") ->
                "Attach denied: could not attach to pid $pid. The target must be a local HotSpot/JBR JVM started by the same user."
            isNotHotSpot(error, lower) ->
                "Attach denied: pid $pid is not a HotSpot/JBR JVM (attach is not supported)."
            isDenied(error, lower) ->
                "Attach denied: could not attach to pid $pid. The target must be a local HotSpot/JBR JVM started by the same user."
            else -> {
                val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                "Failed to attach RuntimeRocket to pid $pid: $detail"
            }
        }
    }

    private fun isProcessGone(lower: String): Boolean {
        return lower.contains("no such process") ||
            lower.contains("no process found") ||
            lower.contains("process not found") ||
            lower.contains("non-existent process") ||
            lower.contains("does not exist") ||
            lower.contains("not running") ||
            lower.contains("no process with") ||
            lower.contains("cannot find the file")
    }

    private fun isWrongUser(lower: String): Boolean {
        return lower.contains("wrong user") ||
            lower.contains("same user") ||
            lower.contains("operation not permitted") ||
            lower.contains("permission denied") ||
            lower.contains("access is denied") ||
            lower.contains("access denied")
    }

    private fun isNotHotSpot(error: Throwable, lower: String): Boolean {
        if (error is AttachNotSupportedException || error.cause is AttachNotSupportedException) {
            return !isWrongUser(lower) && !isProcessGone(lower)
        }
        return lower.contains("not a hotspot") ||
            lower.contains("no providers installed") ||
            lower.contains("attach provider") && lower.contains("not")
    }

    private fun isDenied(error: Throwable, lower: String): Boolean {
        if (error is AttachNotSupportedException || error.cause is AttachNotSupportedException) {
            return true
        }
        return lower.contains("denied") ||
            lower.contains("unable to open socket") ||
            lower.contains("not permitted")
    }

    private class ToolsAttachVm(private val vm: VirtualMachine) : TargetVm {
        override fun loadAgent(agentJar: String, args: String) {
            vm.loadAgent(agentJar, args)
        }

        override fun close() {
            vm.detach()
        }
    }
}
