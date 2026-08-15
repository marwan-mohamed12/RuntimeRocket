package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.watch.ClassPathWatcher;
import io.runtimerocket.protocol.ClassOutcome;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;
import io.runtimerocket.protocol.ResourcePayload;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-flight reload: classify the whole batch, abort before any define/redefine if anything is
 * unsupported, then define new types and issue one {@code redefineClasses} call.
 */
public final class ReloadOrchestrator {

    static final long DEDUP_WINDOW_MS = TimeUnit.SECONDS.toMillis(1);
    static final long WATCHER_SUPPRESS_MS = TimeUnit.SECONDS.toMillis(1);

    private final Instrumentation inst;
    private final ReloadBackend backend;
    private final ClassIndex index;
    private final ClassDeltaClassifier classifier = new ClassDeltaClassifier();
    private final WatchDirs watchDirs;
    private final ClassPathWatcher watcher;
    private final AgentLog log;
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<ClassKey, Long> recentKeys = new ConcurrentHashMap<>();

    public ReloadOrchestrator(
            Instrumentation inst,
            ReloadBackend backend,
            ClassIndex index,
            WatchDirs watchDirs,
            ClassPathWatcher watcher,
            AgentLog log) {
        this.inst = Objects.requireNonNull(inst, "inst");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.index = Objects.requireNonNull(index, "index");
        this.watchDirs = watchDirs == null ? WatchDirs.of(List.of()) : watchDirs;
        this.watcher = watcher;
        this.log = log;
    }

    public ReloadResult reload(ReloadRequest request) {
        long started = System.nanoTime();
        lock.lock();
        try {
            return execute(request == null ? new ReloadRequest() : request);
        } finally {
            lock.unlock();
            finishTimer(started);
        }
    }

    private ReloadResult execute(ReloadRequest request) {
        long t0 = System.currentTimeMillis();
        List<ClassPayload> payloads = request.classes == null ? List.of() : request.classes;
        List<ResourcePayload> resources = request.resources == null ? List.of() : request.resources;

        ResolvedClasses resolved;
        try {
            resolved = resolveClasses(payloads, request.byReference);
        } catch (PathJailException e) {
            return result(
                    ReloadResult.FAILED,
                    t0,
                    List.of(new ClassOutcome(e.binaryName, ClassOutcome.FAILED, List.of(), e.getMessage())),
                    e.getMessage());
        } catch (ResolveException e) {
            return result(ReloadResult.FAILED, t0, List.of(), e.getMessage());
        }

        try {
            checkResources(resources);
        } catch (PathJailException e) {
            return result(ReloadResult.FAILED, t0, List.of(), e.getMessage());
        }

        if (ReloadRequest.TRIGGER_COMPILE.equals(request.trigger) && watcher != null) {
            for (ResolvedClass rc : resolved.items) {
                if (rc.path != null) {
                    watcher.suppress(rc.path, WATCHER_SUPPRESS_MS);
                }
            }
            for (ResourcePayload resource : resources) {
                if (resource.path != null) {
                    watcher.suppress(Path.of(resource.path), WATCHER_SUPPRESS_MS);
                }
            }
        }

        long now = System.currentTimeMillis();
        pruneRecent(now);
        List<ClassOutcome> skipped = new ArrayList<>();
        List<ResolvedClass> remaining = new ArrayList<>();
        for (ResolvedClass rc : resolved.items) {
            ClassKey key = new ClassKey(rc.binaryName, rc.sha256);
            Long seen = recentKeys.get(key);
            if (seen != null && now - seen < DEDUP_WINDOW_MS) {
                skipped.add(new ClassOutcome(rc.binaryName, ClassOutcome.SKIPPED, List.of(), "duplicate"));
            } else {
                recentKeys.put(key, now);
                remaining.add(rc);
            }
        }

        if (remaining.isEmpty() && resources.isEmpty()) {
            return result(ReloadResult.SUCCESS, t0, skipped, skipped.isEmpty() ? "nothing to reload" : "duplicate");
        }

        List<Prepared> prepared = new ArrayList<>();
        List<ClassOutcome> outcomes = new ArrayList<>(skipped);
        for (ResolvedClass rc : remaining) {
            try {
                prepared.add(prepare(rc));
            } catch (ResolveException e) {
                return result(ReloadResult.FAILED, t0, outcomes, e.getMessage());
            }
        }

        Prepared unsupported = firstUnsupported(prepared);
        if (unsupported != null) {
            for (Prepared item : prepared) {
                outcomes.add(
                        new ClassOutcome(
                                item.binaryName,
                                ClassOutcome.FAILED,
                                kindNames(item.delta),
                                "unsupported"));
            }
            String message = "Cannot hot-reload "
                    + unsupported.binaryName
                    + ": "
                    + primaryUnsupportedKind(unsupported.delta);
            if (log != null) {
                log.info("reload RESTART_REQUIRED " + message);
            }
            return result(ReloadResult.RESTART_REQUIRED, t0, outcomes, message);
        }

        List<Prepared> defined = new ArrayList<>();
        try {
            for (Prepared item : prepared) {
                if (item.loaded.isEmpty()) {
                    Class<?> cls = defineNewType(item.binaryName, item.bytes);
                    index.recordClass(cls);
                    index.storeBytes(cls.getClassLoader(), item.binaryName, item.bytes);
                    item.definedClass = cls;
                    defined.add(item);
                }
            }
        } catch (Exception e) {
            return failAfterDefine(t0, prepared, defined, e);
        }

        List<Redefinition> batch = new ArrayList<>();
        for (Prepared item : prepared) {
            for (Class<?> loaded : item.loaded) {
                batch.add(new Redefinition(loaded, item.bytes, item.delta));
            }
        }

        try {
            if (!batch.isEmpty()) {
                backend.apply(inst, batch);
            }
        } catch (Exception e) {
            return failAfterDefine(t0, prepared, defined, e);
        }

        for (Prepared item : prepared) {
            if (item.definedClass != null) {
                outcomes.add(
                        new ClassOutcome(item.binaryName, ClassOutcome.DEFINED, kindNames(item.delta), null));
            } else {
                outcomes.add(
                        new ClassOutcome(item.binaryName, ClassOutcome.REDEFINED, kindNames(item.delta), null));
            }
            index.storeBytes(
                    item.definedClass != null
                            ? item.definedClass.getClassLoader()
                            : item.loaded.isEmpty() ? null : item.loaded.get(0).getClassLoader(),
                    item.binaryName,
                    item.bytes);
        }

        if (log != null) {
            log.info("reload SUCCESS "
                    + prepared.size()
                    + " classes "
                    + (System.currentTimeMillis() - t0)
                    + "ms");
        }
        return result(ReloadResult.SUCCESS, t0, outcomes, null);
    }

