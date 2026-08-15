package io.runtimerocket.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Zero-dependency JSON-lines codec for the closed RR/1 message set.
 * Encode produces one compact object with no raw newlines; decode rejects
 * any UTF-8 payload larger than {@link Protocol#MAX_FRAME_BYTES}.
 */
public final class ProtocolCodec {

    public static final int MAX_FRAME_BYTES = Protocol.MAX_FRAME_BYTES;

    private ProtocolCodec() {}

    public static String encode(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.type == null || frame.type.isEmpty()) {
            throw new ProtocolException("missing type");
        }
        String json = write(frame);
        checkSize(json);
        return json;
    }

    public static byte[] encodeUtf8(Frame frame) {
        return encode(frame).getBytes(StandardCharsets.UTF_8);
    }

    public static String encodeLine(Frame frame) {
        return encode(frame) + '\n';
    }

    public static Frame decode(String json) {
        Objects.requireNonNull(json, "json");
        checkSize(json);
        Object parsed = new JsonReader(json).parse();
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ProtocolException("frame must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) map;
        return readFrame(obj);
    }

    public static Frame decodeUtf8(byte[] utf8) {
        Objects.requireNonNull(utf8, "utf8");
        if (utf8.length > MAX_FRAME_BYTES) {
            throw new ProtocolException("framed message exceeds " + MAX_FRAME_BYTES + " bytes");
        }
        return decode(new String(utf8, StandardCharsets.UTF_8));
    }

    static void checkSize(String text) {
        if (text.length() > MAX_FRAME_BYTES) {
            throw new ProtocolException("framed message exceeds " + MAX_FRAME_BYTES + " bytes");
        }
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_FRAME_BYTES) {
            throw new ProtocolException("framed message exceeds " + MAX_FRAME_BYTES + " bytes");
        }
    }

    private static String write(Frame frame) {
        JsonWriter w = new JsonWriter();
        w.beginObject();
        w.field("type", frame.type);
        w.field("session", frame.session);
        w.field("seq", frame.seq);
        if (frame instanceof Hello hello) {
            w.field("token", hello.token);
            w.field("pluginVersion", hello.pluginVersion);
            w.field("protocolVersion", hello.protocolVersion);
        } else if (frame instanceof HelloOk helloOk) {
            w.field("backend", helloOk.backend);
            w.fieldStrings("capabilities", helloOk.capabilities);
            w.field("agentVersion", helloOk.agentVersion);
            w.field("vmName", helloOk.vmName);
            w.field("javaVersion", helloOk.javaVersion);
        } else if (frame instanceof ReloadRequest reload) {
            w.field("byReference", reload.byReference);
            writeClasses(w, reload.classes);
            writeResources(w, reload.resources);
            w.field("trigger", reload.trigger);
        } else if (frame instanceof ReloadResult result) {
            w.field("status", result.status);
            w.field("durationMs", result.durationMs);
            writeClassOutcomes(w, result.classes);
            writeAdapterOutcomes(w, result.adapters);
            w.field("message", result.message);
        } else if (frame instanceof LogEvent log) {
            w.field("level", log.level);
            w.field("logger", log.logger);
            w.field("message", log.message);
        } else if (frame instanceof StatusEvent status) {
            w.field("phase", status.phase);
            w.field("backend", status.backend);
        }
        w.endObject();
        return w.toString();
    }

    private static void writeClasses(JsonWriter w, List<ClassPayload> classes) {
        if (classes == null) {
            return;
        }
        w.name("classes");
        w.beginArray();
        for (ClassPayload c : classes) {
            w.beginObject();
            w.field("binaryName", c.binaryName);
            w.field("path", c.path);
            w.field("sha256", c.sha256);
            w.field("bytesBase64", c.bytesBase64);
            w.endObject();
        }
        w.endArray();
    }

    private static void writeResources(JsonWriter w, List<ResourcePayload> resources) {
        if (resources == null) {
            return;
        }
        w.name("resources");
        w.beginArray();
        for (ResourcePayload r : resources) {
            w.beginObject();
            w.field("classpathName", r.classpathName);
            w.field("path", r.path);
            w.field("sha256", r.sha256);
            w.endObject();
        }
        w.endArray();
    }

    private static void writeClassOutcomes(JsonWriter w, List<ClassOutcome> classes) {
        if (classes == null) {
            return;
        }
        w.name("classes");
        w.beginArray();
        for (ClassOutcome c : classes) {
            w.beginObject();
            w.field("binaryName", c.binaryName);
            w.field("status", c.status);
            w.fieldStrings("changeKinds", c.changeKinds);
            w.field("reason", c.reason);
            w.endObject();
        }
        w.endArray();
    }

    private static void writeAdapterOutcomes(JsonWriter w, List<AdapterOutcome> adapters) {
        if (adapters == null) {
            return;
        }
        w.name("adapters");
        w.beginArray();
        for (AdapterOutcome a : adapters) {
            w.beginObject();
            w.field("adapterId", a.adapterId);
            w.field("status", a.status);
            w.field("durationMs", a.durationMs);
            w.field("detail", a.detail);
            w.endObject();
        }
        w.endArray();
    }

    private static Frame readFrame(Map<String, Object> obj) {
        String type = requireString(obj, "type");
        String session = optionalString(obj, "session");
        long seq = optionalLong(obj, "seq", 0L);
        Frame frame = switch (type) {
            case FrameTypes.HELLO -> readHello(obj);
            case FrameTypes.HELLO_OK -> readHelloOk(obj);
            case FrameTypes.RELOAD -> readReload(obj);
            case FrameTypes.RELOAD_RESULT -> readReloadResult(obj);
            case FrameTypes.PING -> new Ping();
            case FrameTypes.PONG -> new Pong();
            case FrameTypes.LOG -> readLog(obj);
            case FrameTypes.STATUS -> readStatus(obj);
            case FrameTypes.GOODBYE -> new Goodbye();
            default -> new Frame(type);
        };
        frame.type = type;
        frame.session = session;
        frame.seq = seq;
        return frame;
    }

    private static Hello readHello(Map<String, Object> obj) {
        Hello hello = new Hello();
        hello.token = optionalString(obj, "token");
        hello.pluginVersion = optionalString(obj, "pluginVersion");
        String version = optionalString(obj, "protocolVersion");
        if (version != null) {
            hello.protocolVersion = version;
        }
        return hello;
    }

    private static HelloOk readHelloOk(Map<String, Object> obj) {
        HelloOk helloOk = new HelloOk();
        helloOk.backend = optionalString(obj, "backend");
        helloOk.capabilities = optionalStringList(obj, "capabilities");
        helloOk.agentVersion = optionalString(obj, "agentVersion");
        helloOk.vmName = optionalString(obj, "vmName");
        helloOk.javaVersion = optionalString(obj, "javaVersion");
        return helloOk;
    }

    private static ReloadRequest readReload(Map<String, Object> obj) {
        ReloadRequest reload = new ReloadRequest();
        reload.byReference = optionalBoolean(obj, "byReference", false);
        reload.classes = readClassPayloads(obj.get("classes"));
        reload.resources = readResourcePayloads(obj.get("resources"));
        reload.trigger = optionalString(obj, "trigger");
        return reload;
    }

    private static ReloadResult readReloadResult(Map<String, Object> obj) {
        ReloadResult result = new ReloadResult();
        result.status = optionalString(obj, "status");
        result.durationMs = optionalLong(obj, "durationMs", 0L);
        result.classes = readClassOutcomes(obj.get("classes"));
        result.adapters = readAdapterOutcomes(obj.get("adapters"));
        result.message = optionalString(obj, "message");
        return result;
    }

    private static LogEvent readLog(Map<String, Object> obj) {
        LogEvent log = new LogEvent();
        log.level = optionalString(obj, "level");
        log.logger = optionalString(obj, "logger");
        log.message = optionalString(obj, "message");
        return log;
    }

    private static StatusEvent readStatus(Map<String, Object> obj) {
        StatusEvent status = new StatusEvent();
        status.phase = optionalString(obj, "phase");
        status.backend = optionalString(obj, "backend");
        return status;
    }

    private static List<ClassPayload> readClassPayloads(Object raw) {
        if (raw == null) {
            return null;
        }
        List<Object> arr = requireArray(raw, "classes");
        List<ClassPayload> out = new ArrayList<>(arr.size());
        for (Object el : arr) {
            Map<String, Object> m = requireObject(el, "classes[]");
            ClassPayload c = new ClassPayload();
            c.binaryName = optionalString(m, "binaryName");
            c.path = optionalString(m, "path");
            c.sha256 = optionalString(m, "sha256");
            c.bytesBase64 = optionalString(m, "bytesBase64");
            out.add(c);
        }
        return out;
    }

    private static List<ResourcePayload> readResourcePayloads(Object raw) {
        if (raw == null) {
            return null;
        }
        List<Object> arr = requireArray(raw, "resources");
        List<ResourcePayload> out = new ArrayList<>(arr.size());
        for (Object el : arr) {
            Map<String, Object> m = requireObject(el, "resources[]");
            ResourcePayload r = new ResourcePayload();
            r.classpathName = optionalString(m, "classpathName");
            r.path = optionalString(m, "path");
            r.sha256 = optionalString(m, "sha256");
            out.add(r);
        }
        return out;
    }

    private static List<ClassOutcome> readClassOutcomes(Object raw) {
        if (raw == null) {
            return null;
        }
        List<Object> arr = requireArray(raw, "classes");
        List<ClassOutcome> out = new ArrayList<>(arr.size());
        for (Object el : arr) {
            Map<String, Object> m = requireObject(el, "classes[]");
            ClassOutcome c = new ClassOutcome();
            c.binaryName = optionalString(m, "binaryName");
            c.status = optionalString(m, "status");
            c.changeKinds = optionalStringList(m, "changeKinds");
            c.reason = optionalString(m, "reason");
            out.add(c);
        }
        return out;
    }

    private static List<AdapterOutcome> readAdapterOutcomes(Object raw) {
        if (raw == null) {
            return null;
        }
        List<Object> arr = requireArray(raw, "adapters");
        List<AdapterOutcome> out = new ArrayList<>(arr.size());
        for (Object el : arr) {
            Map<String, Object> m = requireObject(el, "adapters[]");
            AdapterOutcome a = new AdapterOutcome();
            a.adapterId = optionalString(m, "adapterId");
            a.status = optionalString(m, "status");
            a.durationMs = optionalLong(m, "durationMs", 0L);
            a.detail = optionalString(m, "detail");
            out.add(a);
        }
        return out;
    }

    private static String requireString(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        if (!(v instanceof String s) || s.isEmpty()) {
            throw new ProtocolException("missing or empty '" + key + "'");
        }
        return s;
    }

    private static String optionalString(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s;
        }
        throw new ProtocolException("field '" + key + "' must be a string");
    }

    private static long optionalLong(Map<String, Object> obj, String key, long defaultValue) {
        Object v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Long n) {
            return n;
        }
        throw new ProtocolException("field '" + key + "' must be an integer");
    }

    private static boolean optionalBoolean(Map<String, Object> obj, String key, boolean defaultValue) {
        Object v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        throw new ProtocolException("field '" + key + "' must be a boolean");
    }

    private static List<String> optionalStringList(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        if (v == null) {
            return null;
        }
        List<Object> arr = requireArray(v, key);
        List<String> out = new ArrayList<>(arr.size());
        for (Object el : arr) {
            if (!(el instanceof String s)) {
                throw new ProtocolException("array '" + key + "' must contain strings");
            }
            out.add(s);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireArray(Object raw, String name) {
        if (!(raw instanceof List<?> list)) {
            throw new ProtocolException("field '" + name + "' must be an array");
        }
        return (List<Object>) list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(Object raw, String name) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ProtocolException("'" + name + "' must be an object");
        }
        return (Map<String, Object>) map;
    }
}
