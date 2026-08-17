package io.runtimerocket.plugin.ui

import com.intellij.ui.JBColor
import io.runtimerocket.protocol.AdapterOutcome
import io.runtimerocket.protocol.ClassOutcome
import io.runtimerocket.protocol.ReloadResult
import java.awt.Color

/** Theme-aware colors for the tool window. Matches design.md: gray / blue / green / amber / red. */
object RrUiColors {
    val success: Color = JBColor(Color(0x1F8A4C), Color(0x3DDC97))
    val attached: Color = JBColor(Color(0x1D6FA5), Color(0x5CB3E8))
    val partial: Color = JBColor(Color(0xB26A00), Color(0xF0B429))
    val error: Color = JBColor(Color(0xC42B3A), Color(0xFF6B6B))
    val muted: Color = JBColor(Color(0x5C6570), Color(0xA0A8B0))
    val timestamp: Color = JBColor(Color(0x4A5560), Color(0x8B949E))
    val text: Color = JBColor(Color(0x1F2328), Color(0xE6EDF3))
    val header: Color = JBColor(Color(0x0F1419), Color(0xF0F3F6))
    val accent: Color = JBColor(Color(0x1D6FA5), Color(0x79C0FF))
    val consoleBg: Color = JBColor(Color(0xF4F7FA), Color(0x1B1D21))
    val headerBg: Color = JBColor(Color(0xEAEFF4), Color(0x25282E))
    val lastResultBg: Color = JBColor(Color(0xFBFCFD), Color(0x21242A))

    fun forStatus(status: String?): Color {
        return when (status?.uppercase()) {
            ReloadResult.SUCCESS, ClassOutcome.REDEFINED, ClassOutcome.DEFINED, AdapterOutcome.SUCCESS -> success
            ReloadResult.PARTIAL, AdapterOutcome.PARTIAL -> partial
            ReloadResult.RESTART_REQUIRED -> partial
            ReloadResult.FAILED, ClassOutcome.FAILED, AdapterOutcome.FAILED -> error
            ClassOutcome.SKIPPED -> muted
            else -> text
        }
    }

    fun forPhase(phase: RrStatus.Phase, backend: String = ""): Color {
        return when (phase) {
            RrStatus.Phase.SUCCESS -> success
            RrStatus.Phase.PARTIAL -> partial
            RrStatus.Phase.RESTART_REQUIRED, RrStatus.Phase.FAILED -> error
            RrStatus.Phase.ATTACHED ->
                if (backend.contains("enhanced", ignoreCase = true)) success else attached
            RrStatus.Phase.WAITING, RrStatus.Phase.COMPILING, RrStatus.Phase.RELOADING -> attached
            RrStatus.Phase.IDLE, RrStatus.Phase.NOT_ATTACHED -> muted
        }
    }

    fun lastResultTint(status: String?): Color {
        return when (status?.uppercase()) {
            ReloadResult.SUCCESS -> JBColor(Color(0xE8F6EE), Color(0x173024))
            ReloadResult.PARTIAL -> JBColor(Color(0xFFF6E5), Color(0x2C2414))
            ReloadResult.RESTART_REQUIRED, ReloadResult.FAILED -> JBColor(Color(0xFDECEC), Color(0x2C181B))
            else -> lastResultBg
        }
    }
}
