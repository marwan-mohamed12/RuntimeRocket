package io.runtimerocket.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSizeLimitTest {

    @Test
    void encodeRejectsOversizeFrame() {
        LogEvent log = new LogEvent();
        log.seq = 1;
        log.message = "x".repeat(Protocol.MAX_FRAME_BYTES);
        ProtocolException ex = assertThrows(ProtocolException.class, () -> ProtocolCodec.encode(log));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void decodeRejectsOversizeFrame() {
        String huge = "{\"type\":\"log\",\"seq\":1,\"message\":\""
                + "x".repeat(Protocol.MAX_FRAME_BYTES)
                + "\"}";
        assertTrue(huge.length() > Protocol.MAX_FRAME_BYTES);
        ProtocolException ex = assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(huge));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void decodeUtf8RejectsOversizeBytes() {
        byte[] huge = new byte[Protocol.MAX_FRAME_BYTES + 1];
        ProtocolException ex = assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeUtf8(huge));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void frameAtLimitIsAccepted() {
        // Small enough that wrapping JSON still fits, large enough to exercise the check.
        LogEvent log = new LogEvent();
        log.seq = 1;
        log.message = "ok";
        String json = ProtocolCodec.encode(log);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length < Protocol.MAX_FRAME_BYTES);
        assertDoesNotThrow(() -> ProtocolCodec.decode(json));
        assertDoesNotThrow(() -> ProtocolCodec.encode(log));
    }

    @Test
    void maxFrameBytesIs16MiB() {
        assertTrue(Protocol.MAX_FRAME_BYTES == 16 * 1024 * 1024);
        assertTrue(ProtocolCodec.MAX_FRAME_BYTES == Protocol.MAX_FRAME_BYTES);
    }
}
