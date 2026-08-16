package io.runtimerocket.agent.watch;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.config.PackageFilter;
import io.runtimerocket.agent.config.RocketXmlParser;
import io.runtimerocket.agent.reload.Hashes;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ResourcePayload;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Recursive {@link WatchService} over classpath and resource directories. Windows often delivers
 * two {@code ENTRY_MODIFY} events per save; those are collapsed by a per-file debounce then a
 * short batch settle.
 */
public final class ClassPathWatcher implements AutoCloseable {

    public static final long BATCH_SETTLE_MS = 50L;
    public static final long DEFAULT_POLL_INTERVAL_MS = 1000L;

    private final AgentOptions options;
    private final AgentLog log;
    private final List<Path> classpathDirs;
    private final List<Path> resourceDirs;
    private final List<Path> allRoots;
    private final PackageFilter packages;
    private final long fileDebounceMs;
    private final long batchSettleMs;
    private final long pollIntervalMs;
    private final ConcurrentHashMap<Path, Long> suppressedUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, String> lastHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, WatchKey> keys = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final Map<Path, ScheduledFuture<?>> fileDebounces = new ConcurrentHashMap<>();
    private final Set<Path> batch = ConcurrentHashMap.newKeySet();

    private volatile Consumer<ReloadRequest> handler;
    private volatile boolean closed;
    private volatile boolean started;
    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> batchFuture;
    private ScheduledFuture<?> pollFuture;

    public ClassPathWatcher(AgentOptions options, AgentLog log) {
        this(options, log, List.of(), List.of(), PackageFilter.allowAll());
    }

    public ClassPathWatcher(
            AgentOptions options,
            AgentLog log,
            List<Path> classpathDirs,
            List<Path> resourceDirs,
            PackageFilter packages) {
        this(options, log, classpathDirs, resourceDirs, packages, BATCH_SETTLE_MS, DEFAULT_POLL_INTERVAL_MS);
    }

    public ClassPathWatcher(
            AgentOptions options,
            AgentLog log,
            List<Path> classpathDirs,
            List<Path> resourceDirs,
            PackageFilter packages,
            long batchSettleMs,
            long pollIntervalMs) {
        this.options = options;
        this.log = log;
        this.classpathDirs = uniqueAbs(classpathDirs);
        this.resourceDirs = uniqueAbs(resourceDirs);
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.addAll(this.classpathDirs);
        roots.addAll(this.resourceDirs);
        this.allRoots = List.copyOf(roots);
        this.packages = packages == null ? PackageFilter.allowAll() : packages;
        this.fileDebounceMs = options == null ? 150L : Math.max(0L, options.debounceMs);
        this.batchSettleMs = Math.max(0L, batchSettleMs);
        this.pollIntervalMs = Math.max(0L, pollIntervalMs);
    }

    public void setHandler(Consumer<ReloadRequest> handler) {
        this.handler = handler;
    }

