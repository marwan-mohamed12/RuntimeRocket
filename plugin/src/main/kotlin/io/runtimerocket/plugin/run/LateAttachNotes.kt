package io.runtimerocket.plugin.run

import io.runtimerocket.protocol.AdapterOutcome
import io.runtimerocket.protocol.ReloadResult

/** Surfaces the Spring inactive note as a visible PARTIAL result. Never a silent no-op when that string is present. */
object LateAttachNotes {
    const val SPRING_INACTIVE_MARKER = "Spring adapter inactive"
    const val SPRING_INACTIVE_DETAIL =
        "Spring adapter inactive until a request hits the app or you restart with -javaagent (premain)."

    data class Display(
        val status: String,
        val balloon: String,
        val historyMessage: String,
        val toolWindowLine: String,
    )

    fun collect(
        handshake: HandshakeDocument? = null,
        result: ReloadResult? = null,
        extra: Iterable<String?> = emptyList(),
    ): List<String> {
        val out = mutableListOf<String>()
        handshake?.notes?.let { out.addAll(it) }
        result?.message?.let { out.add(it) }
        result?.adapters?.forEach { adapter ->
            adapter.detail?.let { out.add(it) }
        }
        extra.mapNotNull { it }.forEach { out.add(it) }
        return out
    }

    fun springInactive(notes: Iterable<String?>): String? {
        return notes.firstOrNull { note ->
            !note.isNullOrBlank() && note.contains(SPRING_INACTIVE_MARKER, ignoreCase = true)
        }
    }

    fun fromAdapters(adapters: Iterable<AdapterOutcome>?): String? {
        if (adapters == null) {
            return null
        }
        return springInactive(adapters.map { it.detail })
            ?: adapters.firstOrNull { AdapterOutcome.PARTIAL.equals(it.status, ignoreCase = true) }
                ?.detail
                ?.takeIf { !it.isNullOrBlank() && it.contains(SPRING_INACTIVE_MARKER, ignoreCase = true) }
    }

    fun balloonText(note: String): String = note

    fun toolWindowText(note: String): String = "PARTIAL $note"

    fun displayDecision(handshake: HandshakeDocument?, result: ReloadResult? = null): Display? {
        val note = springInactive(collect(handshake = handshake, result = result)) ?: return null
        return Display(
            status = ReloadResult.PARTIAL,
            balloon = balloonText(note),
            historyMessage = note,
            toolWindowLine = toolWindowText(note),
        )
    }
}
