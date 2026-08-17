package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeFileTest {

    @TempDir
    Path temp;

    @Test
    void roundTripJson() {
        Handshake original = new Handshake(
                42L,
                53111,
                "abc123",
                "standard",
                List.of("METHOD_BODY", "NEW_TYPE"),
                "0.1.0-SNAPSHOT",
                Instant.parse("2026-08-15T12:00:00Z"));
        Handshake parsed = Handshake.parse(original.toJson());
        assertEquals(original.pid, parsed.pid);
        assertEquals(original.port, parsed.port);
        assertEquals(original.token, parsed.token);
        assertEquals(original.backend, parsed.backend);
        assertEquals(original.capabilities, parsed.capabilities);
        assertEquals(original.version, parsed.version);
        assertEquals(original.startedAt, parsed.startedAt);
        assertEquals(List.of(), parsed.notes);
    }

    @Test
    void roundTripJsonIncludesNotes() {
        Handshake original = new Handshake(
                42L,
                53111,
                "abc123",
                "standard",
                List.of("METHOD_BODY"),
                "0.1.0-SNAPSHOT",
                Instant.parse("2026-08-15T12:00:00Z"),
                List.of("Spring adapter inactive until a request hits the app or you restart with -javaagent (premain)."));
        Handshake parsed = Handshake.parse(original.toJson());
        assertEquals(original.notes, parsed.notes);
    }

    @Test
    void restrictRemovesWorldAccess() throws Exception {
        Path file = temp.resolve("handshake.json");
        Files.writeString(file, "{}");
        HandshakeFile.restrict(file);

        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> perms = posix.readAttributes().permissions();
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            String owner = acl.getOwner().getName();
            for (AclEntry entry : acl.getAcl()) {
                String name = entry.principal().getName();
                assertFalse(name.contains("Everyone"), name);
                assertFalse(name.contains("World"), name);
                if (name.equals(owner)) {
                    assertTrue(entry.permissions().contains(java.nio.file.attribute.AclEntryPermission.READ_DATA));
                }
            }
        }
    }

    @Test
    void ensureDirectoryReplacesLeftoverFile() throws Exception {
        Path handshake = temp.resolve("runtimerocket");
        Files.writeString(handshake, "not-a-directory");
        HandshakeFile.ensureDirectory(handshake);
        assertTrue(Files.isDirectory(handshake));
    }

    @Test
    void ensureDirectoryWhenDirectoryAlreadyExists() throws Exception {
        Path handshake = Files.createDirectories(temp.resolve("runtimerocket"));
        HandshakeFile.ensureDirectory(handshake);
        assertTrue(Files.isDirectory(handshake));
    }

    @Test
    void ensureDirectoryWhenPathIsDirectoryLink() throws Exception {
        Path real = Files.createDirectories(temp.resolve("real-handshake"));
        Path link = temp.resolve("runtimerocket");
        Assumptions.assumeTrue(linkDirectory(link, real), "directory links are not permitted");
        try {
            HandshakeFile.ensureDirectory(link.resolve("tokens"));
            assertTrue(Files.isDirectory(link.resolve("tokens")));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    private static boolean linkDirectory(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (Exception ignored) {
            // Windows: junctions do not need SeCreateSymbolicLinkPrivilege.
        }
        try {
            int code = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            return code == 0 && Files.isDirectory(link);
        } catch (Exception ignored) {
            return false;
        }
    }
}