    private Prepared prepare(ResolvedClass rc) {
        List<Class<?>> loaded = index.findAll(rc.binaryName);
        byte[] previous;
        if (loaded.isEmpty()) {
            previous = index.previousBytes(null, rc.binaryName).orElse(null);
        } else {
            previous = index.captureBytes(loaded.get(0));
            if (previous == null) {
                throw new ResolveException("no-previous-bytes: " + rc.binaryName);
            }
        }
        ClassDelta delta = classifier.classify(previous, rc.bytes);
        if (loaded.isEmpty() && !delta.kinds.contains(ChangeKind.NEW_TYPE)) {
            delta = new ClassDelta(
                    delta.internalName,
                    EnumSet.of(ChangeKind.NEW_TYPE),
                    delta.addedMethods,
                    delta.removedMethods,
                    delta.bodyChangedMethods,
                    delta.addedFields,
                    delta.removedFields,
                    delta.superclassChanged,
                    delta.interfacesChanged,
                    delta.nestHostChanged,
                    delta.permittedSubclassesChanged,
                    delta.recordComponentsChanged,
                    delta.enumConstantsChanged,
                    delta.anonymousIndexShiftLikely);
        }
        return new Prepared(rc.binaryName, rc.bytes, loaded, delta);
    }

    private Prepared firstUnsupported(List<Prepared> prepared) {
        for (Prepared item : prepared) {
            Support support = backend.assess(item.delta);
            if (support != Support.FULL) {
                return item;
            }
        }
        return null;
    }

    private Class<?> defineNewType(String binaryName, byte[] bytes) throws Exception {
        ClassLoader loader = chooseLoader();
        Method define =
                ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        define.setAccessible(true);
        return (Class<?>) define.invoke(loader, binaryName, bytes, 0, bytes.length);
    }

    private ClassLoader chooseLoader() {
        for (ClassLoader loader : index.applicationLoaders()) {
            if (loader != null) {
                return loader;
            }
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            return ctx;
        }
        ClassLoader self = ReloadOrchestrator.class.getClassLoader();
        return self != null ? self : ClassLoader.getSystemClassLoader();
    }

    private ResolvedClasses resolveClasses(List<ClassPayload> payloads, boolean byReference) {
        List<ResolvedClass> items = new ArrayList<>();
        for (ClassPayload payload : payloads) {
            items.add(resolveOne(payload, byReference));
        }
        return new ResolvedClasses(items);
    }

