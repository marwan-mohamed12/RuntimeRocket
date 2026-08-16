package io.runtimerocket.agent.spi;

import java.util.List;
import java.util.Objects;

/** Watched resource files that changed on disk. Bytes are not injected. */
public final class ResourceChangeEvent {

    public final AdapterContext ctx;
    public final List<ChangedResource> resources;

    public ResourceChangeEvent(AdapterContext ctx, List<ChangedResource> resources) {
        this.ctx = ctx;
        this.resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public static final class ChangedResource {

        public final String classpathName;
        public final String path;
        public final String sha256;

        public ChangedResource(String classpathName, String path, String sha256) {
            this.classpathName = classpathName;
            this.path = path;
            this.sha256 = sha256;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ChangedResource that)) {
                return false;
            }
            return Objects.equals(classpathName, that.classpathName)
                    && Objects.equals(path, that.path)
                    && Objects.equals(sha256, that.sha256);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classpathName, path, sha256);
        }
    }
}
