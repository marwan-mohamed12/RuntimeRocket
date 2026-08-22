package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HybrisResourcePolicyTest {

    @Test
    void itemsXmlAndSpringXmlRequireRestart() {
        assertEquals(HybrisResourcePolicy.ITEMS_XML_DETAIL, HybrisResourcePolicy.restartDetailForName("cchcore-items.xml"));
        assertEquals(HybrisResourcePolicy.SPRING_XML_DETAIL, HybrisResourcePolicy.restartDetailForName("cchcore-spring.xml"));
        assertEquals(HybrisResourcePolicy.PROPERTIES_DETAIL, HybrisResourcePolicy.restartDetailForName("local.properties"));
        assertEquals(HybrisResourcePolicy.IMPEX_DETAIL, HybrisResourcePolicy.restartDetailForName("catalog.impex"));
        assertEquals(HybrisResourcePolicy.JSP_DETAIL, HybrisResourcePolicy.restartDetailForName("cart.jsp"));
        assertNull(HybrisResourcePolicy.restartDetailForName("Foo.java"));
        assertNull(HybrisResourcePolicy.restartDetailForName("logback-spring.xml"));
    }

    @Test
    void springAdapterIgnoresHybrisNamesWithoutCommerceOnClasspath() {
        SpringAdapter adapter = new SpringAdapter();
        AdapterOutcome outcome =
                adapter.onResourcesChanged(
                        new ResourceChangeEvent(
                                SpringTestSupport.context(false),
                                List.of(
                                        new ResourceChangeEvent.ChangedResource(
                                                "cchcore-items.xml", "/ext/cchcore-items.xml", "a".repeat(64)))));
        assertEquals(AdapterOutcome.SUCCESS, outcome.status);
    }

    @Test
    void springAdapterSurfacesHybrisItemsXmlWhenCommercePresent() {
        IsolatedLoader loader = new IsolatedLoader(HybrisResourcePolicyTest.class.getClassLoader());
        loader.define("de.hybris.platform.core.Registry", emptyClass("de.hybris.platform.core.Registry"));
        SpringAdapter adapter = new SpringAdapter();
        AdapterOutcome outcome =
                adapter.onResourcesChanged(
                        new ResourceChangeEvent(
                                SpringTestSupport.context(false, loader),
                                List.of(
                                        new ResourceChangeEvent.ChangedResource(
                                                "cchcore-items.xml", "/ext/cchcore-items.xml", "a".repeat(64)))));
        assertEquals(AdapterOutcome.RESTART_REQUIRED, outcome.status);
        assertEquals(HybrisResourcePolicy.ITEMS_XML_DETAIL, outcome.detail);
    }

    private static byte[] emptyClass(String binaryName) {
        String internal = binaryName.replace('.', '/');
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(
                org.objectweb.asm.Opcodes.V17,
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SUPER,
                internal,
                null,
                "java/lang/Object",
                null);
        org.objectweb.asm.MethodVisitor ctor =
                writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(
                org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
