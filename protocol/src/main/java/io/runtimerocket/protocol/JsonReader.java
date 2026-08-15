package io.runtimerocket.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for the closed RR/1 shapes. Integers only (no floats).
 * Unescaped control characters inside strings are rejected so a raw {@code \n}
 * cannot split a JSON-lines frame.
 */
final class JsonReader {

    private static final int MAX_DEPTH = 32;

    private final String s;
    private final int n;
    private int i;
    private int depth;

    JsonReader(String s) {
        this.s = s;
        this.n = s.length();
    }

    Object parse() {
        skipWs();
        Object value = readValue();
        skipWs();
        if (i != n) {
            throw error("trailing junk at index " + i);
        }
        return value;
    }

    private Object readValue() {
        skipWs();
        if (i >= n) {
            throw error("unexpected end of input");
        }
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readNumber();
            default -> throw error("unexpected '" + c + "'");
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        enter();
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek('}')) {
            i++;
            leave();
            return map;
        }
        while (true) {
            skipWs();
            if (i >= n || s.charAt(i) != '"') {
                throw error("object key must be a string");
            }
            String key = readString();
            skipWs();
            expect(':');
            map.put(key, readValue());
            skipWs();
            if (peek('}')) {
                i++;
                leave();
                return map;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        enter();
        List<Object> list = new ArrayList<>();
        skipWs();
        if (peek(']')) {
            i++;
            leave();
            return list;
        }
        while (true) {
            list.add(readValue());
            skipWs();
            if (peek(']')) {
                i++;
                leave();
                return list;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (i < n) {
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (i >= n) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(readHexChar());
                    default -> throw error("bad escape \\" + e);
                }
            } else if (c < 0x20) {
                throw error("unescaped control character in string");
            } else {
                sb.append(c);
            }
        }
        throw error("unterminated string");
    }

    private char readHexChar() {
        if (i + 4 > n) {
            throw error("truncated \\u escape");
        }
        int cp = 0;
        for (int k = 0; k < 4; k++) {
            char h = s.charAt(i++);
            int d = hex(h);
            if (d < 0) {
                throw error("bad hex digit in \\u escape");
            }
            cp = (cp << 4) | d;
        }
        return (char) cp;
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private Long readNumber() {
        int start = i;
        if (peek('-')) {
            i++;
        }
        if (i >= n || !isDigit(s.charAt(i))) {
            throw error("bad number");
        }
        if (s.charAt(i) == '0') {
            i++;
            if (i < n && isDigit(s.charAt(i))) {
                throw error("leading zero");
            }
        } else {
            while (i < n && isDigit(s.charAt(i))) {
                i++;
            }
        }
        if (i < n) {
            char c = s.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') {
                throw error("integer required");
            }
        }
        try {
            return Long.parseLong(s.substring(start, i));
        } catch (NumberFormatException ex) {
            throw new ProtocolException("integer out of range at index " + start, ex);
        }
    }

    private Object readLiteral(String expected, Object value) {
        if (i + expected.length() > n || !s.startsWith(expected, i)) {
            throw error("expected " + expected);
        }
        i += expected.length();
        return value;
    }

    private void skipWs() {
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                return;
            }
        }
    }

    private void expect(char c) {
        if (i >= n || s.charAt(i) != c) {
            throw error("expected '" + c + "'");
        }
        i++;
    }

    private boolean peek(char c) {
        return i < n && s.charAt(i) == c;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void enter() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw error("nesting too deep");
        }
    }

    private void leave() {
        depth--;
    }

    private ProtocolException error(String message) {
        return new ProtocolException(message + " at index " + i);
    }
}
