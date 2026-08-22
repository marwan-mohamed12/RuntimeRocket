package io.runtimerocket.plugin.watch

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Disk fingerprints for compiler output. Only `.class` and watched resource extensions are tracked. */
class OutputSnapshot {
    data class Fingerprint(val mtime: Long, val size: Long, val sha256: String)

    data class ChangedFile(
        val path: Path,
        val root: Path,
        val sha256: String,
        val bytes: ByteArray,
        val classFile: Boolean,
    ) {
        val binaryName: String
            get() = if (classFile) classBinaryName(root, path) else ""

        val classpathName: String
            get() = relativeName(root, path)
    }

    data class Diff(
        val classes: List<ChangedFile>,
        val resources: List<ChangedFile>,
        val missingModules: List<String> = emptyList(),
    ) {
        fun isEmpty(): Boolean = classes.isEmpty() && resources.isEmpty()
    }

    private val files = ConcurrentHashMap<Path, Fingerprint>()

    fun baseline(roots: List<OutputRoot>) {
        files.clear()
        files.putAll(scan(roots))
    }

    /** Drop remembered fingerprints. Next peek treats every file as new until [baseline] or [commit]. */
    fun invalidate() {
        files.clear()
    }

    fun currentFingerprints(): Map<Path, Fingerprint> = files.toMap()

    data class Peek(
        val diff: Diff,
        val fingerprints: Map<Path, Fingerprint>,
    )

    /** Compare disk to stored fingerprints without advancing the snapshot. */
    fun peek(roots: List<OutputRoot>): Peek {
        val scanned = scan(roots)
        return Peek(toDiff(scanned, roots), scanned)
    }

    fun commit(fingerprints: Map<Path, Fingerprint>) {
        files.clear()
        files.putAll(fingerprints)
    }

    fun diff(roots: List<OutputRoot>): Diff {
        val peek = peek(roots)
        commit(peek.fingerprints)
        return peek.diff
    }

    private fun toDiff(scanned: Map<Path, Fingerprint>, roots: List<OutputRoot>): Diff {
        val changed = mutableListOf<ChangedFile>()
        for ((path, fingerprint) in scanned) {
            val previous = files[path]
            if (previous == null || previous.sha256 != fingerprint.sha256) {
                val root = rootFor(path, roots) ?: continue
                val bytes = readBytes(path) ?: continue
                changed.add(
                    ChangedFile(
                        path = path,
                        root = root.path,
                        sha256 = fingerprint.sha256,
                        bytes = bytes,
                        classFile = isClassFile(path),
                    ),
                )
            }
        }
        return Diff(
            classes = changed.filter { it.classFile },
            resources = changed.filter { !it.classFile },
            missingModules = roots.filter { !Files.isDirectory(it.path) }.map { it.moduleName }.filter { it.isNotEmpty() }.distinct(),
        )
    }

    private fun scan(roots: List<OutputRoot>): Map<Path, Fingerprint> {
        val out = LinkedHashMap<Path, Fingerprint>()
        for (root in roots) {
            if (!Files.isDirectory(root.path)) {
                continue
            }
            Files.walk(root.path).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { path ->
                    if (isClassFile(path) || isWatchedResource(path)) {
                        val bytes = readBytes(path) ?: return@forEach
                        val mtime =
                            try {
                                Files.getLastModifiedTime(path).toMillis()
                            } catch (_: Exception) {
                                0L
                            }
                        out[path.toAbsolutePath().normalize()] =
                            Fingerprint(mtime = mtime, size = bytes.size.toLong(), sha256 = RrHashes.sha256Hex(bytes))
                    }
                }
            }
        }
        return out
    }

    companion object {
        val RESOURCE_EXTENSIONS =
            setOf("properties", "xml", "yml", "yaml", "json", "html", "js", "css", "sql", "impex", "jsp")

        fun isClassFile(path: Path): Boolean = path.fileName.toString().endsWith(".class", ignoreCase = true)

        fun isWatchedResource(path: Path): Boolean {
            val name = path.fileName.toString()
            val dot = name.lastIndexOf('.')
            if (dot < 0) {
                return false
            }
            return name.substring(dot + 1).lowercase() in RESOURCE_EXTENSIONS
        }

        fun classBinaryName(root: Path, file: Path): String {
            return relativeName(root, file).removeSuffix(".class").replace('/', '.')
        }

        fun relativeName(root: Path, file: Path): String {
            val rel =
                try {
                    root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString()
                } catch (_: IllegalArgumentException) {
                    file.fileName.toString()
                }
            return rel.replace('\\', '/')
        }

        private fun rootFor(path: Path, roots: List<OutputRoot>): OutputRoot? {
            val normalized = path.toAbsolutePath().normalize()
            return roots
                .filter { normalized.startsWith(it.path.toAbsolutePath().normalize()) }
                .maxByOrNull { it.path.toAbsolutePath().normalize().nameCount }
        }

        private fun readBytes(path: Path): ByteArray? {
            return try {
                Files.readAllBytes(path)
            } catch (_: Exception) {
                null
            }
        }
    }
}
