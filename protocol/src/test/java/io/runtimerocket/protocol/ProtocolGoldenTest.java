package io.runtimerocket.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProtocolGoldenTest {

    static final String SESSION = "11111111-1111-1111-1111-111111111111";

    static final String HELLO =
            "{\"type\":\"hello\",\"session\":\"" + SESSION + "\",\"seq\":1,"
                    + "\"token\":\"a3f1\",\"pluginVersion\":\"0.1.0\",\"protocolVersion\":\"1\"}";

    static final String HELLO_OK =
            "{\"type\":\"hello-ok\",\"session\":\"" + SESSION + "\",\"seq\":2,"
                    + "\"backend\":\"enhanced\","
                    + "\"capabilities\":[\"METHOD_BODY\",\"ADD_METHOD\",\"ADD_FIELD\"],"
                    + "\"agentVersion\":\"0.1.0\","
                    + "\"vmName\":\"OpenJDK 64-Bit Server VM\","
                    + "\"javaVersion\":\"21.0.1\"}";

    static final String RELOAD =
            "{\"type\":\"reload\",\"session\":\"" + SESSION + "\",\"seq\":3,"
                    + "\"byReference\":false,"
                    + "\"classes\":[{\"binaryName\":\"com.example.Foo\","
                    + "\"path\":\"F:/work/app/out/Foo.class\","
                    + "\"sha256\":\"deadbeef\","
                    + "\"bytesBase64\":\"yv66vg==\"}],"
                    + "\"resources\":[{\"classpathName\":\"application.yml\","
                    + "\"path\":\"F:/work/app/src/main/resources/application.yml\","
                    + "\"sha256\":\"cafebabe\"}],"
                    + "\"trigger\":\"compile\"}";

    static final String RELOAD_BY_REF =
            "{\"type\":\"reload\",\"session\":\"" + SESSION + "\",\"seq\":4,"
                    + "\"byReference\":true,"
                    + "\"classes\":[{\"binaryName\":\"com.example.Foo\","
                    + "\"path\":\"F:/work/app/out/Foo.class\","
                    + "\"sha256\":\"deadbeef\"}],"
                    + "\"resources\":[],"
                    + "\"trigger\":\"watch\"}";

    static final String RELOAD_RESULT =
            "{\"type\":\"reload-result\",\"session\":\"" + SESSION + "\",\"seq\":5,"
                    + "\"status\":\"SUCCESS\",\"durationMs\":142,"
                    + "\"classes\":[{\"binaryName\":\"com.example.Foo\","
                    + "\"status\":\"REDEFINED\","
                    + "\"changeKinds\":[\"METHOD_BODY\"]}],"
                    + "\"adapters\":[{\"adapterId\":\"spring\",\"status\":\"SUCCESS\","
                    + "\"durationMs\":7,\"detail\":\"ok\"}],"
                    + "\"message\":\"Reloaded Foo\"}";

    static final String PING =
            "{\"type\":\"ping\",\"session\":\"" + SESSION + "\",\"seq\":6}";

    static final String PONG =
            "{\"type\":\"pong\",\"session\":\"" + SESSION + "\",\"seq\":7}";

    static final String LOG =
            "{\"type\":\"log\",\"session\":\"" + SESSION + "\",\"seq\":8,"
                    + "\"level\":\"error\",\"logger\":\"io.runtimerocket.agent\","
                    + "\"message\":\"reload failed\\njava.lang.RuntimeException: boom\"}";

    static final String STATUS =
            "{\"type\":\"status\",\"session\":\"" + SESSION + "\",\"seq\":9,"
                    + "\"phase\":\"ATTACHED\",\"backend\":\"enhanced\"}";

    static final String GOODBYE =
            "{\"type\":\"goodbye\",\"session\":\"" + SESSION + "\",\"seq\":10}";

    @Test
    void helloGolden() {
        Hello hello = new Hello();
        hello.session = SESSION;
        hello.seq = 1;
        hello.token = "a3f1";
        hello.pluginVersion = "0.1.0";
        assertEquals(HELLO, ProtocolCodec.encode(hello));
        assertEquals(hello, ProtocolCodec.decode(HELLO));
    }

    @Test
    void helloOkGolden() {
        HelloOk ok = new HelloOk();
        ok.session = SESSION;
        ok.seq = 2;
        ok.backend = "enhanced";
        ok.capabilities = List.of("METHOD_BODY", "ADD_METHOD", "ADD_FIELD");
        ok.agentVersion = "0.1.0";
        ok.vmName = "OpenJDK 64-Bit Server VM";
        ok.javaVersion = "21.0.1";
        assertEquals(HELLO_OK, ProtocolCodec.encode(ok));
        assertEquals(ok, ProtocolCodec.decode(HELLO_OK));
    }

    @Test
    void reloadGolden() {
        ReloadRequest req = new ReloadRequest();
        req.session = SESSION;
        req.seq = 3;
        req.byReference = false;
        req.classes = List.of(new ClassPayload(
                "com.example.Foo", "F:/work/app/out/Foo.class", "deadbeef", "yv66vg=="));
        req.resources = List.of(new ResourcePayload(
                "application.yml",
                "F:/work/app/src/main/resources/application.yml",
                "cafebabe"));
        req.trigger = ReloadRequest.TRIGGER_COMPILE;
        assertEquals(RELOAD, ProtocolCodec.encode(req));
        assertEquals(req, ProtocolCodec.decode(RELOAD));
    }

    @Test
    void reloadByReferenceOmitsBytes() {
        ReloadRequest req = new ReloadRequest();
        req.session = SESSION;
        req.seq = 4;
        req.byReference = true;
        req.classes = List.of(new ClassPayload(
                "com.example.Foo", "F:/work/app/out/Foo.class", "deadbeef", null));
        req.resources = List.of();
        req.trigger = ReloadRequest.TRIGGER_WATCH;
        assertEquals(RELOAD_BY_REF, ProtocolCodec.encode(req));
        assertEquals(req, ProtocolCodec.decode(RELOAD_BY_REF));
    }

    @Test
    void reloadResultGolden() {
        ReloadResult result = new ReloadResult();
        result.session = SESSION;
        result.seq = 5;
        result.status = ReloadResult.SUCCESS;
        result.durationMs = 142;
        result.classes = List.of(new ClassOutcome(
                "com.example.Foo", ClassOutcome.REDEFINED, List.of("METHOD_BODY"), null));
        result.adapters = List.of(new AdapterOutcome("spring", AdapterOutcome.SUCCESS, 7, "ok"));
        result.message = "Reloaded Foo";
        assertEquals(RELOAD_RESULT, ProtocolCodec.encode(result));
        assertEquals(result, ProtocolCodec.decode(RELOAD_RESULT));
    }

    @Test
    void pingPongGoodbyeGolden() {
        Ping ping = new Ping();
        ping.session = SESSION;
        ping.seq = 6;
        assertEquals(PING, ProtocolCodec.encode(ping));
        assertInstanceOf(Ping.class, ProtocolCodec.decode(PING));

        Pong pong = new Pong();
        pong.session = SESSION;
        pong.seq = 7;
        assertEquals(PONG, ProtocolCodec.encode(pong));
        assertInstanceOf(Pong.class, ProtocolCodec.decode(PONG));

        Goodbye goodbye = new Goodbye();
        goodbye.session = SESSION;
        goodbye.seq = 10;
        assertEquals(GOODBYE, ProtocolCodec.encode(goodbye));
        assertInstanceOf(Goodbye.class, ProtocolCodec.decode(GOODBYE));
    }

    @Test
    void logEventEscapesNewlines() {
        LogEvent log = new LogEvent();
        log.session = SESSION;
        log.seq = 8;
        log.level = "error";
        log.logger = "io.runtimerocket.agent";
        log.message = "reload failed\njava.lang.RuntimeException: boom";
        String encoded = ProtocolCodec.encode(log);
        assertEquals(LOG, encoded);
        assertFalse(encoded.contains("\n"), "raw newline would split a JSON-lines frame");
        LogEvent decoded = (LogEvent) ProtocolCodec.decode(LOG);
        assertEquals("reload failed\njava.lang.RuntimeException: boom", decoded.message);
    }

    @Test
    void statusGolden() {
        StatusEvent status = new StatusEvent();
        status.session = SESSION;
        status.seq = 9;
        status.phase = StatusEvent.ATTACHED;
        status.backend = "enhanced";
        assertEquals(STATUS, ProtocolCodec.encode(status));
        assertEquals(status, ProtocolCodec.decode(STATUS));
    }

    @Test
    void resourcePayloadHasNoBytesField() {
        assertEquals(0, java.util.Arrays.stream(ResourcePayload.class.getDeclaredFields())
                .filter(f -> "bytesBase64".equals(f.getName()))
                .count());
        assertFalse(RELOAD.contains("resources") && resourceObjectContainsBytes());
        ResourcePayload payload = new ResourcePayload("application.yml", "/tmp/application.yml", "ab");
        String json = ProtocolCodec.encode(reloadWith(payload));
        assertFalse(json.contains("bytesBase64"));
    }

    @Test
    void extraBytesOnResourceAreIgnored() {
        String json = "{\"type\":\"reload\",\"seq\":1,\"byReference\":false,"
                + "\"resources\":[{\"classpathName\":\"a.yml\",\"path\":\"/a.yml\","
                + "\"sha256\":\"aa\",\"bytesBase64\":\"SHOULD_NOT_EXIST\"}]}";
        ReloadRequest req = (ReloadRequest) ProtocolCodec.decode(json);
        assertEquals("a.yml", req.resources.get(0).classpathName);
        String encoded = ProtocolCodec.encode(req);
        assertFalse(encoded.contains("bytesBase64"));
    }

    private static boolean resourceObjectContainsBytes() {
        int start = RELOAD.indexOf("\"resources\":[");
        int end = RELOAD.indexOf(']', start);
        return RELOAD.substring(start, end).contains("bytesBase64");
    }

    private static ReloadRequest reloadWith(ResourcePayload payload) {
        ReloadRequest req = new ReloadRequest();
        req.seq = 1;
        req.resources = List.of(payload);
        return req;
    }
}
