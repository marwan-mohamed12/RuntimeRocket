package io.runtimerocket.protocol;

import java.util.Objects;

/** v1 resource notification: path + hash only. No {@code bytesBase64}. */
public final class ResourcePayload {

    public String classpathName;
    public String path;
    public String sha256;

    public ResourcePayload() {}

    public ResourcePayload(String classpathName, String path, String sha256) {
        this.classpathName = classpathName;
        this.path = path;
        this.sha256 = sha256;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourcePayload that)) {
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
