package io.runtimerocket.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void frameAtExactLimitIsAccepted() {
        LogEvent probe = new LogEvent();
        probe.seq = 1;
        probe.message = "";
        int overhead = ProtocolCodec.encode(probe).getBytes(StandardCharsets.UTF_8).length;
        int pad = Protocol.MAX_FRAME_BYTES - overhead;
        assertTrue(pad > 0);

        LogEvent atLimit = new LogEvent();
        atLimit.seq = 1;
        atLimit.message = "x".repeat(pad);
        String json = ProtocolCodec.encode(atLimit);
        assertEquals(Protocol.MAX_FRAME_BYTES, json.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(atLimit.message, ((LogEvent) ProtocolCodec.decode(json)).message);

        LogEvent over = new LogEvent();
        over.seq = 1;
        over.message = "x".repeat(pad + 1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.encode(over));
    }

    @Test
    void maxFrameBytesIs16MiB() {
        assertTrue(Protocol.MAX_FRAME_BYTES == 16 * 1024 * 1024);
        assertTrue(ProtocolCodec.MAX_FRAME_BYTES == Protocol.MAX_FRAME_BYTES);
    }
}
