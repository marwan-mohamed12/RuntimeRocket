package io.runtimerocket.agent;

import io.runtimerocket.protocol.Frame;
import io.runtimerocket.protocol.Hello;
import io.runtimerocket.protocol.HelloOk;
import io.runtimerocket.protocol.Ping;
import io.runtimerocket.protocol.Pong;
import io.runtimerocket.protocol.ProtocolCodec;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Tiny RR/1 client used by in-process agent tests. */
final class AgentClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedWriter out;
    private final BufferedReader in;
    private final String session = UUID.randomUUID().toString();

    AgentClient(int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
        socket.setSoTimeout(10_000);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    HelloOk handshake(String token) throws IOException {
        Hello hello = new Hello();
        hello.session = session;
        hello.seq = 1;
        hello.token = token;
        hello.pluginVersion = "test";
        write(hello);
        Frame first = read();
        if (!(first instanceof HelloOk ok)) {
            throw new IOException("expected hello-ok, got " + (first == null ? "eof" : first.type));
        }
        return ok;
    }

    Pong ping() throws IOException {
        Ping ping = new Ping();
        ping.session = session;
        ping.seq = 2;
        write(ping);
        Frame frame = read();
        if (!(frame instanceof Pong pong)) {
            throw new IOException("expected pong, got " + (frame == null ? "eof" : frame.type));
        }
        return pong;
    }

    ReloadResult reload(ReloadRequest request) throws IOException {
        if (request.session == null) {
            request.session = session;
        }
        write(request);
        Frame frame = read();
        if (!(frame instanceof ReloadResult result)) {
            throw new IOException("expected reload-result, got " + (frame == null ? "eof" : frame.type));
        }
        return result;
    }

    void writeRaw(String jsonLine) throws IOException {
        out.write(jsonLine);
        if (!jsonLine.endsWith("\n")) {
            out.write('\n');
        }
        out.flush();
    }

    Frame read() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        return ProtocolCodec.decode(line);
    }

    private void write(Frame frame) throws IOException {
        out.write(ProtocolCodec.encodeLine(frame));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
