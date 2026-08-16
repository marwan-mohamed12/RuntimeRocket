package io.runtimerocket.plugin.watch

import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext

class RrCompilationStatusListener : CompilationStatusListener {
    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) {
        RrReloadService.getInstance(context.project).onCompileFinished(aborted, errors, context)
    }

    override fun automakeCompilationFinished(errors: Int, warnings: Int, context: CompileContext) {
        compilationFinished(false, errors, warnings, context)
    }
}
