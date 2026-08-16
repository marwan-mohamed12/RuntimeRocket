package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.spi.Capabilities;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.agent.watch.ClassPathWatcher;
import io.runtimerocket.protocol.AdapterOutcome;
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
    private final AdapterHost adapters;
    private final AgentAdapterContext adapterContext;
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<ClassKey, Long> recentKeys = new ConcurrentHashMap<>();

    public ReloadOrchestrator(
            Instrumentation inst,
            ReloadBackend backend,
            ClassIndex index,
            WatchDirs watchDirs,
            ClassPathWatcher watcher,
            AgentLog log) {
        this(inst, backend, index, watchDirs, watcher, log, AdapterHost.none(), false);
    }

    public ReloadOrchestrator(
            Instrumentation inst,
            ReloadBackend backend,
            ClassIndex index,
            WatchDirs watchDirs,
            ClassPathWatcher watcher,
            AgentLog log,
            AdapterHost adapters,
            boolean lateAttach) {
        this.inst = Objects.requireNonNull(inst, "inst");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.index = Objects.requireNonNull(index, "index");
        this.watchDirs = watchDirs == null ? WatchDirs.of(List.of()) : watchDirs;
        this.watcher = watcher;
        this.log = log;
        this.adapters = adapters == null ? AdapterHost.none() : adapters;
        this.adapterContext = new AgentAdapterContext(this.index, lateAttach, log);
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
                    Class<?> cls = defineNewType(item.binaryName, item.bytes, prepared);
                    index.recordClass(cls);
                    index.storeBytes(cls.getClassLoader(), item.binaryName, item.bytes);
                    item.definedClass = cls;
                    defined.add(item);
                }
            }
        } catch (Exception e) {
            markApplied(defined);
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
            markApplied(defined);
            return failAfterDefine(t0, prepared, defined, e);
        }

        markApplied(prepared);
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

        List<AdapterOutcome> adapterOutcomes = notifyAdapters(prepared, resources);
        String status = AdapterHost.softFailed(adapterOutcomes) ? ReloadResult.PARTIAL : ReloadResult.SUCCESS;
        String message = ReloadResult.PARTIAL.equals(status) ? AdapterHost.firstFailureDetail(adapterOutcomes) : null;
        if (log != null) {
            log.info("reload "
                    + status
                    + " "
                    + prepared.size()
                    + " classes "
                    + (System.currentTimeMillis() - t0)
                    + "ms");
        }
        return result(status, t0, outcomes, adapterOutcomes, message);
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
        return new Prepared(rc.binaryName, rc.bytes, rc.sha256, loaded, delta);
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

    private Class<?> defineNewType(String binaryName, byte[] bytes, List<Prepared> batch) throws Exception {
        ClassLoader loader = chooseLoader(binaryName, batch);
        Method define =
                ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        define.setAccessible(true);
        return (Class<?>) define.invoke(loader, binaryName, bytes, 0, bytes.length);
    }

    private ClassLoader chooseLoader(String binaryName, List<Prepared> batch) {
        String pkg = ClassIndex.packageName(binaryName);
        if (batch != null) {
            for (Prepared item : batch) {
                for (Class<?> loaded : item.loaded) {
                    ClassLoader loader = loaded.getClassLoader();
                    if (loader != null && ClassIndex.packageName(loaded.getName()).equals(pkg)) {
                        return loader;
                    }
                }
            }
        }
        ClassLoader indexed = index.loaderForNewType(binaryName);
        if (indexed != null) {
            return indexed;
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            return ctx;
        }
        ClassLoader self = ReloadOrchestrator.class.getClassLoader();
        return self != null ? self : ClassLoader.getSystemClassLoader();
    }

    private void markApplied(List<Prepared> items) {
        long now = System.currentTimeMillis();
        for (Prepared item : items) {
            recentKeys.put(new ClassKey(item.binaryName, item.sha256), now);
        }
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

    private String primaryUnsupportedKind(ClassDelta delta) {
        if (delta.anonymousIndexShiftLikely) {
            return "anonymous-index-shift";
        }
        List<String> capabilities = backend.capabilityNames();
        ChangeKind first = null;
        for (ChangeKind kind : delta.kinds) {
            if (first == null) {
                first = kind;
            }
            if (capabilities == null || !capabilities.contains(kind.name())) {
                return kind.name();
            }
        }
        if (first != null) {
            return first.name();
        }
        return "UNSUPPORTED";
    }

    private List<AdapterOutcome> notifyAdapters(List<Prepared> prepared, List<ResourcePayload> resources) {
        List<AdapterOutcome> outcomes = new ArrayList<>();
        if (!prepared.isEmpty()) {
            for (Prepared item : prepared) {
                if (item.definedClass != null) {
                    adapters.onNewClass(item.definedClass);
                }
            }
            outcomes.addAll(adapters.onClassesReloaded(classReloadEvent(prepared)));
        }
        if (resources != null && !resources.isEmpty()) {
            outcomes.addAll(adapters.onResourcesChanged(resourceEvent(resources)));
        }
        return outcomes;
    }

    private ClassReloadEvent classReloadEvent(List<Prepared> prepared) {
        List<ReloadedClass> classes = new ArrayList<>();
        for (Prepared item : prepared) {
            List<String> kinds = kindNames(item.delta);
            if (item.definedClass != null) {
                classes.add(new ReloadedClass(item.binaryName, item.definedClass, kinds));
                continue;
            }
            for (Class<?> loaded : item.loaded) {
                classes.add(new ReloadedClass(item.binaryName, loaded, kinds));
            }
        }
        return new ClassReloadEvent(
                adapterContext,
                classes,
                backend.id(),
                Capabilities.fromNames(backend.capabilityNames()));
    }

    private ResourceChangeEvent resourceEvent(List<ResourcePayload> resources) {
        List<ResourceChangeEvent.ChangedResource> changed = new ArrayList<>(resources.size());
        for (ResourcePayload resource : resources) {
            changed.add(
                    new ResourceChangeEvent.ChangedResource(
                            resource.classpathName, resource.path, resource.sha256));
        }
        return new ResourceChangeEvent(adapterContext, changed);
    }

    private ReloadResult result(String status, long startedMs, List<ClassOutcome> classes, String message) {
        return result(status, startedMs, classes, List.of(), message);
    }

    private ReloadResult result(
            String status,
            long startedMs,
            List<ClassOutcome> classes,
            List<AdapterOutcome> adapters,
            String message) {
        ReloadResult result = new ReloadResult();
        result.status = status;
        result.durationMs = Math.max(0L, System.currentTimeMillis() - startedMs);
        result.classes = classes;
        result.adapters = adapters == null ? List.of() : adapters;
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
        final String sha256;
        final List<Class<?>> loaded;
        final ClassDelta delta;
        Class<?> definedClass;

        Prepared(String binaryName, byte[] bytes, String sha256, List<Class<?>> loaded, ClassDelta delta) {
            this.binaryName = binaryName;
            this.bytes = bytes;
            this.sha256 = sha256;
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
