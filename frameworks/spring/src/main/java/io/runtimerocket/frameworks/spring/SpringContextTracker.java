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
    private static final AtomicReference<WeakReference<Object>> LAST = new AtomicReference<>();
    private static final AtomicReference<Consumer<Object>> LISTENER = new AtomicReference<>();

    private SpringContextTracker() {}

    public static void register(Object context) {
        if (context == null || !isApplicationContext(context)) {
            return;
        }
        WeakReference<Object> lastRef = LAST.get();
        if (lastRef != null && lastRef.get() == context) {
            return;
        }
        for (WeakReference<Object> ref : CONTEXTS) {
            if (ref.get() == context) {
                LAST.set(new WeakReference<>(context));
                return;
            }
        }
        purge();
        CONTEXTS.add(new WeakReference<>(context));
        LAST.set(new WeakReference<>(context));
        Consumer<Object> listener = LISTENER.get();
        if (listener != null) {
            try {
                listener.accept(context);
            } catch (RuntimeException ignored) {
                // registration must never fail the application call
            }
        }
    }

    public static boolean isEmpty() {
        return liveContexts().isEmpty();
    }

    public static List<Object> liveContexts() {
        List<Object> live = new ArrayList<>();
        for (WeakReference<Object> ref : CONTEXTS) {
            Object ctx = ref.get();
            if (ctx != null && isApplicationContext(ctx) && !live.contains(ctx)) {
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
        WeakReference<Object> lastRef = LAST.get();
        Object last = lastRef == null ? null : lastRef.get();
        if (last == null) {
            LAST.compareAndSet(lastRef, null);
            return;
        }
        boolean found = false;
        for (WeakReference<Object> ref : CONTEXTS) {
            if (ref.get() == last) {
                found = true;
                break;
            }
        }
        if (!found) {
            LAST.compareAndSet(lastRef, null);
        }
    }

    static boolean isApplicationContext(Object value) {
        if (value == null) {
            return false;
        }
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (isApplicationContextName(type.getName()) || hasApplicationContextInterface(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasApplicationContextInterface(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if (isApplicationContextName(iface.getName()) || hasApplicationContextInterface(iface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicationContextName(String name) {
        return "org.springframework.context.ApplicationContext".equals(name)
                || "org.springframework.context.ConfigurableApplicationContext".equals(name);
    }
}
