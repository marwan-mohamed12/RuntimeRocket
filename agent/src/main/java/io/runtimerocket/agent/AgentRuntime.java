package io.runtimerocket.agent;

import io.runtimerocket.agent.config.Handshake;
import io.runtimerocket.agent.config.HandshakeFile;
import io.runtimerocket.agent.config.ProductionGuard;
import io.runtimerocket.agent.config.RocketXmlDiscovery;
import io.runtimerocket.agent.config.RocketXmlDocuments;
import io.runtimerocket.agent.config.Tokens;
import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.net.LoopbackServer;
import io.runtimerocket.agent.reload.AdapterHost;
import io.runtimerocket.agent.reload.AgentAdapterContext;
import io.runtimerocket.agent.reload.BackendSelector;
import io.runtimerocket.agent.reload.ClassIndex;
import io.runtimerocket.agent.reload.ReloadBackend;
import io.runtimerocket.agent.reload.ReloadOrchestrator;
import io.runtimerocket.agent.spi.AdapterContext;
import io.runtimerocket.agent.watch.ClassPathWatcher;
import io.runtimerocket.protocol.AdapterOutcome;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
    private AdapterHost adapterHost;
    private ClassPathWatcher watcher;
    private RocketXmlDocuments rocketXml;
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
        this.backend = BackendSelector.select(options.backend, inst);
        this.classIndex = new ClassIndex(inst);
        this.classIndex.install();
        this.rocketXml = RocketXmlDiscovery.discover(options, inst, log);
        List<Path> classpathDirs = new ArrayList<>();
        List<Path> resourceDirs = new ArrayList<>();
        classpathDirs.addAll(rocketXml.classpathDirs());
        resourceDirs.addAll(rocketXml.resourceDirs());
        classpathDirs.addAll(options.watchDirs);
        LinkedHashSet<Path> jail = new LinkedHashSet<>();
        jail.addAll(classpathDirs);
        jail.addAll(resourceDirs);
        WatchDirs watchDirs = WatchDirs.of(new ArrayList<>(jail));
        this.watcher = new ClassPathWatcher(
                options, log, classpathDirs, resourceDirs, rocketXml.packageFilter());
        this.adapterHost =
                AdapterHost.load(AgentRuntime.class.getClassLoader(), options.disabledAdapters, log);
        AdapterContext adapterContext = new AgentAdapterContext(classIndex, lateAttach, log, inst);
        this.orchestrator =
                new ReloadOrchestrator(
                        inst, backend, classIndex, watchDirs, watcher, log, adapterHost, lateAttach);
        this.watcher.setHandler(orchestrator::reload);
        adapterHost.onAgentStart(adapterContext);
        List<String> lateNotes = new ArrayList<>();
        if (lateAttach) {
            for (AdapterOutcome outcome : adapterHost.onLateAttach(adapterContext)) {
                if (outcome != null
                        && outcome.status != null
                        && !AdapterOutcome.SUCCESS.equals(outcome.status)) {
                    if (outcome.detail != null && !outcome.detail.isBlank()) {
                        lateNotes.add(outcome.detail);
                    }
                    if (log != null) {
                        log.warn("adapter "
                                + outcome.adapterId
                                + " late-attach "
                                + outcome.status
                                + (outcome.detail == null ? "" : " " + outcome.detail));
                    }
                }
            }
        }
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
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                lateNotes);
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
        if (options.watch) {
            log.info("watching "
                    + classpathDirs.size()
                    + " classpath dirs, "
                    + resourceDirs.size()
                    + " resource dir"
                    + (resourceDirs.size() == 1 ? "" : "s"));
        }
    }

    public RocketXmlDocuments rocketXml() {
        synchronized (lock) {
            return rocketXml;
        }
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

    public AdapterHost adapterHost() {
        synchronized (lock) {
            return adapterHost;
        }
    }

    public ClassPathWatcher watcher() {
        synchronized (lock) {
            return watcher;
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
        shutdownAdapters();
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
        shutdownAdapters();
        uninstallIndex();
        HandshakeFile.deleteQuietly(handshakePath);
        rollbackFields();
        started = false;
    }

    private void shutdownAdapters() {
        if (adapterHost != null) {
            adapterHost.onAgentShutdown();
        }
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
        adapterHost = null;
        handshakePath = null;
        token = null;
        rocketXml = null;
    }
}
