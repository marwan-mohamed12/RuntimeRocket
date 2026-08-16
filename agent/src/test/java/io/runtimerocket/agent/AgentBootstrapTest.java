package io.runtimerocket.agent;

import io.runtimerocket.agent.config.Handshake;
import io.runtimerocket.agent.config.HandshakeFile;
import io.runtimerocket.agent.spi.LateAttachPartialAdapter;
import io.runtimerocket.protocol.Hello;
import io.runtimerocket.protocol.HelloOk;
import io.runtimerocket.protocol.Ping;
import io.runtimerocket.protocol.ProtocolCodec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class AgentBootstrapTest {

    @TempDir
    Path temp;

    @AfterEach
    void tearDown() {
        LateAttachPartialAdapter.disable();
        AgentTestSupport.stop();
    }

    @Test
    void writesHandshakeFileAndAcceptsHello() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        Path handshakePath = AgentRuntime.get().handshakePath();
        assertNotNull(handshakePath);
        assertTrue(Files.isRegularFile(handshakePath));
        assertEquals(HandshakeFile.pathForPid(ProcessHandle.current().pid()), handshakePath);

        Handshake handshake = HandshakeFile.read(handshakePath);
        assertEquals(ProcessHandle.current().pid(), handshake.pid);
        assertEquals(AgentRuntime.get().port(), handshake.port);
        assertEquals(token, handshake.token);
        assertEquals("standard", handshake.backend);
        assertTrue(handshake.capabilities.contains("METHOD_BODY"));
        assertNotNull(handshake.startedAt);
        assertRestricted(handshakePath);

        try (AgentClient client = new AgentClient(handshake.port)) {
            HelloOk ok = client.handshake(token);
            assertEquals("standard", ok.backend);
            assertTrue(ok.capabilities.contains("METHOD_BODY"));
            assertNotNull(ok.agentVersion);
            assertNotNull(client.ping());
        }
    }

    @Test
    void tokenMismatchClosesTheSocket() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);
        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            Hello hello = new Hello();
            hello.token = "definitely-not-" + token;
            client.writeRaw(ProtocolCodec.encodeLine(hello));
            assertNull(client.read());
        }
    }

    @Test
    void firstMessageMustBeHello() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", AgentRuntime.get().port()), 5_000);
            socket.setSoTimeout(3_000);
            socket.getOutputStream().write(ProtocolCodec.encodeLine(new Ping()).getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void lateAttachWritesAdapterNotesToHandshake() {
        LateAttachPartialAdapter.enable();
        String token = AgentTestSupport.token();
        AgentTestSupport.startLate(temp, token);

        Handshake handshake = HandshakeFile.read(AgentRuntime.get().handshakePath());
        assertTrue(
                handshake.notes.contains(LateAttachPartialAdapter.DETAIL),
                String.valueOf(handshake.notes));
    }

    @Test
    void deletesHandshakeOnStop() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);
        Path handshakePath = AgentRuntime.get().handshakePath();
        assertTrue(Files.isRegularFile(handshakePath));
        AgentTestSupport.stop();
        assertFalse(Files.exists(handshakePath));
    }

    private static void assertRestricted(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> perms = posix.readAttributes().permissions();
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            for (AclEntry entry : acl.getAcl()) {
                String name = entry.principal().getName();
                assertFalse(name.contains("Everyone"), name);
            }
        }
    }
}
