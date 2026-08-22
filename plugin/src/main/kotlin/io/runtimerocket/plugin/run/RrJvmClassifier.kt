package io.runtimerocket.plugin.run

import com.sun.tools.attach.VirtualMachineDescriptor

/** Labels local JVMs so Attach can hide IDE/Gradle noise and highlight Hybris. */
object RrJvmClassifier {
    enum class Kind {
        HYBRIS,
        APP,
        IDE,
        BUILD,
        OTHER,
    }

    data class Choice(
        val pid: String,
        val displayName: String,
        val kind: Kind,
        val label: String,
    )

    fun classify(displayName: String): Kind {
        val n = displayName.lowercase()
        return when {
            isHybris(n) -> Kind.HYBRIS
            isIde(n) -> Kind.IDE
            isBuild(n) -> Kind.BUILD
            isApp(n) -> Kind.APP
            else -> Kind.OTHER
        }
    }

    fun isNoise(kind: Kind): Boolean = kind == Kind.IDE || kind == Kind.BUILD

    fun label(pid: String, displayName: String, kind: Kind = classify(displayName)): String {
        val shortName = displayName.ifBlank { "JVM" }
        val tag =
            when (kind) {
                Kind.HYBRIS -> "Hybris / Tomcat"
                Kind.APP -> "Application"
                Kind.IDE -> "IntelliJ"
                Kind.BUILD -> "Build tool"
                Kind.OTHER -> "JVM"
            }
        return "$pid — $tag — $shortName"
    }

    fun choices(descriptors: List<VirtualMachineDescriptor>, selfPid: String): List<Choice> {
        return descriptors
            .filter { it.id() != selfPid }
            .map { descriptor ->
                val name = descriptor.displayName().ifBlank { "JVM" }
                val kind = classify(name)
                Choice(descriptor.id(), name, kind, label(descriptor.id(), name, kind))
            }
    }

    /** Recommended first (Hybris, then app). IDE/Gradle hidden unless they are the only JVMs. */
    fun chooserLines(choices: List<Choice>): List<String> {
        val visible = choices.filterNot { isNoise(it.kind) }.ifEmpty { choices }
        return visible.sortedWith(compareBy<Choice> { rank(it.kind) }.thenBy { it.pid }).map { it.label }
    }

    fun defaultSelection(lines: List<String>): String = lines.firstOrNull().orEmpty()

    internal fun isHybris(lower: String): Boolean {
        return lower.contains("catalina") ||
            lower.contains("hybris") ||
            lower.contains("hybrisserver") ||
            lower.contains("de.hybris") ||
            lower.contains("wrappersimpleapp") ||
            lower.contains("tomcat") && (lower.contains("bootstrap") || lower.contains("hybris") || lower.contains("platform")) ||
            (lower.contains("bootstrap") && (lower.contains("org.apache") || lower.contains("catalina") || lower.contains("tomcat")))
    }

    internal fun isIde(lower: String): Boolean {
        return lower.contains("com.intellij") ||
            lower.contains("intellij") ||
            lower.contains("jetbrains") ||
            lower.contains("idea64") ||
            lower.contains("idea.exe") ||
            lower.contains("fsnotifier")
    }

    internal fun isBuild(lower: String): Boolean {
        return lower.contains("gradle") ||
            lower.contains("kotlincompile") ||
            lower.contains("compilerdaemon") ||
            lower.contains("maven") ||
            lower.contains("jps.cmdline") ||
            lower.contains("org.jetbrains.jps")
    }

    internal fun isApp(lower: String): Boolean {
        return lower.contains("springboot") ||
            lower.contains("org.springframework") ||
            lower.contains(".main") ||
            lower.contains("application")
    }

    private fun rank(kind: Kind): Int {
        return when (kind) {
            Kind.HYBRIS -> 0
            Kind.APP -> 1
            Kind.OTHER -> 2
            Kind.BUILD -> 3
            Kind.IDE -> 4
        }
    }
}
