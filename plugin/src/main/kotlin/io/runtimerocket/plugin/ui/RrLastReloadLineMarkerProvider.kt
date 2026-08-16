package io.runtimerocket.plugin.ui

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import io.runtimerocket.plugin.watch.RrReloadHistory
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RrLastReloadLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val name = binaryName(element) ?: return null
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

        internal fun binaryName(element: PsiElement): String? {
            val uClass = element.parent.toUElement() as? UClass
            if (uClass != null) {
                val anchor = uClass.uastAnchor?.sourcePsi
                if (anchor == element) {
                    return RrBinaryNames.fromPsiClass(uClass.javaPsi)
                }
            }
            val cls = element.parent as? PsiClass ?: return null
            if (cls.nameIdentifier != element) {
                return null
            }
            return RrBinaryNames.fromPsiClass(cls)
        }
    }
}
