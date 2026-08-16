package io.runtimerocket.protocol;

import java.util.ArrayList;
import java.util.List;

/** Compact JSON writer. Omits null strings/lists. Control chars in strings are escaped. */
final class JsonWriter {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final StringBuilder out = new StringBuilder();
    private final List<Boolean> firstAtDepth = new ArrayList<>();

    JsonWriter() {
        firstAtDepth.add(true);
    }

    JsonWriter beginObject() {
        prepareValue();
        out.append('{');
        firstAtDepth.add(true);
        return this;
    }

    JsonWriter endObject() {
        out.append('}');
        firstAtDepth.remove(firstAtDepth.size() - 1);
        return this;
    }

    JsonWriter beginArray() {
        prepareValue();
        out.append('[');
        firstAtDepth.add(true);
        return this;
    }

    JsonWriter endArray() {
        out.append(']');
        firstAtDepth.remove(firstAtDepth.size() - 1);
        return this;
    }

    JsonWriter name(String name) {
        prepareValue();
        writeString(name);
        out.append(':');
        firstAtDepth.set(firstAtDepth.size() - 1, true);
        return this;
    }

    JsonWriter value(String value) {
        prepareValue();
        if (value == null) {
            out.append("null");
        } else {
            writeString(value);
        }
        return this;
    }

    JsonWriter value(long value) {
        prepareValue();
        out.append(value);
        return this;
    }

    JsonWriter value(boolean value) {
        prepareValue();
        out.append(value);
        return this;
    }

    JsonWriter field(String name, String value) {
        if (value == null) {
            return this;
        }
        name(name);
        value(value);
        return this;
    }

    JsonWriter field(String name, long value) {
        name(name);
        value(value);
        return this;
    }

    JsonWriter field(String name, boolean value) {
        name(name);
        value(value);
        return this;
    }

    JsonWriter fieldStrings(String name, List<String> values) {
        if (values == null) {
            return this;
        }
        name(name);
        beginArray();
        for (String v : values) {
            value(v);
        }
        endArray();
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    int length() {
        return out.length();
    }

    private void prepareValue() {
        int i = firstAtDepth.size() - 1;
        if (!firstAtDepth.get(i)) {
            out.append(',');
        }
        firstAtDepth.set(i, false);
    }

    private void writeString(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u");
                        out.append(HEX[(c >> 12) & 0xF]);
                        out.append(HEX[(c >> 8) & 0xF]);
                        out.append(HEX[(c >> 4) & 0xF]);
                        out.append(HEX[c & 0xF]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
