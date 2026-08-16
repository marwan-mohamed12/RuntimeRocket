package io.runtimerocket.agent.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One parsed {@code runtimerocket.xml} document. */
public final class RocketXml {

    public final String id;
    public final Path source;
    public final List<Path> classpathDirs;
    public final List<Path> resourceDirs;
    public final List<String> includes;
    public final List<String> excludes;

    public RocketXml(
            String id,
            Path source,
            List<Path> classpathDirs,
            List<Path> resourceDirs,
            List<String> includes,
            List<String> excludes) {
        this.id = id;
        this.source = source;
        this.classpathDirs = List.copyOf(classpathDirs == null ? List.of() : classpathDirs);
        this.resourceDirs = List.copyOf(resourceDirs == null ? List.of() : resourceDirs);
        this.includes = List.copyOf(includes == null ? List.of() : includes);
        this.excludes = List.copyOf(excludes == null ? List.of() : excludes);
    }

    public PackageFilter packageFilter() {
        return PackageFilter.of(includes, excludes);
    }

    public List<Path> allDirs() {
        if (classpathDirs.isEmpty()) {
            return resourceDirs;
        }
        if (resourceDirs.isEmpty()) {
            return classpathDirs;
        }
        ArrayList<Path> all = new ArrayList<>(classpathDirs.size() + resourceDirs.size());
        all.addAll(classpathDirs);
        all.addAll(resourceDirs);
        return List.copyOf(all);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RocketXml that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(source, that.source)
                && classpathDirs.equals(that.classpathDirs)
                && resourceDirs.equals(that.resourceDirs)
                && includes.equals(that.includes)
                && excludes.equals(that.excludes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, classpathDirs, resourceDirs, includes, excludes);
    }
}
