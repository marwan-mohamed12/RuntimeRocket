package io.runtimerocket.plugin.watch

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Resolves the file/module Reload should compile when the tool window has no data context. */
object RrEditorFocus {
    internal val SOURCE_EXTENSIONS = setOf("java", "kt", "kts")

    fun preferredFile(project: Project, explicit: VirtualFile?): VirtualFile? {
        explicit?.takeIf { it.isValid }?.let { return it }
        val editors = FileEditorManager.getInstance(project)
        editors.selectedFiles.firstOrNull { it.isValid }?.let { return it }
        return editors.openFiles.firstOrNull { it.isValid && it.extension?.lowercase() in SOURCE_EXTENSIONS }
            ?: editors.openFiles.firstOrNull { it.isValid }
    }

    fun moduleFor(project: Project, file: VirtualFile?): Module? {
        val target = preferredFile(project, file) ?: return null
        return ModuleUtilCore.findModuleForFile(target, project)
    }

    internal fun pickFocusName(explicit: String?, selected: List<String>, open: List<String>): String? {
        if (!explicit.isNullOrBlank()) {
            return explicit
        }
        selected.firstOrNull()?.let { return it }
        return open.firstOrNull { it.substringAfterLast('.').lowercase() in SOURCE_EXTENSIONS } ?: open.firstOrNull()
    }
}
