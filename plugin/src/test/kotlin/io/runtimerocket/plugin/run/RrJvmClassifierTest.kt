package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RrJvmClassifierTest {
    @Test
    fun hybrisTomcatIsRecommendedAndLabeled() {
        val name = "org.apache.catalina.startup.Bootstrap start"
        assertEquals(RrJvmClassifier.Kind.HYBRIS, RrJvmClassifier.classify(name))
        val label = RrJvmClassifier.label("18232", name)
        assertTrue(label.contains("Hybris / Tomcat"), label)
        assertTrue(label.startsWith("18232"), label)
    }

    @Test
    fun wrapperSimpleAppIsHybris() {
        assertEquals(
            RrJvmClassifier.Kind.HYBRIS,
            RrJvmClassifier.classify("org.tanukisoftware.wrapper.WrapperSimpleApp de.hybris.bootstrap.loader.Loader"),
        )
    }

    @Test
    fun ideAndGradleAreNoise() {
        assertEquals(RrJvmClassifier.Kind.IDE, RrJvmClassifier.classify("com.intellij.idea.Main"))
        assertEquals(RrJvmClassifier.Kind.BUILD, RrJvmClassifier.classify("org.gradle.launcher.daemon.bootstrap.GradleDaemon"))
        assertTrue(RrJvmClassifier.isNoise(RrJvmClassifier.Kind.IDE))
        assertTrue(RrJvmClassifier.isNoise(RrJvmClassifier.Kind.BUILD))
    }

    @Test
    fun chooserHidesNoiseAndPutsHybrisFirst() {
        val choices =
            listOf(
                RrJvmClassifier.Choice("1", "com.intellij.idea.Main", RrJvmClassifier.Kind.IDE, "1 — IntelliJ — idea"),
                RrJvmClassifier.Choice("9", "org.gradle.launcher.daemon.bootstrap.GradleDaemon", RrJvmClassifier.Kind.BUILD, "9 — Build tool — gradle"),
                RrJvmClassifier.Choice("4", "org.example.Other", RrJvmClassifier.Kind.OTHER, "4 — JVM — org.example.Other"),
                RrJvmClassifier.Choice("8", "org.apache.catalina.startup.Bootstrap start", RrJvmClassifier.Kind.HYBRIS, "8 — Hybris / Tomcat — Bootstrap"),
            )
        val lines = RrJvmClassifier.chooserLines(choices)
        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("Hybris / Tomcat"), lines.toString())
        assertFalse(lines.any { it.contains("IntelliJ") || it.contains("Gradle") }, lines.toString())
    }

    @Test
    fun chooserFallsBackToNoiseWhenNothingElseExists() {
        val choices =
            listOf(
                RrJvmClassifier.Choice("1", "com.intellij.idea.Main", RrJvmClassifier.Kind.IDE, "1 — IntelliJ — idea"),
            )
        val lines = RrJvmClassifier.chooserLines(choices)
        assertEquals(listOf("1 — IntelliJ — idea"), lines)
    }
}
