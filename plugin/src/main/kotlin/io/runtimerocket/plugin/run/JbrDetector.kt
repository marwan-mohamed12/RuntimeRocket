package io.runtimerocket.plugin.run

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import java.nio.file.Files
import java.nio.file.Path

/**
 * Detects JetBrains Runtime / DCEVM so the patcher can add
 * `-XX:+AllowEnhancedClassRedefinition`. Detection only — never swaps the run configuration JDK.
 */
object JbrDetector {
    fun isEnhancedCapable(sdk: Sdk?): Boolean {
        if (sdk == null) {
            return false
        }
        val home = sdk.homePath?.let { Path.of(it) }
        return isEnhancedCapable(home, sdk.versionString)
    }

    fun isEnhancedCapable(home: Path?, versionString: String?): Boolean {
        if (mentionsJbrOrDcevm(versionString)) {
            return true
        }
        if (home == null) {
            return false
        }
        if (mentionsJbrOrDcevm(home.toString())) {
            return true
        }
        val release = readRelease(home)
        if (release != null && releaseLooksEnhanced(release)) {
            return true
        }
        return hasJvmLibrary(home) &&
            ((release != null && releaseLooksEnhanced(release)) || mentionsJbrOrDcevm(home.fileName.toString()))
    }

    fun jbrHome(): Path? {
        val bundled = bundledRuntime()
        if (bundled != null && isEnhancedCapable(bundled, null)) {
            return bundled
        }
        for (sdk in ProjectJdkTable.getInstance().allJdks) {
            val home = sdk.homePath?.let { Path.of(it) } ?: continue
            if (isEnhancedCapable(home, sdk.versionString)) {
                return home
            }
        }
        return null
    }

    internal fun mentionsJbrOrDcevm(text: String?): Boolean {
        if (text.isNullOrBlank()) {
            return false
        }
        val lower = text.lowercase()
        return lower.contains("jbr") ||
            lower.contains("jetbrains") ||
            lower.contains("dcevm") ||
            lower.contains("dynamic code evolution")
    }

    internal fun releaseLooksEnhanced(release: String): Boolean {
        val lower = release.lowercase()
        if (lower.contains("jetbrains")) {
            return true
        }
        if (lower.contains("dcevm")) {
            return true
        }
        return IMPLEMENTOR_JETBRAINS.containsMatchIn(release)
    }

    internal fun readRelease(home: Path): String? {
        val file = home.resolve("release")
        if (!Files.isRegularFile(file)) {
            return null
        }
        return try {
            Files.readString(file)
        } catch (_: Exception) {
            null
        }
    }

    internal fun hasJvmLibrary(home: Path): Boolean {
        val candidates =
            listOf(
                home.resolve("lib").resolve("server").resolve("jvm.dll"),
                home.resolve("bin").resolve("server").resolve("jvm.dll"),
                home.resolve("lib").resolve("server").resolve("libjvm.so"),
                home.resolve("lib").resolve("server").resolve("libjvm.dylib"),
            )
        return candidates.any { Files.isRegularFile(it) }
    }

    private fun bundledRuntime(): Path? {
        return try {
            val raw = PathManager.getBundledRuntimePath()
            if (raw.isNullOrBlank()) null else Path.of(raw)
        } catch (_: Throwable) {
            null
        }
    }

    private val IMPLEMENTOR_JETBRAINS = Regex("""IMPLEMENTOR\s*=\s*"?JetBrains""", RegexOption.IGNORE_CASE)
}