    private ResolvedClass resolveOne(ClassPayload payload, boolean byReference) {
        String binaryName = payload.binaryName;
        Path path = payload.path == null || payload.path.isBlank() ? null : Path.of(payload.path);
        byte[] bytes = null;
        boolean mustRead = byReference || payload.bytesBase64 == null || payload.bytesBase64.isBlank();
        if (mustRead) {
            if (path == null) {
                throw new ResolveException("missing-bytes: " + nullToEmpty(binaryName));
            }
            if (!watchDirs.contains(path)) {
                throw new PathJailException(binaryName, "path-not-watched");
            }
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException e) {
                throw new ResolveException("cannot read " + path, e);
            }
        } else {
            try {
                bytes = Base64.getDecoder().decode(payload.bytesBase64);
            } catch (IllegalArgumentException e) {
                throw new ResolveException("invalid-base64: " + nullToEmpty(binaryName), e);
            }
        }
        if (binaryName == null || binaryName.isBlank()) {
            binaryName = new ClassReader(bytes).getClassName().replace('/', '.');
        }
        String sha = payload.sha256;
        String computed = Hashes.sha256Hex(bytes);
        if (sha == null || sha.isBlank()) {
            sha = computed;
        } else if (!Hashes.equalHex(sha, computed)) {
            throw new ResolveException("sha256-mismatch: " + binaryName);
        }
        return new ResolvedClass(binaryName, bytes, sha, path);
    }

    private void checkResources(List<ResourcePayload> resources) {
        for (ResourcePayload resource : resources) {
            if (resource.path == null || resource.path.isBlank()) {
                throw new PathJailException(resource.classpathName, "path-not-watched");
            }
            if (!watchDirs.contains(Path.of(resource.path))) {
                throw new PathJailException(resource.classpathName, "path-not-watched");
            }
        }
    }

    private ReloadResult failAfterDefine(long t0, List<Prepared> prepared, List<Prepared> defined, Exception e) {
        List<ClassOutcome> outcomes = new ArrayList<>();
        for (Prepared item : prepared) {
            boolean wasDefined = item.definedClass != null || defined.contains(item);
            outcomes.add(
                    new ClassOutcome(
                            item.binaryName,
                            wasDefined ? ClassOutcome.DEFINED : ClassOutcome.FAILED,
                            kindNames(item.delta),
                            e.getMessage()));
        }
        String status = defined.isEmpty() ? ReloadResult.RESTART_REQUIRED : ReloadResult.FAILED;
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (log != null) {
            log.info("reload " + status + " " + message);
        }
        return result(status, t0, outcomes, message);
    }

    private static List<String> kindNames(ClassDelta delta) {
        if (delta == null || delta.kinds.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(delta.kinds.size());
        for (ChangeKind kind : delta.kinds) {
            names.add(kind.name());
        }
        return names;
    }

    private static String primaryUnsupportedKind(ClassDelta delta) {
        if (delta.anonymousIndexShiftLikely) {
            return "anonymous-index-shift";
        }
        for (ChangeKind kind : delta.kinds) {
            if (kind != ChangeKind.METHOD_BODY
                    && kind != ChangeKind.CONSTANT_POOL_ONLY
                    && kind != ChangeKind.NEW_TYPE) {
                return kind.name();
            }
        }
        if (!delta.kinds.isEmpty()) {
            return delta.kinds.iterator().next().name();
        }
        return "UNSUPPORTED";
    }

    private ReloadResult result(String status, long startedMs, List<ClassOutcome> classes, String message) {
        ReloadResult result = new ReloadResult();
        result.status = status;
        result.durationMs = Math.max(0L, System.currentTimeMillis() - startedMs);
        result.classes = classes;
        result.adapters = List.of();
        result.message = message;
        return result;
    }

    private void pruneRecent(long now) {
        if (recentKeys.size() < 64) {
            return;
        }
        Iterator<Map.Entry<ClassKey, Long>> it = recentKeys.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ClassKey, Long> entry = it.next();
            if (now - entry.getValue() >= DEDUP_WINDOW_MS) {
                it.remove();
            }
        }
    }

    private void finishTimer(long startedNanos) {
        // duration is recorded on the result; keep the lock section measurable
        if (log != null && log.isDebugEnabled()) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            log.debug("reload lock held " + ms + "ms");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ClassKey(String binaryName, String sha256) {}

    private record ResolvedClasses(List<ResolvedClass> items) {}

    private record ResolvedClass(String binaryName, byte[] bytes, String sha256, Path path) {}

    private static final class Prepared {
        final String binaryName;
        final byte[] bytes;
        final List<Class<?>> loaded;
        final ClassDelta delta;
        Class<?> definedClass;

        Prepared(String binaryName, byte[] bytes, List<Class<?>> loaded, ClassDelta delta) {
            this.binaryName = binaryName;
            this.bytes = bytes;
            this.loaded = loaded;
            this.delta = delta;
        }
    }

    private static final class PathJailException extends RuntimeException {
        final String binaryName;

        PathJailException(String binaryName, String message) {
            super(message);
            this.binaryName = binaryName;
        }
    }

    private static final class ResolveException extends RuntimeException {
        ResolveException(String message) {
            super(message);
        }

        ResolveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
