package io.runtimerocket.frameworks.spring;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Weak set of live Spring {@code ApplicationContext}s. Called from instrumented Spring methods;
 * must not touch Spring types or call back into {@code isActive}/{@code getBean}.
 */
public final class SpringContextTracker {

    private static final CopyOnWriteArrayList<WeakReference<Object>> CONTEXTS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Object> LAST = new AtomicReference<>();
    private static final AtomicReference<Consumer<Object>> LISTENER = new AtomicReference<>();

    private SpringContextTracker() {}

    public static void register(Object context) {
        if (context == null) {
            return;
        }
        if (LAST.get() == context) {
            return;
        }
        for (WeakReference<Object> ref : CONTEXTS) {
            if (ref.get() == context) {
                LAST.set(context);
                return;
            }
        }
        purge();
        CONTEXTS.add(new WeakReference<>(context));
        LAST.set(context);
        Consumer<Object> listener = LISTENER.get();
        if (listener != null) {
            try {
                listener.accept(context);
            } catch (RuntimeException ignored) {
                // registration must never fail the application call
            }
        }
    }

    /** Optional snapshot from {@code DefaultListableBeanFactory#preInstantiateSingletons}. */
    public static void snapshotBeanNames(Object factory) {
        if (factory != null) {
            register(factory);
        }
    }

    public static boolean isEmpty() {
        return liveContexts().isEmpty();
    }

    public static List<Object> liveContexts() {
        List<Object> live = new ArrayList<>();
        for (WeakReference<Object> ref : CONTEXTS) {
            Object ctx = ref.get();
            if (ctx != null && !live.contains(ctx)) {
                live.add(ctx);
            }
        }
        if (live.size() != CONTEXTS.size()) {
            purge();
        }
        return live;
    }

    public static void setListener(Consumer<Object> listener) {
        LISTENER.set(listener);
    }

    public static void reset() {
        CONTEXTS.clear();
        LAST.set(null);
        LISTENER.set(null);
    }

    private static void purge() {
        Iterator<WeakReference<Object>> it = CONTEXTS.iterator();
        List<WeakReference<Object>> stale = new ArrayList<>();
        while (it.hasNext()) {
            WeakReference<Object> ref = it.next();
            if (ref.get() == null) {
                stale.add(ref);
            }
        }
        if (!stale.isEmpty()) {
            CONTEXTS.removeAll(stale);
        }
        Object last = LAST.get();
        if (last != null) {
            boolean found = false;
            for (WeakReference<Object> ref : CONTEXTS) {
                if (ref.get() == last) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LAST.compareAndSet(last, null);
            }
        }
    }
}
