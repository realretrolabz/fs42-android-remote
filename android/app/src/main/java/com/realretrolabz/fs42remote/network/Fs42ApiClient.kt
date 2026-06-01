package com.realretrolabz.fs42remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class Fs42ApiClient {
    suspend fun getPlayerStatus(host: String, port: Int): Result<String> {
        return request(host = host, port = port, method = "GET", path = "/player/status")
    }

    suspend fun get(host: String, port: Int, path: String): Result<String> {
        return request(host = host, port = port, method = "GET", path = path)
    }

    suspend fun post(host: String, port: Int, path: String): Result<String> {
        return request(host = host, port = port, method = "POST", path = path)
    }

    private suspend fun request(
        host: String,
        port: Int,
        method: String,
        path: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            val url = URL("http://$host:$port$normalizedPath")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = method
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
                    responseBody.ifBlank { "HTTP $responseCode" }
                } else {
                    throw IllegalStateException("HTTP $responseCode ${responseBody.take(240)}".trim())
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
