package io.runtimerocket.agent.reload;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks loaded application classes and the last bytes seen on DEFINE/retransform. Used to resolve
 * {@code Class<?>} and previous bytecode for classification.
 */
public final class ClassIndex {

    private final Instrumentation inst;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WeakReference<Class<?>>>> byName =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> bytesByLoaderAndName = new ConcurrentHashMap<>();
    private final IndexingTransformer transformer = new IndexingTransformer();

    public ClassIndex(Instrumentation inst) {
        this.inst = inst;
    }

    public void install() {
        inst.addTransformer(transformer, true);
    }

    public Optional<Class<?>> find(String binaryName) {
        List<Class<?>> all = findAll(binaryName);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public List<Class<?>> findAll(String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return List.of();
        }
        Set<Class<?>> out = new LinkedHashSet<>();
        CopyOnWriteArrayList<WeakReference<Class<?>>> refs = byName.get(binaryName);
        if (refs != null) {
            for (WeakReference<Class<?>> ref : refs) {
                Class<?> cls = ref.get();
                if (cls != null) {
                    out.add(cls);
                }
            }
        }
        for (Class<?> cls : inst.getAllLoadedClasses()) {
            if (binaryName.equals(cls.getName()) && !skipBinary(cls.getName())) {
                out.add(cls);
            }
        }
        return List.copyOf(out);
    }

    public void recordClass(Class<?> cls) {
        if (cls == null || skipBinary(cls.getName())) {
            return;
        }
        String name = cls.getName();
        CopyOnWriteArrayList<WeakReference<Class<?>>> refs =
                byName.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>());
        refs.removeIf(ref -> ref.get() == null || ref.get() == cls);
        refs.add(new WeakReference<>(cls));
    }

    public void storeBytes(ClassLoader loader, String binaryName, byte[] bytes) {
        if (binaryName == null || bytes == null) {
            return;
        }
        bytesByLoaderAndName.put(key(loader, binaryName), bytes);
    }

    public Optional<byte[]> previousBytes(ClassLoader loader, String binaryName) {
        byte[] exact = bytesByLoaderAndName.get(key(loader, binaryName));
        if (exact != null) {
            return Optional.of(exact);
        }
        for (var entry : bytesByLoaderAndName.entrySet()) {
            if (entry.getKey().endsWith('\0' + binaryName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public byte[] captureBytes(Class<?> cls) {
        if (cls == null) {
            return null;
        }
        Optional<byte[]> cached = previousBytes(cls.getClassLoader(), cls.getName());
        if (cached.isPresent()) {
            return cached.get();
        }
        if (inst.isRetransformClassesSupported() && canRetransform(cls)) {
            try {
                inst.retransformClasses(cls);
            } catch (UnmodifiableClassException | RuntimeException ignored) {
                // fall through to classpath resource
            }
            cached = previousBytes(cls.getClassLoader(), cls.getName());
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        return readFromResource(cls);
    }

    public List<ClassLoader> applicationLoaders() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        for (CopyOnWriteArrayList<WeakReference<Class<?>>> refs : byName.values()) {
            for (WeakReference<Class<?>> ref : refs) {
                Class<?> cls = ref.get();
                if (cls != null && cls.getClassLoader() != null) {
                    loaders.add(cls.getClassLoader());
                }
            }
        }
        return new ArrayList<>(loaders);
    }

    static boolean skipInternal(String internalName) {
        if (internalName == null) {
            return true;
        }
        return internalName.startsWith("java/")
                || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/")
                || internalName.startsWith("sun/")
                || internalName.startsWith("com/sun/")
                || internalName.startsWith("io/runtimerocket/");
    }

    static boolean skipBinary(String binaryName) {
        if (binaryName == null) {
            return true;
        }
        return skipInternal(binaryName.replace('.', '/'));
    }

    private static boolean canRetransform(Class<?> cls) {
        return !cls.isArray() && !cls.isPrimitive() && !cls.isHidden() && !skipBinary(cls.getName());
    }

    private static byte[] readFromResource(Class<?> cls) {
        String resource = cls.getName().replace('.', '/') + ".class";
        ClassLoader loader = cls.getClassLoader();
        try (InputStream in =
                loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String key(ClassLoader loader, String binaryName) {
        return System.identityHashCode(loader) + "\0" + binaryName;
    }

    private final class IndexingTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer)
                throws IllegalClassFormatException {
            if (className == null || skipInternal(className) || classfileBuffer == null) {
                return null;
            }
            String binary = className.replace('/', '.');
            storeBytes(loader, binary, classfileBuffer.clone());
            if (classBeingRedefined != null) {
                recordClass(classBeingRedefined);
            }
            return null;
        }
    }
}
