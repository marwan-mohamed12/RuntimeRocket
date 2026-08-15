package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchDirsTest {

    @TempDir
    Path temp;

    @Test
    void acceptsOnlyRealPathsUnderConfiguredDirs() throws Exception {
        Path watch = Files.createDirectories(temp.resolve("watch"));
        Path inside = watch.resolve("Foo.class");
        Files.write(inside, new byte[] {1, 2, 3});
        Path outside = temp.resolve("secret.class");
        Files.write(outside, new byte[] {4, 5, 6});
        Path escape = watch.resolve("..").resolve("secret.class");

        WatchDirs dirs = WatchDirs.of(List.of(watch));
        assertTrue(dirs.contains(inside));
        assertFalse(dirs.contains(outside));
        assertFalse(dirs.contains(escape));
        assertFalse(dirs.contains(watch.resolve("missing.class")));
    }

    @Test
    void emptyAllowListRejectsEverything() throws Exception {
        Path file = temp.resolve("x.class");
        Files.write(file, new byte[] {1});
        assertFalse(WatchDirs.of(List.of()).contains(file));
    }
}