    public void start() {
        if (options == null || !options.watch || closed) {
            return;
        }
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rr-watch-debounce");
                t.setDaemon(true);
                return t;
            });
            seedSnapshots();
            try {
                watchService = FileSystems.getDefault().newWatchService();
                for (Path root : allRoots) {
                    registerTree(root);
                }
                watchThread = new Thread(this::loop, "rr-watch-service");
                watchThread.setDaemon(true);
                watchThread.start();
            } catch (IOException e) {
                if (log != null) {
                    log.warn("WatchService unavailable; using mtime poll: " + e.getMessage());
                }
            }
            if (pollIntervalMs > 0) {
                pollFuture = scheduler.scheduleAtFixedRate(
                        this::poll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean isRunning() {
        return started && !closed && options != null && options.watch;
    }

    public void suppress(Path path, long durationMs) {
        if (path == null) {
            return;
        }
        suppressedUntil.put(canon(path), System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    public boolean isSuppressed(Path path) {
        if (path == null) {
            return false;
        }
        Path key = canon(path);
        Long until = suppressedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            suppressedUntil.remove(key, until);
            return false;
        }
        return true;
    }

    /**
     * Same entry used by {@link WatchService} events and the mtime poll. Tests call this to inject
     * {@code ENTRY_MODIFY} without waiting on the OS.
     */
    public void notifyChanged(Path path) {
        if (closed || options == null || !options.watch || path == null || scheduler == null) {
            return;
        }
        Path normalized = canon(path);
        if (shouldIgnore(normalized) || isSuppressed(normalized)) {
            return;
        }
        ScheduledFuture<?> prev = fileDebounces.put(
                normalized,
                scheduler.schedule(() -> fileReady(normalized), fileDebounceMs, TimeUnit.MILLISECONDS));
        if (prev != null) {
            prev.cancel(false);
        }
    }

    private void fileReady(Path path) {
        if (closed) {
            return;
        }
        fileDebounces.remove(path);
        batch.add(path);
        synchronized (lock) {
            if (batchFuture != null) {
                batchFuture.cancel(false);
            }
            if (scheduler == null || closed) {
                return;
            }
            batchFuture = scheduler.schedule(this::flushBatch, batchSettleMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushBatch() {
        List<Path> paths = new ArrayList<>();
        synchronized (lock) {
            batchFuture = null;
            paths.addAll(batch);
            batch.clear();
        }
        if (paths.isEmpty() || closed) {
            return;
        }
        List<ClassPayload> classes = new ArrayList<>();
        List<ResourcePayload> resources = new ArrayList<>();
        for (Path path : paths) {
            classify(path, classes, resources);
        }
        if (classes.isEmpty() && resources.isEmpty()) {
            return;
        }
        Consumer<ReloadRequest> sink = handler;
        if (sink == null) {
            return;
        }
        ReloadRequest request = new ReloadRequest();
        request.byReference = true;
        request.trigger = ReloadRequest.TRIGGER_WATCH;
        request.classes = classes;
        request.resources = resources;
        try {
            sink.accept(request);
        } catch (RuntimeException e) {
            if (log != null) {
                log.warn("watch reload failed: " + e.getMessage());
            }
        }
    }

    private void classify(Path path, List<ClassPayload> classes, List<ResourcePayload> resources) {
        if (!Files.isRegularFile(path)) {
            if (Files.isDirectory(path)) {
                registerTree(path);
            }
            return;
        }
        if (shouldIgnore(path)) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            return;
        }
        String hash = Hashes.sha256Hex(bytes);
        remember(path, bytes.length, hash);
        if (isSuppressed(path)) {
            lastHashes.put(path, hash);
            return;
        }
        String previous = lastHashes.put(path, hash);
        if (previous != null && Hashes.equalHex(previous, hash)) {
            return;
        }
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".class")) {
            String binaryName = className(bytes);
            if (binaryName == null || !packages.accepts(binaryName)) {
                return;
            }
            classes.add(new ClassPayload(binaryName, path.toString(), hash, null));
            return;
        }
        resources.add(new ResourcePayload(resourceName(path), path.toString(), hash));
    }

    private void loop() {
        WatchService service = watchService;
        if (service == null) {
            return;
        }
        while (!closed) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                return;
            }
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    poll();
                    continue;
                }
                Object context = event.context();
                if (!(context instanceof Path name)) {
                    continue;
                }
                Path child = dir.resolve(name);
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    registerTree(child);
                }
                notifyChanged(child);
            }
            if (!key.reset()) {
                keys.values().remove(key);
            }
        }
    }

    private void poll() {
        if (closed) {
            return;
        }
        for (Path root : allRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (hiddenDir(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        registerDir(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (shouldIgnore(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        Snapshot prev = snapshots.get(canon(file));
                        if (prev == null
                                || prev.size != attrs.size()
                                || prev.mtime != attrs.lastModifiedTime().toMillis()) {
                            notifyChanged(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // poll is best-effort
            }
        }
    }

    private void seedSnapshots() {
        for (Path root : allRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        return hiddenDir(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (shouldIgnore(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        try {
                            byte[] bytes = Files.readAllBytes(file);
                            String hash = Hashes.sha256Hex(bytes);
                            Path key = canon(file);
                            lastHashes.put(key, hash);
                            snapshots.put(key, new Snapshot(attrs.size(), attrs.lastModifiedTime().toMillis()));
                        } catch (IOException ignored) {
                            // skip unreadable files at start
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // seed is best-effort
            }
        }
    }

    private void registerTree(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (hiddenDir(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    registerDir(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            if (log != null && log.isDebugEnabled()) {
                log.debug("cannot watch " + root + ": " + e.getMessage());
            }
        }
    }

    private void registerDir(Path dir) {
        WatchService service = watchService;
        if (service == null || dir == null || !Files.isDirectory(dir)) {
            return;
        }
        Path key = canon(dir);
        if (keys.containsKey(key)) {
            return;
        }
        try {
            WatchKey watchKey = dir.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            keys.put(key, watchKey);
        } catch (IOException e) {
            if (log != null && log.isDebugEnabled()) {
                log.debug("cannot register " + dir + ": " + e.getMessage());
            }
        }
    }

    private void remember(Path path, long size, String hash) {
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            mtime = System.currentTimeMillis();
        }
        snapshots.put(path, new Snapshot(size, mtime));
    }

    private String resourceName(Path file) {
        Path best = null;
        for (Path root : allRoots) {
            Path abs = canon(root);
            if (file.startsWith(abs) && (best == null || abs.getNameCount() > best.getNameCount())) {
                best = abs;
            }
        }
        if (best == null) {
            return file.getFileName().toString();
        }
        return best.relativize(file).toString().replace('\\', '/');
    }

    static boolean shouldIgnore(Path path) {
        if (path == null) {
            return true;
        }
        Path namePath = path.getFileName();
        if (namePath == null) {
            return true;
        }
        String name = namePath.toString();
        if (name.startsWith(".")) {
            return true;
        }
        if (name.endsWith(".tmp") || name.endsWith(".swp")) {
            return true;
        }
        if (name.contains(".class.__jb_")) {
            return true;
        }
        return RocketXmlParser.FILE_NAME.equals(name);
    }

    static boolean hiddenDir(Path dir) {
        Path name = dir.getFileName();
        return name != null && name.toString().startsWith(".");
    }

    static Path canon(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(abs)) {
                return abs.toRealPath();
            }
        } catch (IOException ignored) {
            // fall back to the normalized absolute path
        }
        return abs;
    }

    private static String className(byte[] bytes) {
        try {
            return new ClassReader(bytes).getClassName().replace('/', '.');
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Path> uniqueAbs(List<Path> dirs) {
        if (dirs == null || dirs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        for (Path dir : dirs) {
            if (dir != null) {
                out.add(dir.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(out);
    }

    @Override
    public void close() {
        closed = true;
        synchronized (lock) {
            started = false;
            if (batchFuture != null) {
                batchFuture.cancel(false);
                batchFuture = null;
            }
            if (pollFuture != null) {
                pollFuture.cancel(false);
                pollFuture = null;
            }
            for (ScheduledFuture<?> future : fileDebounces.values()) {
                future.cancel(false);
            }
            fileDebounces.clear();
            batch.clear();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // shutting down
            }
            watchService = null;
        }
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }
        keys.clear();
        suppressedUntil.clear();
    }

    private record Snapshot(long size, long mtime) {}
}
