package io.runtimerocket.agent.reload;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Stock {@code Instrumentation.redefineClasses}: method bodies, constant-pool-only, new types. */
public final class StandardHotSwapBackend implements ReloadBackend {

    public static final String ID = "standard";

    private static final EnumSet<ChangeKind> SUPPORTED =
            EnumSet.of(ChangeKind.METHOD_BODY, ChangeKind.CONSTANT_POOL_ONLY, ChangeKind.NEW_TYPE);

    public static final List<String> CAPABILITIES =
            List.of(ChangeKind.METHOD_BODY.name(), ChangeKind.CONSTANT_POOL_ONLY.name(), ChangeKind.NEW_TYPE.name());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> capabilityNames() {
        return CAPABILITIES;
    }

    @Override
    public boolean probe(Instrumentation inst) {
        return inst != null && inst.isRedefineClassesSupported();
    }

    @Override
    public Support assess(ClassDelta delta) {
        if (delta == null) {
            return Support.UNSUPPORTED;
        }
        if (delta.anonymousIndexShiftLikely) {
            return Support.UNSUPPORTED;
        }
        if (delta.kinds.isEmpty()) {
            return Support.FULL;
        }
        boolean anySupported = false;
        boolean anyUnsupported = false;
        for (ChangeKind kind : delta.kinds) {
            if (SUPPORTED.contains(kind)) {
                anySupported = true;
            } else {
                anyUnsupported = true;
            }
        }
        if (anyUnsupported && anySupported) {
            return Support.PARTIAL;
        }
        if (anyUnsupported) {
            return Support.UNSUPPORTED;
        }
        return Support.FULL;
    }

    @Override
    public ReloadBackendResult apply(Instrumentation inst, List<Redefinition> batch) throws Exception {
        if (batch == null || batch.isEmpty()) {
            return new ReloadBackendResult(List.of());
        }
        List<ClassDefinition> defs = new ArrayList<>(batch.size());
        List<Class<?>> redefined = new ArrayList<>(batch.size());
        for (Redefinition item : batch) {
            if (item.loaded == null) {
                continue;
            }
            defs.add(new ClassDefinition(item.loaded, item.bytes));
            redefined.add(item.loaded);
        }
        if (!defs.isEmpty()) {
            inst.redefineClasses(defs.toArray(ClassDefinition[]::new));
        }
        return new ReloadBackendResult(redefined);
    }
}
