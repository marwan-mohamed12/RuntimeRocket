package io.runtimerocket.agent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Allow-list of directories the agent may read for {@code byReference} payloads. A path is accepted
 * only after {@link Path#toRealPath()} and only if it stays under a configured directory.
 */
public final class WatchDirs {

    private final List<Path> dirs;

    public WatchDirs(List<Path> dirs) {
        List<Path> normalized = new ArrayList<>();
        if (dirs != null) {
            for (Path dir : dirs) {
                if (dir != null) {
                    normalized.add(dir.toAbsolutePath().normalize());
                }
            }
        }
        this.dirs = List.copyOf(normalized);
    }

    public static WatchDirs of(List<Path> dirs) {
        return new WatchDirs(dirs);
    }

    public boolean isEmpty() {
        return dirs.isEmpty();
    }

    public List<Path> dirs() {
        return dirs;
    }

    public boolean contains(Path path) {
        if (path == null || dirs.isEmpty()) {
            return false;
        }
        Path real;
        try {
            real = path.toRealPath();
        } catch (IOException e) {
            return false;
        }
        for (Path dir : dirs) {
            Path base = resolveBase(dir);
            if (base != null && real.startsWith(base)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveBase(Path dir) {
        try {
            if (Files.exists(dir)) {
                return dir.toRealPath();
            }
            return dir.toAbsolutePath().normalize();
        } catch (IOException e) {
            return dir.toAbsolutePath().normalize();
        }
    }
}
