package io.runtimerocket.plugin.ui

import io.runtimerocket.plugin.run.LateAttachNotes
import io.runtimerocket.plugin.run.RrSession
import io.runtimerocket.plugin.watch.RrReloadHistory
import io.runtimerocket.protocol.ReloadResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the tool-window console as typed segments so status can be colored. */
object RrConsoleFormatter {
    enum class Kind {
        DEFAULT,
        MUTED,
        SUCCESS,
        PARTIAL,
        WARNING,
        ERROR,
        ACCENT,
        HEADER,
        TIMESTAMP,
        META,
    }

    data class Segment(val text: String, val kind: Kind = Kind.DEFAULT)

    fun kindForStatus(status: String?): Kind {
        return when (status?.uppercase()) {
            ReloadResult.SUCCESS, "REDEFINED", "DEFINED" -> Kind.SUCCESS
            ReloadResult.PARTIAL -> Kind.PARTIAL
            ReloadResult.RESTART_REQUIRED, "SKIPPED" -> Kind.WARNING
            ReloadResult.FAILED -> Kind.ERROR
            else -> Kind.DEFAULT
        }
    }

    fun statusCaption(phase: RrStatus.Phase, backend: String = ""): String {
        return when (phase) {
            RrStatus.Phase.IDLE -> "IDLE"
            RrStatus.Phase.WAITING -> "WAITING"
            RrStatus.Phase.ATTACHED ->
                when {
                    backend.contains("enhanced", ignoreCase = true) -> "ENHANCED"
                    backend.contains("standard", ignoreCase = true) -> "STANDARD"
                    backend.isBlank() -> "ATTACHED"
                    else -> backend.uppercase()
                }
            RrStatus.Phase.NOT_ATTACHED -> "NOT ATTACHED"
            RrStatus.Phase.COMPILING -> "COMPILING"
            RrStatus.Phase.RELOADING -> "RELOADING"
            RrStatus.Phase.SUCCESS -> "SUCCESS"
            RrStatus.Phase.PARTIAL -> "PARTIAL"
            RrStatus.Phase.RESTART_REQUIRED -> "RESTART"
            RrStatus.Phase.FAILED -> "FAILED"
        }
    }

    fun sessionLines(sessions: Collection<RrSession>): List<Segment> {
        if (sessions.isEmpty()) {
            return listOf(Segment("No session attached", Kind.MUTED))
        }
        val out = mutableListOf<Segment>()
        sessions.forEachIndexed { index, session ->
            if (index > 0) {
                out += Segment("\n")
            }
            out += Segment("pid ${session.pid}", Kind.ACCENT)
            out += Segment("  ·  ")
            out += Segment(session.backend.ifBlank { "attached" }, kindForBackend(session.backend))
            if (session.handshake.version.isNotBlank()) {
                out += Segment("  ·  ${session.handshake.version}", Kind.MUTED)
            }
            val spring = LateAttachNotes.springInactive(session.handshake.notes)
            if (spring != null) {
                out += Segment("\n${LateAttachNotes.toolWindowText(spring)}", Kind.PARTIAL)
            }
        }
        return out
    }

    fun lastResult(
        event: RrReloadHistory.Event?,
        missingOutput: String?,
        steps: List<String> = emptyList(),
    ): List<Segment> {
        if (event == null && missingOutput.isNullOrBlank() && steps.isEmpty()) {
            return listOf(
                Segment("No reload yet", Kind.HEADER),
                Segment(
                    "\nBuild the project while a session is attached, or use Attach to connect to a running JVM.",
                    Kind.MUTED,
                ),
            )
        }
        val out = mutableListOf<Segment>()
        if (steps.isNotEmpty()) {
            out += Segment("RELOAD STEPS", Kind.HEADER)
            for (step in steps) {
                out += Segment("\n$step", Kind.META)
            }
            out += Segment("\n\n")
        }
        if (event != null) {
            out += Segment("LAST RESULT", Kind.HEADER)
            out += Segment("\n")
            out += Segment(event.status, kindForStatus(event.status))
            out += Segment("   reload ", Kind.MUTED)
            out += Segment("${event.latencyMs} ms", Kind.META)
            out += Segment("   agent ", Kind.MUTED)
            out += Segment("${event.durationMs} ms", Kind.META)
            if (!event.message.isNullOrBlank()) {
                out += Segment("\nReason  ", Kind.MUTED)
                out += Segment(event.message, kindForStatus(event.status))
            }
            if (event.status == ReloadResult.RESTART_REQUIRED) {
                out += Segment("\nRESTART_REQUIRED — use Restart to relaunch the run configuration.", Kind.WARNING)
            }
            if (event.classes.isNotEmpty()) {
                out += Segment("\n\nClasses", Kind.HEADER)
                for (outcome in event.classes) {
                    out += Segment("\n  ")
                    out += Segment(outcome.binaryName ?: "(unknown)", Kind.DEFAULT)
                    out += Segment("  ")
                    out += Segment(outcome.status ?: "", kindForStatus(outcome.status))
                    if (!outcome.reason.isNullOrBlank()) {
                        out += Segment("  ${outcome.reason}", Kind.MUTED)
                    }
                    val kinds = outcome.changeKinds
                    if (!kinds.isNullOrEmpty()) {
                        out += Segment("  ${kinds.joinToString(",")}", Kind.META)
                    }
                }
            }
            if (event.adapters.isNotEmpty()) {
                out += Segment("\n\nAdapters", Kind.HEADER)
                for (adapter in event.adapters) {
                    out += Segment("\n  ")
                    out += Segment(adapter.adapterId ?: "(adapter)", Kind.DEFAULT)
                    out += Segment("  ")
                    out += Segment(adapter.status ?: "", kindForStatus(adapter.status))
                    if (!adapter.detail.isNullOrBlank()) {
                        out += Segment("  ${adapter.detail}", Kind.MUTED)
                    }
                }
            }
        }
        if (!missingOutput.isNullOrBlank()) {
            if (out.isNotEmpty()) {
                out += Segment("\n\n")
            }
            out += Segment(missingOutput, Kind.WARNING)
        }
        return out
    }

    fun events(events: List<RrReloadHistory.Event>): List<Segment> {
        val out = mutableListOf<Segment>()
        out += Segment("RECENT EVENTS", Kind.HEADER)
        if (events.isEmpty()) {
            out += Segment("\n  (none yet)", Kind.MUTED)
            return out
        }
        for (event in events) {
            out += Segment("\n")
            out += Segment(TIME.format(event.time.atZone(ZoneId.systemDefault())), Kind.TIMESTAMP)
            out += Segment("  ")
            out += Segment(padStatus(event.status), kindForStatus(event.status))
            out += Segment("  ${classCount(event.classCount)}", Kind.META)
            out += Segment("  ${event.latencyMs} ms", Kind.META)
            if (!event.message.isNullOrBlank()) {
                out += Segment("  ${event.message}", Kind.MUTED)
            }
        }
        return out
    }

    fun kindForBackend(backend: String): Kind {
        return if (backend.contains("enhanced", ignoreCase = true)) Kind.SUCCESS else Kind.ACCENT
    }

    internal fun padStatus(status: String): String = status.padEnd(STATUS_WIDTH)

    internal fun classCount(count: Int): String = if (count == 1) "1 class" else "$count classes"

    private const val STATUS_WIDTH = 17
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
}
