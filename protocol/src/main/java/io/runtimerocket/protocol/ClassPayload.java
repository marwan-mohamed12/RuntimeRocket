package io.runtimerocket.protocol;

import java.util.Objects;

public final class ClassPayload {

    public String binaryName;
    public String path;
    public String sha256;
    /** Null when {@link ReloadRequest#byReference} is true or bytes are omitted. */
    public String bytesBase64;

    public ClassPayload() {}

    public ClassPayload(String binaryName, String path, String sha256, String bytesBase64) {
        this.binaryName = binaryName;
        this.path = path;
        this.sha256 = sha256;
        this.bytesBase64 = bytesBase64;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassPayload that)) {
            return false;
        }
        return Objects.equals(binaryName, that.binaryName)
                && Objects.equals(path, that.path)
                && Objects.equals(sha256, that.sha256)
                && Objects.equals(bytesBase64, that.bytesBase64);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryName, path, sha256, bytesBase64);
    }
}
