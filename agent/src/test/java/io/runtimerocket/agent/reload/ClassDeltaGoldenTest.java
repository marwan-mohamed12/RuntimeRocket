package io.runtimerocket.agent.reload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDeltaGoldenTest {

    private final ClassDeltaClassifier classifier = new ClassDeltaClassifier();

    static Stream<String> goldenDirectories() {
        return DeltaFixtures.all().stream().map(DeltaFixtures.Fixture::directory);
    }

    @Test
    void checkedInPairsMatchGeneratedFixtures() {
        for (DeltaFixtures.Fixture fixture : DeltaFixtures.all()) {
            String dir = fixture.directory();
            assertArrayEquals(fixture.before(), readResource(dir + "/before.class"), dir + " before.class");
            assertArrayEquals(fixture.after(), readResource(dir + "/after.class"), dir + " after.class");
            assertEquals(
                    fixture.expectedText().replace("\r\n", "\n"),
                    new String(readResource(dir + "/expected.txt"), StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    dir + " expected.txt");
        }
    }

    @ParameterizedTest
    @MethodSource("goldenDirectories")
    void goldenPair(String directory) {
        byte[] before = readResource(directory + "/before.class");
        byte[] after = readResource(directory + "/after.class");
        Expected expected = Expected.parse(new String(readResource(directory + "/expected.txt"), StandardCharsets.UTF_8));

        ClassDelta delta = classifier.classify(before, after);
        for (ChangeKind kind : expected.contains) {
            assertTrue(delta.kinds.contains(kind), directory + " missing " + kind + " in " + delta.kinds);
        }
        assertEquals(expected.shift, delta.anonymousIndexShiftLikely, directory + " shift flag");
        if (expected.version != 0) {
            assertEquals(expected.version, DeltaFixtures.majorVersion(before), directory + " before version");
            assertEquals(expected.version, DeltaFixtures.majorVersion(after), directory + " after version");
        }
    }

    private static byte[] readResource(String relative) {
        String path = "/deltas/" + relative;
        try (InputStream in = ClassDeltaGoldenTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class Expected {
        final EnumSet<ChangeKind> contains = EnumSet.noneOf(ChangeKind.class);
        boolean shift;
        int version;

        static Expected parse(String text) {
            Expected expected = new Expected();
            for (String raw : text.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "contains", "kinds" -> {
                        for (String token : value.split(",")) {
                            expected.contains.add(ChangeKind.valueOf(token.trim()));
                        }
                    }
                    case "shift" -> expected.shift = Boolean.parseBoolean(value);
                    case "version" -> expected.version = Integer.parseInt(value);
                    default -> {
                    }
                }
            }
            return expected;
        }
    }
}
