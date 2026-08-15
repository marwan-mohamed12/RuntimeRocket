package io.runtimerocket.agent.config;

import io.runtimerocket.agent.AgentStartException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Atomic handshake file writer. Restricting the file to the current user is fatal on failure. */
public final class HandshakeFile {

    private HandshakeFile() {}

    public static Path directory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "runtimerocket");
    }

    public static Path pathForPid(long pid) {
        return directory().resolve(pid + ".json");
    }

    public static Path write(Handshake handshake) {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new AgentStartException("cannot create handshake directory: " + dir, e);
        }
        restrict(dir);
        Path target = pathForPid(handshake.pid);
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.writeString(tmp, handshake.toJson(), StandardCharsets.UTF_8);
            restrict(tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            restrict(target);
            return target;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort
            }
            throw new AgentStartException("cannot write handshake file: " + target, e);
        }
    }

    public static Handshake read(Path file) {
        try {
            return Handshake.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AgentStartException("cannot read handshake file: " + file, e);
        }
    }

    public static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // shutdown must not throw
        }
    }

    public static void restrict(Path path) {
        try {
            PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            if (posix != null) {
                Set<PosixFilePermission> perms =
                        Files.isDirectory(path)
                                ? EnumSet.of(
                                        PosixFilePermission.OWNER_READ,
                                        PosixFilePermission.OWNER_WRITE,
                                        PosixFilePermission.OWNER_EXECUTE)
                                : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                posix.setPermissions(perms);
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                restrictAcl(path, acl);
                return;
            }
        } catch (IOException e) {
            throw new AgentStartException("failed to restrict handshake permissions: " + path, e);
        }
        throw new AgentStartException("cannot restrict handshake permissions: " + path);
    }

    private static void restrictAcl(Path path, AclFileAttributeView acl) throws IOException {
        UserPrincipal owner = acl.getOwner();
        if (owner == null) {
            owner = Files.getOwner(path);
        }
        if (owner == null) {
            throw new IOException("no file owner for " + path);
        }
        EnumSet<AclEntryPermission> permissions = EnumSet.of(
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
                AclEntryPermission.SYNCHRONIZE);
        AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(permissions)
                .build();
        acl.setAcl(List.of(entry));
    }
}
