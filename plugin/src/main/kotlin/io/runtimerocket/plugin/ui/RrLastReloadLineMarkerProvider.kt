package io.runtimerocket.plugin.ui

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import io.runtimerocket.plugin.watch.RrReloadHistory
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RrLastReloadLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) {
            return null
        }
        val cls = element.parent as? PsiClass ?: return null
        if (cls.nameIdentifier != element) {
            return null
        }
        val name = cls.qualifiedName ?: return null
        val project = element.project
        val at = RrReloadHistory.getInstance(project).lastReloadedAt(name) ?: return null
        val stamp = TIME.format(at.atZone(ZoneId.systemDefault()))
        return LineMarkerInfo(
            element,
            element.textRange,
            RrIcons.Gutter,
            { "Reloaded $stamp" },
            null,
            GutterIconRenderer.Alignment.RIGHT,
            { "RuntimeRocket last reload $stamp" },
        )
    }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
