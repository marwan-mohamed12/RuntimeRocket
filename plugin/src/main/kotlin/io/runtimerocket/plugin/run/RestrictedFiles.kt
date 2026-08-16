package io.runtimerocket.plugin.run

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import java.util.EnumSet

/** Restrict a file or directory to the current user (0600 / Windows ACL). Failure is fatal. */
object RestrictedFiles {
    fun restrict(path: Path) {
        try {
            val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
            if (posix != null) {
                val perms =
                    if (Files.isDirectory(path)) {
                        EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE,
                        )
                    } else {
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    }
                posix.setPermissions(perms)
                return
            }
            val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            if (acl != null) {
                restrictAcl(path, acl)
                return
            }
        } catch (e: IOException) {
            throw IllegalStateException("failed to restrict permissions: $path", e)
        }
        throw IllegalStateException("cannot restrict permissions: $path")
    }

    private fun restrictAcl(path: Path, acl: AclFileAttributeView) {
        var owner: UserPrincipal? = acl.owner
        if (owner == null) {
            owner = Files.getOwner(path)
        }
        if (owner == null) {
            throw IOException("no file owner for $path")
        }
        val permissions =
            EnumSet.of(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.EXECUTE,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.DELETE_CHILD,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.SYNCHRONIZE,
            )
        val entry =
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(permissions)
                .build()
        acl.acl = listOf(entry)
    }
}
