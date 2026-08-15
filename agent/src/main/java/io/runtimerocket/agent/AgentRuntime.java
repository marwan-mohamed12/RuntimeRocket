package io.runtimerocket.agent;

import io.runtimerocket.agent.config.Handshake;
import io.runtimerocket.agent.config.HandshakeFile;
import io.runtimerocket.agent.config.ProductionGuard;
import io.runtimerocket.agent.config.Tokens;
import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.net.LoopbackServer;
import io.runtimerocket.agent.reload.ClassIndex;
import io.runtimerocket.agent.reload.ReloadBackend;
import io.runtimerocket.agent.reload.ReloadOrchestrator;
import io.runtimerocket.agent.reload.StandardHotSwapBackend;
import io.runtimerocket.agent.watch.ClassPathWatcher;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Process-wide agent state. Started from {@link AgentMain}. */
public final class AgentRuntime {

    private static final AgentRuntime INSTANCE = new AgentRuntime();

    private final Object lock = new Object();
    private boolean started;
    private AgentOptions options;
    private AgentLog log;
    private String token;
    private Path handshakePath;
    private LoopbackServer server;
    private ClassIndex classIndex;
    private ReloadOrchestrator orchestrator;
    private ReloadBackend backend;
    private ClassPathWatcher watcher;
    private Thread shutdownHook;
    private boolean lateAttach;

    private AgentRuntime() {}

    public static AgentRuntime get() {
        return INSTANCE;
    }

    public void start(AgentOptions opt, Instrumentation inst, boolean late) {
        synchronized (lock) {
            if (started) {
                if (log != null) {
                    log.warn("agent already started; ignoring duplicate start");
                }
                return;
            }
            this.options = opt;
            this.log = new AgentLog(opt.log, opt.logFile);
            this.lateAttach = late;
            try {
                doStart(inst);
                started = true;
            } catch (RuntimeException | IOException e) {
                rollback();
                if (e instanceof AgentStartException start) {
                    throw start;
                }
                throw new AgentStartException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
            }
        }
    }

    private void doStart(Instrumentation inst) throws IOException {
        if (ProductionGuard.shouldAbort()) {
            log.error("refusing to start in a production-like environment (set RUNTIMEROCKET_ALLOW_NONDEV=true to override)");
            throw new AgentStartException("production environment detected");
        }
        if (ProductionGuard.shouldWarnProdPort()) {
            log.warn("prod profile and PORT are set; continuing (warning only)");
        }
        if (!inst.isRedefineClassesSupported()) {
            throw new AgentStartException("Instrumentation.redefineClasses is not supported");
        }

        this.token = resolveToken(options);
        this.backend = selectBackend(options.backend, inst);
        this.classIndex = new ClassIndex(inst);
        this.classIndex.install();
        WatchDirs watchDirs = WatchDirs.of(options.watchDirs);
        this.watcher = new ClassPathWatcher(options, log);
        this.orchestrator = new ReloadOrchestrator(inst, backend, classIndex, watchDirs, watcher, log);
        this.server = new LoopbackServer(
                token, backend.id(), backend.capabilityNames(), orchestrator, HandshakeFile.pathForPid(pid()), log);
        int bound = server.bind(options.port);
        Handshake handshake = new Handshake(
                pid(),
                bound,
                token,
                backend.id(),
                backend.capabilityNames(),
                AgentVersion.VERSION,
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
        this.handshakePath = HandshakeFile.write(handshake);
        server.start();
        watcher.start();
        this.shutdownHook = new Thread(this::stopFromHook, "rr-agent-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        log.info("agent "
                + AgentVersion.VERSION
                + " started pid="
                + pid()
                + " backend="
                + backend.id()
                + " port="
                + bound
                + (lateAttach ? " late=true" : ""));
    }

    private ReloadBackend selectBackend(String requested, Instrumentation inst) {
        String id = requested == null ? AgentOptions.BACKEND_AUTO : requested;
        if (AgentOptions.BACKEND_AUTO.equals(id) || AgentOptions.BACKEND_STANDARD.equals(id)) {
            StandardHotSwapBackend standard = new StandardHotSwapBackend();
            if (!standard.probe(inst)) {
                throw new AgentStartException("standard HotSwap is not available");
            }
            return standard;
        }
        throw new AgentStartException("backend '" + id + "' is not available");
    }

    private static String resolveToken(AgentOptions options) {
        if (options.tokenFile != null) {
            return Tokens.readFile(options.tokenFile);
        }
        if (options.token != null && !options.token.isBlank()) {
            return options.token;
        }
        return Tokens.generate();
    }

    private static long pid() {
        return ProcessHandle.current().pid();
    }

    public boolean isStarted() {
        synchronized (lock) {
            return started;
        }
    }

    public int port() {
        synchronized (lock) {
            return server == null ? -1 : server.port();
        }
    }

    public String token() {
        synchronized (lock) {
            return token;
        }
    }

    public Path handshakePath() {
        synchronized (lock) {
            return handshakePath;
        }
    }

    public String backendId() {
        synchronized (lock) {
            return backend == null ? null : backend.id();
        }
    }

    public ReloadOrchestrator orchestrator() {
        synchronized (lock) {
            return orchestrator;
        }
    }

    public void stop() {
        synchronized (lock) {
            if (!started && server == null && handshakePath == null) {
                return;
            }
            stopFromHook();
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM is already shutting down
                }
                shutdownHook = null;
            }
        }
    }

    private void stopFromHook() {
        if (log != null) {
            log.info("agent shutting down");
        }
        if (watcher != null) {
            watcher.close();
        }
        if (server != null) {
            server.close();
        }
        uninstallIndex();
        HandshakeFile.deleteQuietly(handshakePath);
        rollbackFields();
        started = false;
    }

    private void rollback() {
        if (server != null) {
            server.close();
        }
        if (watcher != null) {
            watcher.close();
        }
        uninstallIndex();
        HandshakeFile.deleteQuietly(handshakePath);
        rollbackFields();
        started = false;
    }

    private void uninstallIndex() {
        if (classIndex != null) {
            classIndex.uninstall();
        }
    }

    private void rollbackFields() {
        server = null;
        watcher = null;
        orchestrator = null;
        classIndex = null;
        backend = null;
        handshakePath = null;
        token = null;
    }
}
