package io.runtimerocket.agent.net;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.AgentVersion;
import io.runtimerocket.agent.config.Tokens;
import io.runtimerocket.agent.reload.ReloadOrchestrator;
import io.runtimerocket.protocol.Frame;
import io.runtimerocket.protocol.Goodbye;
import io.runtimerocket.protocol.Hello;
import io.runtimerocket.protocol.HelloOk;
import io.runtimerocket.protocol.Ping;
import io.runtimerocket.protocol.Pong;
import io.runtimerocket.protocol.Protocol;
import io.runtimerocket.protocol.ProtocolCodec;
import io.runtimerocket.protocol.ProtocolException;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JSON-lines server bound to {@code 127.0.0.1}. One client at a time; Hello is required; idle
 * timeout is ten minutes and does not drop a live debugger session.
 */
public final class LoopbackServer implements AutoCloseable {

    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    private final String token;
    private final String backendId;
    private final List<String> capabilities;
    private final ReloadOrchestrator orchestrator;
    private final Path handshakeFile;
    private final AgentLog log;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object clientLock = new Object();

    private ServerSocket server;
    private Thread acceptThread;
    private volatile boolean clientActive;

    public LoopbackServer(
            String token,
            String backendId,
            List<String> capabilities,
            ReloadOrchestrator orchestrator,
            Path handshakeFile,
            AgentLog log) {
        this.token = token;
        this.backendId = backendId;
        this.capabilities = List.copyOf(capabilities);
        this.orchestrator = orchestrator;
        this.handshakeFile = handshakeFile;
        this.log = log;
    }

    public int bind(int port) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
        this.server = socket;
        return socket.getLocalPort();
    }

    public int port() {
        return server == null ? -1 : server.getLocalPort();
    }

    public void start() {
        if (server == null) {
            throw new IllegalStateException("server not bound");
        }
        acceptThread = new Thread(this::acceptLoop, "rr-agent-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!closed.get() && server != null && !server.isClosed()) {
            try {
                Socket client = server.accept();
                synchronized (clientLock) {
                    if (clientActive) {
                        silentClose(client);
                        continue;
                    }
                    clientActive = true;
                }
                Thread handler = new Thread(() -> handleClient(client), "rr-agent-client");
                handler.setDaemon(true);
                handler.start();
            } catch (SocketException e) {
                if (closed.get()) {
                    return;
                }
                if (log != null) {
                    log.debug("accept failed: " + e.getMessage());
                }
            } catch (IOException e) {
                if (!closed.get() && log != null) {
                    log.warn("accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout((int) IDLE_TIMEOUT.toMillis());
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            boolean greeted = false;
            String session = null;
            while (!closed.get() && !client.isClosed()) {
                String line;
                try {
                    line = readLine(in, Protocol.MAX_FRAME_BYTES);
                } catch (SocketTimeoutException e) {
                    if (shouldKeepIdleConnection()) {
                        continue;
                    }
                    break;
                } catch (ProtocolException e) {
                    if (log != null) {
                        log.warn("closing client: " + e.getMessage());
                    }
                    break;
                }
                if (line == null) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                Frame frame;
                try {
                    frame = ProtocolCodec.decode(line);
                } catch (ProtocolException e) {
                    if (log != null) {
                        log.warn("invalid frame: " + e.getMessage());
                    }
                    break;
                }
                if (!greeted) {
                    if (!(frame instanceof Hello hello)) {
                        break;
                    }
                    if (!Tokens.equal(token, hello.token)) {
                        break;
                    }
                    if (hello.protocolVersion != null
                            && !hello.protocolVersion.isBlank()
                            && !Protocol.VERSION.equals(hello.protocolVersion)) {
                        break;
                    }
                    greeted = true;
                    session = hello.session;
                    write(out, helloOk(session, hello.seq));
                    continue;
                }
                if (frame instanceof Ping ping) {
                    Pong pong = new Pong();
                    pong.session = sessionOf(ping, session);
                    pong.seq = ping.seq;
                    write(out, pong);
                } else if (frame instanceof ReloadRequest reload) {
                    if (reload.session == null) {
                        reload.session = session;
                    }
                    ReloadResult result = orchestrator.reload(reload);
                    result.session = sessionOf(reload, session);
                    result.seq = reload.seq;
                    write(out, result);
                } else if (frame instanceof Goodbye) {
                    break;
                } else if (log != null) {
                    log.debug("ignored frame type=" + frame.type);
                }
            }
        } catch (IOException e) {
            if (!closed.get() && log != null) {
                log.debug("client I/O: " + e.getMessage());
            }
        } finally {
            silentClose(client);
            synchronized (clientLock) {
                clientActive = false;
            }
        }
    }

    private HelloOk helloOk(String session, long seq) {
        HelloOk ok = new HelloOk();
        ok.session = session;
        ok.seq = seq;
        ok.backend = backendId;
        ok.capabilities = capabilities;
        ok.agentVersion = AgentVersion.VERSION;
        ok.vmName = System.getProperty("java.vm.name");
        ok.javaVersion = System.getProperty("java.version");
        return ok;
    }

    private static String sessionOf(Frame frame, String fallback) {
        return frame.session != null ? frame.session : fallback;
    }

    private boolean shouldKeepIdleConnection() {
        if (handshakeFile != null && Files.exists(handshakeFile)) {
            return true;
        }
        return isJvmtiSuspended();
    }

    static boolean isJvmtiSuspended() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = mx.getAllThreadIds();
        ThreadInfo[] infos = mx.getThreadInfo(ids, 0);
        if (infos == null) {
            return false;
        }
        for (ThreadInfo info : infos) {
            if (info != null && info.isSuspended()) {
                return true;
            }
        }
        return false;
    }

    static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b == '\r') {
                continue;
            }
            buf.write(b);
            if (buf.size() > maxBytes) {
                throw new ProtocolException("framed message exceeds " + maxBytes + " bytes");
            }
        }
        if (b == -1 && buf.size() == 0) {
            return null;
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static void write(OutputStream out, Frame frame) throws IOException {
        byte[] utf8 = ProtocolCodec.encodeLine(frame).getBytes(StandardCharsets.UTF_8);
        out.write(utf8);
        out.flush();
    }

    private static void silentClose(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closing
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }
}
