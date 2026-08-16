package io.runtimerocket.agent.reload;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;

/** Proxy {@link Instrumentation} for backend probe/selector tests. */
final class FakeInstrumentation {

    private FakeInstrumentation() {}

    interface Redefine {
        void apply(ClassDefinition[] definitions) throws Exception;
    }

    static Instrumentation of(boolean redefineSupported, Redefine redefine) {
        return (Instrumentation)
                Proxy.newProxyInstance(
                        Instrumentation.class.getClassLoader(),
                        new Class<?>[] {Instrumentation.class},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if ("isRedefineClassesSupported".equals(name)) {
                                return redefineSupported;
                            }
                            if ("redefineClasses".equals(name)) {
                                ClassDefinition[] defs =
                                        args == null || args.length == 0
                                                ? new ClassDefinition[0]
                                                : (ClassDefinition[]) args[0];
                                redefine.apply(defs);
                                return null;
                            }
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) {
                                return false;
                            }
                            if (rt == int.class) {
                                return 0;
                            }
                            if (rt == long.class) {
                                return 0L;
                            }
                            return null;
                        });
    }
}
