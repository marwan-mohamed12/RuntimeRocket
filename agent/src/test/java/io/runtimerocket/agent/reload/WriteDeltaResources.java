package io.runtimerocket.agent.reload;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes ASM fixtures into {@code agent/src/test/resources/deltas}. */
public final class WriteDeltaResources {

    private WriteDeltaResources() {}

    public static void main(String[] args) throws Exception {
        Path root = resolveRoot(args);
        Files.createDirectories(root);
        for (DeltaFixtures.Fixture fixture : DeltaFixtures.all()) {
            Path dir = root.resolve(fixture.directory());
            Files.createDirectories(dir);
            Files.write(dir.resolve("before.class"), fixture.before());
            Files.write(dir.resolve("after.class"), fixture.after());
            Files.writeString(dir.resolve("expected.txt"), fixture.expectedText(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + DeltaFixtures.all().size() + " fixtures to " + root.toAbsolutePath());
    }

    private static Path resolveRoot(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path nested = cwd.resolve("agent/src/test/resources/deltas");
        if (Files.isDirectory(cwd.resolve("agent"))) {
            return nested;
        }
        return cwd.resolve("src/test/resources/deltas");
    }
}
