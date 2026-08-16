package io.runtimerocket.plugin.ui

import com.intellij.util.messages.Topic

fun interface RrUiRefresh {
    fun refresh()

    companion object {
        val TOPIC: Topic<RrUiRefresh> = Topic.create("rr-ui-refresh", RrUiRefresh::class.java)
    }
}
