package com.realretrolabz.fs42remote

import org.json.JSONObject

private val ChannelNumberPattern = """"channel_number"\s*:\s*(\d+)""".toRegex()

fun String.extractChannelNumber(): Int? {
    return ChannelNumberPattern.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

fun String.extractVfdText(): String? {
    return runCatching {
        JSONObject(this).optString("vfd_text").trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}

fun String.toNowPlayingDisplayText(): String {
    return runCatching {
        val json = JSONObject(this)
        val channel = json.optInt("channel_number", -1).takeIf { it >= 0 }
        val network = json.optString("network_name").trim()
        val title = json.optString("title").trim()
        buildList {
            if (channel != null || network.isNotBlank()) {
                add(listOfNotNull(channel?.let { "CH $it" }, network.ifBlank { null }).joinToString(" "))
            }
            add(title.ifBlank { json.optString("status").ifBlank { "NOW PLAYING" } })
        }.joinToString("\n").uppercase()
    }.getOrDefault("NOW PLAYING")
}
