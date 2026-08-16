package io.runtimerocket.agent.config;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.AgentStartException;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds {@code runtimerocket.xml} from {@code config=}, the application classpath, extra {@code
 * watchDir} roots, and {@code URLClassLoader} file URLs. Multiple files are unioned.
 */
public final class RocketXmlDiscovery {

    private RocketXmlDiscovery() {}

    public static RocketXmlDocuments discover(AgentOptions options) {
        return discover(options, null, null);
    }

    public static RocketXmlDocuments discover(AgentOptions options, Instrumentation inst, AgentLog log) {
        Map<String, RocketXml> byKey = new LinkedHashMap<>();
        if (options != null && options.config != null) {
            Path config = options.config.toAbsolutePath().normalize();
            try {
                put(byKey, RocketXmlParser.parse(config));
            } catch (RuntimeException e) {
                throw new AgentStartException("cannot read config " + config, e);
            }
        }
        if (options != null) {
            for (Path dir : options.watchDirs) {
                if (dir == null) {
                    continue;
                }
                Path xml = dir.resolve(RocketXmlParser.FILE_NAME);
                if (Files.isRegularFile(xml)) {
                    addOptional(byKey, xml, log);
                }
            }
        }
        for (ClassLoader loader : loaders(inst)) {
            collectFromLoader(loader, byKey, log);
        }
        return new RocketXmlDocuments(new ArrayList<>(byKey.values()));
    }

    public static RocketXmlDocuments fromFiles(List<Path> files) {
        List<RocketXml> docs = new ArrayList<>();
        if (files != null) {
            for (Path file : files) {
                if (file != null) {
                    docs.add(RocketXmlParser.parse(file));
                }
            }
        }
        return new RocketXmlDocuments(docs);
    }

    private static Set<ClassLoader> loaders(Instrumentation inst) {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(RocketXmlDiscovery.class.getClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());
        if (inst != null) {
            for (Class<?> cls : inst.getAllLoadedClasses()) {
                ClassLoader loader = cls.getClassLoader();
                if (loader instanceof URLClassLoader) {
                    loaders.add(loader);
                }
            }
        }
        return loaders;
    }

    private static void collectFromLoader(ClassLoader loader, Map<String, RocketXml> byKey, AgentLog log) {
        if (loader == null) {
            return;
        }
        try {
            Enumeration<URL> urls = loader.getResources(RocketXmlParser.FILE_NAME);
            while (urls.hasMoreElements()) {
                addUrl(byKey, urls.nextElement(), log);
            }
        } catch (Exception ignored) {
            // a broken loader must not abort discovery
        }
        if (loader instanceof URLClassLoader urlLoader) {
            for (URL url : urlLoader.getURLs()) {
                Path root = filePath(url);
                if (root == null || !Files.isDirectory(root)) {
                    continue;
                }
                Path xml = root.resolve(RocketXmlParser.FILE_NAME);
                if (Files.isRegularFile(xml)) {
                    addOptional(byKey, xml, log);
                }
            }
        }
        collectFromLoader(loader.getParent(), byKey, log);
    }

    private static void addUrl(Map<String, RocketXml> byKey, URL url, AgentLog log) {
        if (url == null) {
            return;
        }
        Path file = filePath(url);
        if (file != null && Files.isRegularFile(file)) {
            addOptional(byKey, file, log);
            return;
        }
        try (InputStream in = url.openStream()) {
            RocketXml xml = RocketXmlParser.parse(in, Path.of(RocketXmlParser.FILE_NAME), RocketXmlParser.userDir());
            byKey.putIfAbsent(url.toExternalForm(), xml);
        } catch (Exception e) {
            if (log != null) {
                log.warn("skipping " + url + ": " + e.getMessage());
            }
        }
    }

    private static void addOptional(Map<String, RocketXml> byKey, Path file, AgentLog log) {
        try {
            put(byKey, RocketXmlParser.parse(file));
        } catch (RuntimeException e) {
            if (log != null) {
                log.warn("skipping " + file + ": " + e.getMessage());
            }
        }
    }

    private static void put(Map<String, RocketXml> byKey, RocketXml xml) {
        String key = xml.source == null ? String.valueOf(System.identityHashCode(xml)) : canonKey(xml.source);
        byKey.putIfAbsent(key, xml);
    }

    private static String canonKey(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (Exception e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    static Path filePath(URL url) {
        if (url == null || url.getProtocol() == null || !"file".equalsIgnoreCase(url.getProtocol())) {
            return null;
        }
        try {
            return Path.of(url.toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            try {
                return Path.of(URI.create(url.toExternalForm())).toAbsolutePath().normalize();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
