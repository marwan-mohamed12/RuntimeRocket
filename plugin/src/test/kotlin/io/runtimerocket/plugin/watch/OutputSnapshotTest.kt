package io.runtimerocket.plugin.watch

import io.runtimerocket.protocol.Protocol
import io.runtimerocket.protocol.ReloadRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class OutputSnapshotTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun buildsReloadRequestFromTempClassFiles() {
        val classes = temp.resolve("classes")
        val resources = temp.resolve("resources")
        val pkg = classes.resolve("com").resolve("example")
        Files.createDirectories(pkg)
        Files.createDirectories(resources)
        val classFile = pkg.resolve("Foo.class")
        val original = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 1, 2, 3, 4)
        Files.write(classFile, original)

        val snapshot = OutputSnapshot()
        val roots = listOf(OutputRoot(classes, "app"), OutputRoot(resources, "app"))
        snapshot.baseline(roots)

        val updated = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 9, 9, 9, 9)
        Files.write(classFile, updated)
        val props = resources.resolve("application.properties")
        Files.writeString(props, "server.port=8080")

        val diff = snapshot.diff(roots)
        assertEquals(1, diff.classes.size)
        assertEquals("com.example.Foo", diff.classes[0].binaryName)
        assertEquals(1, diff.resources.size)
        assertEquals("application.properties", diff.resources[0].classpathName)

        val request = ReloadRequestFactory.fromDiff(diff, ReloadRequest.TRIGGER_COMPILE)
        assertEquals(ReloadRequest.TRIGGER_COMPILE, request.trigger)
        assertFalse(request.byReference)
        assertEquals(1, request.classes.size)
        val payload = request.classes[0]
        assertEquals("com.example.Foo", payload.binaryName)
        assertEquals(classFile.toAbsolutePath().toString(), payload.path)
        assertEquals(RrHashes.sha256Hex(updated), payload.sha256)
        assertNotNull(payload.bytesBase64)
        assertEquals(updated.toList(), Base64.getDecoder().decode(payload.bytesBase64).toList())

        assertEquals(1, request.resources.size)
        val resource = request.resources[0]
        assertEquals("application.properties", resource.classpathName)
        assertEquals(props.toAbsolutePath().toString(), resource.path)
        assertEquals(RrHashes.sha256Hex(Files.readAllBytes(props)), resource.sha256)
        assertNull(resourceFieldBytes(resource))
    }

    @Test
    fun unchangedFilesAreNotResent() {
        val classes = temp.resolve("out")
        Files.createDirectories(classes)
        val classFile = classes.resolve("A.class")
        Files.write(classFile, byteArrayOf(1, 2, 3))
        val snapshot = OutputSnapshot()
        val roots = listOf(OutputRoot(classes))
        snapshot.baseline(roots)
        val again = snapshot.diff(roots)
        assertTrue(again.isEmpty())
    }

    @Test
    fun oversizedInlineSwitchesToByReference() {
        val classes = temp.resolve("big")
        Files.createDirectories(classes)
        val classFile = classes.resolve("Huge.class")
        Files.write(classFile, ByteArray(64) { 7 })
        val snapshot = OutputSnapshot()
        val roots = listOf(OutputRoot(classes))
        snapshot.baseline(roots)
        Files.write(classFile, ByteArray(64) { 8 })
        val request = ReloadRequestFactory.fromDiff(snapshot.diff(roots), ReloadRequest.TRIGGER_COMPILE, maxFrameBytes = 40)
        assertTrue(request.byReference)
        assertNull(request.classes[0].bytesBase64)
        assertNotNull(request.classes[0].path)
        assertTrue(request.classes[0].path.endsWith("Huge.class"))
        assertTrue(Protocol.MAX_FRAME_BYTES > 40)
    }

    private fun resourceFieldBytes(resource: io.runtimerocket.protocol.ResourcePayload): String? {
        return try {
            val field = resource.javaClass.getField("bytesBase64")
            field.get(resource) as String?
        } catch (_: NoSuchFieldException) {
            null
        }
    }
}
