package io.runtimerocket.agent.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Union of every loaded {@code runtimerocket.xml}. Classpath dirs, resource dirs, and package
 * filters are merged: a class is accepted when any document accepts it.
 */
public final class RocketXmlDocuments {

    public static final RocketXmlDocuments EMPTY = new RocketXmlDocuments(List.of());

    private final List<RocketXml> documents;

    public RocketXmlDocuments(List<RocketXml> documents) {
        this.documents = List.copyOf(documents == null ? List.of() : documents);
    }

    public static RocketXmlDocuments of(RocketXml... documents) {
        if (documents == null || documents.length == 0) {
            return EMPTY;
        }
        List<RocketXml> list = new ArrayList<>();
        for (RocketXml xml : documents) {
            if (xml != null) {
                list.add(xml);
            }
        }
        return new RocketXmlDocuments(list);
    }

    public List<RocketXml> documents() {
        return documents;
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    public List<Path> classpathDirs() {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        for (RocketXml xml : documents) {
            out.addAll(xml.classpathDirs);
        }
        return List.copyOf(out);
    }

    public List<Path> resourceDirs() {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        for (RocketXml xml : documents) {
            out.addAll(xml.resourceDirs);
        }
        return List.copyOf(out);
    }

    public List<Path> allDirs() {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        out.addAll(classpathDirs());
        out.addAll(resourceDirs());
        return List.copyOf(out);
    }

    public PackageFilter packageFilter() {
        if (documents.isEmpty()) {
            return PackageFilter.allowAll();
        }
        List<PackageFilter> filters = new ArrayList<>(documents.size());
        for (RocketXml xml : documents) {
            filters.add(xml.packageFilter());
        }
        return PackageFilter.union(filters);
    }
}
