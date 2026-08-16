package io.runtimerocket.plugin.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object RrIcons {
    val Rocket: Icon = IconLoader.getIcon("/icons/rr.svg", RrIcons::class.java)
    val Gutter: Icon = IconLoader.getIcon("/icons/rrGutter.svg", RrIcons::class.java)
}
