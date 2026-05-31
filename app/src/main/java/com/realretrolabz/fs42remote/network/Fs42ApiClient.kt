package com.realretrolabz.fs42remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class Fs42ApiClient {
    suspend fun getPlayerStatus(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://$host:$port/player/status")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000

            try {
                val responseCode = connection.responseCode
                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }

                if (responseCode in 200..299) {
                    responseBody.ifBlank { "Connected: HTTP $responseCode" }
                } else {
                    "HTTP $responseCode ${responseBody.take(240)}".trim()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
