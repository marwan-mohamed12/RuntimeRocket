package io.runtimerocket.protocol;

import java.util.List;
import java.util.Objects;

public final class ReloadRequest extends Frame {

    public static final String TRIGGER_COMPILE = "compile";
    public static final String TRIGGER_WATCH = "watch";
    public static final String TRIGGER_MANUAL = "manual";

    public boolean byReference;
    public List<ClassPayload> classes;
    public List<ResourcePayload> resources;
    public String trigger;

    public ReloadRequest() {
        super(FrameTypes.RELOAD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReloadRequest that)) {
            return false;
        }
        return seq == that.seq
                && byReference == that.byReference
                && Objects.equals(type, that.type)
                && Objects.equals(session, that.session)
                && Objects.equals(classes, that.classes)
                && Objects.equals(resources, that.resources)
                && Objects.equals(trigger, that.trigger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, byReference, classes, resources, trigger);
    }
}
