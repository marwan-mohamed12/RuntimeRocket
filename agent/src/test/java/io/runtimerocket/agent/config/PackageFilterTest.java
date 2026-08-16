package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageFilterTest {

    @Test
    void emptyIncludesAcceptsEverythingExceptExcludes() {
        PackageFilter filter = PackageFilter.of(List.of(), List.of("com.example.generated.**"));
        assertTrue(filter.accepts("com.example.Foo"));
        assertFalse(filter.accepts("com.example.generated.Bar"));
        assertFalse(filter.accepts("com.example.generated.sub.Baz"));
    }

    @Test
    void doubleStarMatchesNestedAndExactPackage() {
        PackageFilter filter = PackageFilter.of(List.of("com.example.**"), List.of());
        assertTrue(filter.accepts("com.example"));
        assertTrue(filter.accepts("com.example.Foo"));
        assertTrue(filter.accepts("com.example.sub.Bar"));
        assertFalse(filter.accepts("org.other.Foo"));
    }

    @Test
    void singleStarMatchesOneSegmentOnly() {
        PackageFilter filter = PackageFilter.of(List.of("com.example.*"), List.of());
        assertTrue(filter.accepts("com.example.Foo"));
        assertFalse(filter.accepts("com.example.sub.Foo"));
        assertFalse(filter.accepts("com.example"));
    }

    @Test
    void excludeWinsOverInclude() {
        PackageFilter filter = PackageFilter.of(List.of("com.example.**"), List.of("com.example.generated.**"));
        assertTrue(filter.accepts("com.example.Foo"));
        assertFalse(filter.accepts("com.example.generated.X"));
    }

    @Test
    void unionAcceptsIfAnyDocumentAccepts() {
        PackageFilter lib = PackageFilter.of(List.of("demo.twomodule.lib.**"), List.of());
        PackageFilter app = PackageFilter.of(List.of("demo.twomodule.app.**"), List.of());
        PackageFilter union = PackageFilter.union(List.of(lib, app));
        assertTrue(union.accepts("demo.twomodule.lib.LibGreeter"));
        assertTrue(union.accepts("demo.twomodule.app.App"));
        assertFalse(union.accepts("demo.twomodule.generated.X"));
    }
}
