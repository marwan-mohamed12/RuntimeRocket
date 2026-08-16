package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

class RestrictedFilesTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun restrictRemovesWorldAccess() {
        val file = temp.resolve("token")
        Files.writeString(file, "deadbeef")
        RestrictedFiles.restrict(file)

        val posix = Files.getFileAttributeView(file, PosixFileAttributeView::class.java)
        if (posix != null) {
            val perms = posix.readAttributes().permissions()
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ))
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
            return
        }
        val acl = Files.getFileAttributeView(file, AclFileAttributeView::class.java)
        if (acl != null) {
            val owner = acl.owner.name
            for (entry in acl.acl) {
                val name = entry.principal().name
                assertFalse(name.contains("Everyone"), name)
                assertFalse(name.contains("World"), name)
                if (name == owner) {
                    assertTrue(entry.permissions().contains(java.nio.file.attribute.AclEntryPermission.READ_DATA))
                }
            }
        }
    }

    @Test
    fun tokenFileIsSixtyFourHexChars() {
        val token = TokenFactory.generate()
        assertTrue(token.matches(Regex("[0-9a-f]{64}")), token)
    }

    @Test
    fun writeTokenFileIsRestricted() {
        val dir = temp.resolve("tokens")
        val file = TokenFactory.writeTokenFile("abc", dir)
        assertTrue(Files.isRegularFile(file))
        assertTrue(Files.readString(file).trim() == "abc")
        val posix = Files.getFileAttributeView(file, PosixFileAttributeView::class.java)
        if (posix != null) {
            assertFalse(posix.readAttributes().permissions().contains(PosixFilePermission.OTHERS_READ))
        }
    }
}
