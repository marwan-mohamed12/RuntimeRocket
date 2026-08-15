package io.runtimerocket.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolCodecTest {

    static Stream<Frame> allMessageTypes() {
        Hello hello = new Hello();
        hello.session = "s";
        hello.seq = 1;
        hello.token = "tok";
        hello.pluginVersion = "0.1.0";

        HelloOk helloOk = new HelloOk();
        helloOk.session = "s";
        helloOk.seq = 2;
        helloOk.backend = "standard";
        helloOk.capabilities = List.of("METHOD_BODY");
        helloOk.agentVersion = "0.1.0";
        helloOk.vmName = "vm";
        helloOk.javaVersion = "17";

        ReloadRequest reload = new ReloadRequest();
        reload.session = "s";
        reload.seq = 3;
        reload.byReference = false;
        reload.classes = List.of(new ClassPayload("a.B", "/a/B.class", "aa", "YQ=="));
        reload.resources = List.of(new ResourcePayload("x.properties", "/x.properties", "bb"));
        reload.trigger = ReloadRequest.TRIGGER_MANUAL;

        ReloadResult result = new ReloadResult();
        result.session = "s";
        result.seq = 4;
        result.status = ReloadResult.PARTIAL;
        result.durationMs = 9;
        result.classes = List.of(new ClassOutcome("a.B", ClassOutcome.SKIPPED, List.of(), "duplicate"));
        result.adapters = List.of(AdapterOutcome.ok("spring"));
        result.message = "partial";

        Ping ping = new Ping();
        ping.session = "s";
        ping.seq = 5;

        Pong pong = new Pong();
        pong.session = "s";
        pong.seq = 6;

        LogEvent log = new LogEvent();
        log.session = "s";
        log.seq = 7;
        log.level = "info";
        log.logger = "rr";
        log.message = "ok\nnext";

        StatusEvent status = new StatusEvent();
        status.session = "s";
        status.seq = 8;
        status.phase = StatusEvent.IDLE;
        status.backend = "enhanced";

        Goodbye goodbye = new Goodbye();
        goodbye.session = "s";
        goodbye.seq = 9;

        return Stream.of(hello, helloOk, reload, result, ping, pong, log, status, goodbye);
    }

    @ParameterizedTest
    @MethodSource("allMessageTypes")
    void roundTrip(Frame original) {
        String json = ProtocolCodec.encode(original);
        Frame decoded = ProtocolCodec.decode(json);
        assertEquals(original.getClass(), decoded.getClass());
        assertEquals(original, decoded);
        assertEquals(json, ProtocolCodec.encode(decoded));
    }

    @ParameterizedTest
    @MethodSource("allMessageTypes")
    void encodeLineIsSingleTerminatedFrame(Frame original) {
        String line = ProtocolCodec.encodeLine(original);
        assertTrue(line.endsWith("\n"));
        assertEquals(1, line.chars().filter(c -> c == '\n').count());
        assertEquals(original, ProtocolCodec.decode(line.substring(0, line.length() - 1)));
    }

    @Test
    void logMessageNewlineIsNotASecondFrame() {
        LogEvent log = new LogEvent();
        log.seq = 1;
        log.message = "line1\nline2\nline3";
        String encoded = ProtocolCodec.encode(log);
        assertEquals(-1, encoded.indexOf('\n'));
        assertTrue(encoded.contains("\\n"));
        String[] frames = encoded.split("\n", -1);
        assertEquals(1, frames.length);
        assertEquals("line1\nline2\nline3", ((LogEvent) ProtocolCodec.decode(encoded)).message);
    }

    @Test
    void controlCharactersAreEscaped() {
        LogEvent log = new LogEvent();
        log.seq = 1;
        log.message = "a\"b\\c\bd\fe\rg\th\u0001i";
        String encoded = ProtocolCodec.encode(log);
        assertTrue(encoded.contains("\\\""));
        assertTrue(encoded.contains("\\\\"));
        assertTrue(encoded.contains("\\b"));
        assertTrue(encoded.contains("\\f"));
        assertTrue(encoded.contains("\\r"));
        assertTrue(encoded.contains("\\t"));
        assertTrue(encoded.contains("\\u0001"));
        assertEquals(log.message, ((LogEvent) ProtocolCodec.decode(encoded)).message);
    }

    @Test
    void unicodeEscapeRoundTrip() {
        Frame decoded = ProtocolCodec.decode("{\"type\":\"log\",\"seq\":1,\"message\":\"\\u0041\\nB\"}");
        assertEquals("A\nB", ((LogEvent) decoded).message);
    }

    @Test
    void unknownTypeYieldsBareFrame() {
        Frame frame = ProtocolCodec.decode("{\"type\":\"future-msg\",\"session\":\"s\",\"seq\":3}");
        assertEquals(Frame.class, frame.getClass());
        assertEquals("future-msg", frame.type);
        assertEquals("s", frame.session);
        assertEquals(3L, frame.seq);
    }

    @Test
    void additiveUnknownFieldsAreIgnored() {
        Hello hello = (Hello) ProtocolCodec.decode(
                "{\"type\":\"hello\",\"seq\":1,\"token\":\"t\",\"extra\":true,\"n\":1}");
        assertEquals("t", hello.token);
        assertEquals(Protocol.VERSION, hello.protocolVersion);
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(""));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("[]"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"log\""));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"log\",\"seq\":1} extra"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"log\",\"message\":\"unterminated}"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":1}"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{}"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"\"}"));
    }

    @Test
    void unescapedControlCharInStringIsRejected() {
        String bad = "{\"type\":\"log\",\"message\":\"line1\nline2\"}";
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(bad));
    }

    @Test
    void integersOnly() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"ping\",\"seq\":1.5}"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("{\"type\":\"ping\",\"seq\":01}"));
    }

    @Test
    void encodeRequiresType() {
        Frame frame = new Frame();
        assertThrows(ProtocolException.class, () -> ProtocolCodec.encode(frame));
    }

    @Test
    void utf8HelpersRoundTrip() {
        StatusEvent status = new StatusEvent();
        status.seq = 1;
        status.phase = StatusEvent.SHUTDOWN;
        byte[] utf8 = ProtocolCodec.encodeUtf8(status);
        assertEquals(ProtocolCodec.encode(status), new String(utf8, StandardCharsets.UTF_8));
        assertEquals(status, ProtocolCodec.decodeUtf8(utf8));
    }

    @Test
    void adapterOutcomeOkFactory() {
        AdapterOutcome ok = AdapterOutcome.ok("spring");
        assertEquals("spring", ok.adapterId);
        assertEquals(AdapterOutcome.SUCCESS, ok.status);
        assertEquals(0L, ok.durationMs);
        assertNull(ok.detail);
    }

    @Test
    void discriminatorsMatchDesign() {
        assertEquals("hello", FrameTypes.HELLO);
        assertEquals("hello-ok", FrameTypes.HELLO_OK);
        assertEquals("reload", FrameTypes.RELOAD);
        assertEquals("reload-result", FrameTypes.RELOAD_RESULT);
        assertEquals("ping", FrameTypes.PING);
        assertEquals("pong", FrameTypes.PONG);
        assertEquals("log", FrameTypes.LOG);
        assertEquals("status", FrameTypes.STATUS);
        assertEquals("goodbye", FrameTypes.GOODBYE);
        assertEquals("1", Protocol.VERSION);
        assertInstanceOf(Hello.class, ProtocolCodec.decode("{\"type\":\"hello\"}"));
        assertInstanceOf(HelloOk.class, ProtocolCodec.decode("{\"type\":\"hello-ok\"}"));
        assertInstanceOf(ReloadRequest.class, ProtocolCodec.decode("{\"type\":\"reload\"}"));
        assertInstanceOf(ReloadResult.class, ProtocolCodec.decode("{\"type\":\"reload-result\"}"));
    }

    @Test
    void whitespaceAroundObjectIsAccepted() {
        Frame frame = ProtocolCodec.decode("  {\"type\":\"pong\",\"seq\":2} \r\n");
        assertInstanceOf(Pong.class, frame);
        assertEquals(2L, frame.seq);
    }

    @Test
    void mutableDecodedLists() {
        ReloadRequest req = (ReloadRequest) ProtocolCodec.decode(
                "{\"type\":\"reload\",\"classes\":[],\"resources\":[]}");
        req.classes.add(new ClassPayload("a.B", null, null, null));
        assertEquals(1, req.classes.size());
    }
}
