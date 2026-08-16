package io.runtimerocket.it;

import io.runtimerocket.agent.AgentMain;
import io.runtimerocket.agent.AgentRuntime;
import io.runtimerocket.agent.config.Handshake;
import io.runtimerocket.agent.config.HandshakeFile;
import io.runtimerocket.fixtures.springboot.ExistingGreeter;
import io.runtimerocket.fixtures.springboot.FixtureApp;
import io.runtimerocket.frameworks.spring.SpringAdapter;
import io.runtimerocket.frameworks.spring.SpringContextTracker;
import io.runtimerocket.frameworks.spring.SpringVersionGate;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;

import net.bytebuddy.agent.ByteBuddyAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.SpringVersion;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class SpringContextTrackerIT {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private ConfigurableApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        AgentRuntime.get().stop();
        SpringContextTracker.reset();
    }

    @Test
    void addServiceWhileRunningRegistersAndPreservesSingleton() throws Exception {
        assertTrue(SpringVersion.getVersion().startsWith("7."), SpringVersion.getVersion());
        assertEquals(
                SpringVersionGate.FW7_HELPER,
                SpringVersionGate.helperClassName(SpringContextTrackerIT.class.getClassLoader()));

        startAgent(false);
        context = FixtureApp.run();
        assertFalse(SpringContextTracker.isEmpty(), "premain hook must capture finishRefresh");

        ExistingGreeter before = context.getBean(ExistingGreeter.class);
        String name = "io.runtimerocket.fixtures.springboot.HotService" + NEXT.getAndIncrement();
        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.classes = List.of(new ClassPayload(name, null, null, Base64.getEncoder().encodeToString(serviceBytes(name))));

        ReloadResult result = AgentRuntime.get().orchestrator().reload(request);
        assertEquals(ReloadResult.SUCCESS, result.status, result.message);
        Class<?> addedType = Class.forName(name, false, context.getClassLoader());
        Object added = context.getBean(addedType);
        assertNotNull(added);
        assertSame(before, context.getBean(ExistingGreeter.class));
    }

    @Test
    void lateAttachIdleBootReturnsPartialWithInactiveString() {
        context = FixtureApp.run();
        assertTrue(SpringContextTracker.isEmpty());

        long started = System.nanoTime();
        startAgent(true);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Handshake handshake = HandshakeFile.read(AgentRuntime.get().handshakePath());
        assertTrue(
                handshake.notes.contains(SpringAdapter.INACTIVE_DETAIL),
                String.valueOf(handshake.notes));
        assertTrue(SpringContextTracker.isEmpty());
        assertTrue(elapsedMs < 500L, "late attach must not wait 2s: " + elapsedMs + "ms");
    }

    private static void startAgent(boolean late) {
        String token = "t" + UUID.randomUUID().toString().replace("-", "");
        String args = "port=0,watch=false,debounceMs=150,token=" + token + ",backend=standard,log=debug";
        if (late) {
            AgentMain.agentmain(args, ByteBuddyAgent.install());
        } else {
            AgentMain.premain(args, ByteBuddyAgent.install());
        }
    }

    private static byte[] serviceBytes(String binaryName) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        AnnotationVisitor ann = writer.visitAnnotation("Lorg/springframework/stereotype/Service;", true);
        ann.visitEnd();
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
