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

fun createHttpClient(enableLogging: Boolean = false): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay()
        }
        install(HttpCache)
        defaultRequest {
            headers {
                append(HttpHeaders.UserAgent, "TechReader/1.0 KMP RSS Reader")
            }
        }
        if (enableLogging) {
            install(Logging) {
                level = LogLevel.HEADERS
            }
        }
    }
}