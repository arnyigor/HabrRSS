package com.arny.habrrss.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.coroutines.CancellationException

fun createHttpClient(enableLogging: Boolean = false): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response ->
                response.status.value == 429 || response.status.value in 500..599
            }
            retryOnExceptionIf { _, cause ->
                cause !is CancellationException
            }
            exponentialDelay()
        }
        install(HttpCache)
        defaultRequest {
            headers {
                append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 ArnyHabrReader/0.1",
                )
            }
        }
        if (enableLogging) {
            install(Logging) {
                level = LogLevel.HEADERS
            }
        }
    }
}