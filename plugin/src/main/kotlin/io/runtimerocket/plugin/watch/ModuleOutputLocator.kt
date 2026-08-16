package io.runtimerocket.plugin.watch

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves compiler output directories. Unknown layouts (including KMP) are not guessed —
 * the caller reports "no compiler output found".
 */
object ModuleOutputLocator {
    val GRADLE: ProjectSystemId = ProjectSystemId("GRADLE")
    val MAVEN: ProjectSystemId = ProjectSystemId("Maven")

    val GRADLE_CLASS_DIRS =
        listOf(
            "build/classes/java/main",
            "build/classes/kotlin/main",
        )
    val GRADLE_RESOURCE_DIRS = listOf("build/resources/main")
    val GRADLE_TEST_CLASS_DIRS =
        listOf(
            "build/classes/java/test",
            "build/classes/kotlin/test",
        )
    val GRADLE_TEST_RESOURCE_DIRS = listOf("build/resources/test")
    val MAVEN_CLASS_DIRS = listOf("target/classes")
    val MAVEN_TEST_CLASS_DIRS = listOf("target/test-classes")

    fun paths(project: Project, context: CompileContext?, includeTests: Boolean): List<OutputRoot> {
        val modules = affectedModules(project, context)
        return modules.flatMap { paths(it, context, includeTests) }
    }

    fun paths(module: Module, context: CompileContext?, includeTests: Boolean): List<OutputRoot> {
        val found = LinkedHashMap<Path, OutputRoot>()
        addIfPresent(found, compilerOutput(module, context, tests = false), module.name)
        if (includeTests) {
            addIfPresent(found, compilerOutput(module, context, tests = true), module.name)
        }
        val gradle = isGradle(module)
        val maven = isMaven(module)
        for (contentRoot in contentRoots(module)) {
            for (relative in wellKnownRelativeDirs(includeTests, gradle, maven)) {
                addIfPresent(found, contentRoot.resolve(relative), module.name)
            }
        }
        return found.values.toList()
    }

    fun wellKnownOutputs(
        contentRoot: Path,
        includeTests: Boolean,
        gradle: Boolean,
        maven: Boolean,
    ): List<Path> {
        return wellKnownRelativeDirs(includeTests, gradle, maven)
            .map { contentRoot.resolve(it) }
            .filter { Files.isDirectory(it) }
    }

    fun wellKnownRelativeDirs(includeTests: Boolean, gradle: Boolean, maven: Boolean): List<String> {
        val dirs = mutableListOf<String>()
        if (gradle) {
            dirs.addAll(GRADLE_CLASS_DIRS)
            dirs.addAll(GRADLE_RESOURCE_DIRS)
            if (includeTests) {
                dirs.addAll(GRADLE_TEST_CLASS_DIRS)
                dirs.addAll(GRADLE_TEST_RESOURCE_DIRS)
            }
        }
        if (maven) {
            dirs.addAll(MAVEN_CLASS_DIRS)
            if (includeTests) {
                dirs.addAll(MAVEN_TEST_CLASS_DIRS)
            }
        }
        return dirs
    }

    fun isGradle(module: Module): Boolean {
        return ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE, module)
    }

    fun isMaven(module: Module): Boolean {
        return ExternalSystemApiUtil.isExternalSystemAwareModule(MAVEN, module)
    }

    private fun affectedModules(project: Project, context: CompileContext?): Array<Module> {
        val fromContext = context?.compileScope?.affectedModules
        if (fromContext != null && fromContext.isNotEmpty()) {
            return fromContext
        }
        return ModuleManager.getInstance(project).modules
    }

    private fun compilerOutput(module: Module, context: CompileContext?, tests: Boolean): Path? {
        if (context != null) {
            val fromContext =
                if (tests) {
                    context.getModuleOutputDirectoryForTests(module)
                } else {
                    context.getModuleOutputDirectory(module)
                }
            virtualToPath(fromContext)?.let { return it }
        }
        val extension = CompilerModuleExtension.getInstance(module) ?: return null
        val virtual =
            if (tests) {
                extension.compilerOutputPathForTests
            } else {
                extension.compilerOutputPath
            }
        virtualToPath(virtual)?.let { return it }
        val url =
            if (tests) {
                extension.compilerOutputUrlForTests
            } else {
                extension.compilerOutputUrl
            }
        return urlToPath(url)
    }

    private fun contentRoots(module: Module): List<Path> {
        return ModuleRootManager.getInstance(module).contentRoots.mapNotNull { virtualToPath(it) }
    }

    private fun addIfPresent(into: MutableMap<Path, OutputRoot>, path: Path?, moduleName: String) {
        if (path == null) {
            return
        }
        val normalized = path.toAbsolutePath().normalize()
        if (Files.isDirectory(normalized)) {
            into.putIfAbsent(normalized, OutputRoot(normalized, moduleName))
        }
    }

    internal fun virtualToPath(file: VirtualFile?): Path? {
        if (file == null) {
            return null
        }
        return Path.of(FileUtil.toSystemDependentName(file.path))
    }

    internal fun urlToPath(url: String?): Path? {
        if (url.isNullOrBlank()) {
            return null
        }
        val path = com.intellij.openapi.vfs.VfsUtilCore.urlToPath(url)
        if (path.isBlank()) {
            return null
        }
        return Path.of(FileUtil.toSystemDependentName(path))
    }
}
