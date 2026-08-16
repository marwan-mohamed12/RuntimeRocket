package io.runtimerocket.plugin.ui

import com.intellij.psi.PsiClass

object RrBinaryNames {
    fun fromPsiClass(cls: PsiClass): String {
        val nest = ArrayList<String>()
        var current = cls
        while (true) {
            val parent = current.containingClass ?: break
            val simple = current.name ?: return current.qualifiedName ?: ""
            nest.add(0, simple)
            current = parent
        }
        val outer = current.qualifiedName ?: current.name ?: ""
        return if (nest.isEmpty()) {
            outer
        } else {
            outer + nest.joinToString(prefix = "$", separator = "$")
        }
    }

    fun fromQualifiedAndNesting(outerQualified: String, nestedSimpleNames: List<String>): String {
        if (nestedSimpleNames.isEmpty()) {
            return outerQualified
        }
        return outerQualified + nestedSimpleNames.joinToString(prefix = "$", separator = "$")
    }
}
