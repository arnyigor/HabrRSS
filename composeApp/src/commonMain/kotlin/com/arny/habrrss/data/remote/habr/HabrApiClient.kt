package com.arny.habrrss.data.remote.habr

import com.arny.habrrss.data.remote.habr.dto.HabrArticleDto
import com.arny.habrrss.data.remote.habr.dto.HabrArticlesPageDto
import com.arny.habrrss.data.remote.habr.dto.HabrErrorDto
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
import com.arny.habrrss.data.remote.habr.error.toHabrException
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class HabrApiClient(
    private val client: HttpClient,
    private val json: Json = habrJson,
) {
    suspend fun getArticles(request: HabrArticlesRequest): HabrArticlesPageDto {
        val response = client.get(HabrApiConstants.BaseUrl + "articles/") {
            applyHabrDefaults()
            applyRequest(request)
        }

        return response.decodeOrThrow()
    }

    suspend fun getArticle(articleId: Long): HabrArticleDto {
        val response = client.get(HabrApiConstants.BaseUrl + "articles/$articleId") {
            applyHabrDefaults()
            parameter("fl", HabrApiConstants.DefaultLanguage)
            parameter("hl", HabrApiConstants.DefaultLanguage)
        }

        return response.decodeOrThrow()
    }

    suspend fun getHubsRaw(page: Int): JsonObject {
        val response = client.get(HabrApiConstants.BaseUrl + "hubs/") {
            applyHabrDefaults()
            parameter("fl", HabrApiConstants.DefaultLanguage)
            parameter("hl", HabrApiConstants.DefaultLanguage)
            parameter("page", page)
        }

        return response.decodeOrThrow()
    }

    suspend fun getCompaniesRaw(page: Int): JsonObject {
        val response = client.get(HabrApiConstants.BaseUrl + "companies/") {
            applyHabrDefaults()
            parameter("fl", HabrApiConstants.DefaultLanguage)
            parameter("hl", HabrApiConstants.DefaultLanguage)
            parameter("page", page)
        }

        return response.decodeOrThrow()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHabrDefaults() {
        accept(ContentType.Application.Json)
        header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9,en;q=0.7")
        header(HttpHeaders.UserAgent, HabrApiConstants.UserAgent)
    }

    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
        val raw = bodyAsText()

        if (status.isSuccess()) {
            try {
                return json.decodeFromString(raw)
            } catch (error: SerializationException) {
                throw HabrRemoteException.ContractChanged(
                    status = status.value,
                    responseSample = raw.take(CONTRACT_SAMPLE_LIMIT),
                    cause = error,
                )
            }
        }

        val errorDto = runCatching {
            json.decodeFromString<HabrErrorDto>(raw)
        }.getOrNull()

        throw status.toHabrException(
            errorDto = errorDto,
            responseSample = raw.take(ERROR_SAMPLE_LIMIT),
            retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
        )
    }

    private companion object {
        const val CONTRACT_SAMPLE_LIMIT = 4_000
        const val ERROR_SAMPLE_LIMIT = 2_000
    }
}
