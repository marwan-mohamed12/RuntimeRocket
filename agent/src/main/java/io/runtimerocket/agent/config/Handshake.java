package io.runtimerocket.agent.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contents of {@code ${tmpdir}/runtimerocket/${pid}.json}. */
public final class Handshake {

    public final long pid;
    public final int port;
    public final String token;
    public final String backend;
    public final List<String> capabilities;
    public final String version;
    public final Instant startedAt;
    public final List<String> notes;

    public Handshake(
            long pid,
            int port,
            String token,
            String backend,
            List<String> capabilities,
            String version,
            Instant startedAt) {
        this(pid, port, token, backend, capabilities, version, startedAt, List.of());
    }

    public Handshake(
            long pid,
            int port,
            String token,
            String backend,
            List<String> capabilities,
            String version,
            Instant startedAt,
            List<String> notes) {
        this.pid = pid;
        this.port = port;
        this.token = Objects.requireNonNull(token, "token");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.capabilities = List.copyOf(capabilities);
        this.version = Objects.requireNonNull(version, "version");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.notes = List.copyOf(notes == null ? List.of() : notes);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"pid\":").append(pid).append(',');
        sb.append("\"port\":").append(port).append(',');
        sb.append("\"token\":\"").append(escape(token)).append("\",");
        sb.append("\"backend\":\"").append(escape(backend)).append("\",");
        sb.append("\"capabilities\":[");
        for (int i = 0; i < capabilities.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(capabilities.get(i))).append('"');
        }
        sb.append("],");
        sb.append("\"version\":\"").append(escape(version)).append("\",");
        sb.append("\"startedAt\":\"").append(escape(startedAt.toString())).append('"');
        if (!notes.isEmpty()) {
            sb.append(",\"notes\":[");
            for (int i = 0; i < notes.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(notes.get(i))).append('"');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    public static Handshake parse(String json) {
        Objects.requireNonNull(json, "json");
        long pid = longField(json, "pid");
        int port = (int) longField(json, "port");
        String token = stringField(json, "token");
        String backend = stringField(json, "backend");
        String version = stringField(json, "version");
        Instant startedAt = Instant.parse(stringField(json, "startedAt"));
        return new Handshake(
                pid, port, token, backend, stringArray(json, "capabilities"), version, startedAt, stringArray(json, "notes"));
    }

    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String stringField(String json, String name) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("missing handshake field: " + name);
        }
        return unescape(m.group(1));
    }

    private static long longField(String json, String name) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("missing handshake field: " + name);
        }
        return Long.parseLong(m.group(1));
    }

    private static List<String> stringArray(String json, String name) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return List.of();
        }
        String body = m.group(1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        Matcher item = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        List<String> out = new ArrayList<>();
        while (item.find()) {
            out.add(unescape(item.group(1)));
        }
        return out;
    }

    private static String unescape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(++i);
                sb.append(
                        switch (n) {
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> n;
                        });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
