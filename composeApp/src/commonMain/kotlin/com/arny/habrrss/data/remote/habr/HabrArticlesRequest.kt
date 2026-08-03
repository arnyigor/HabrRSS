package com.arny.habrrss.data.remote.habr

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter

sealed interface HabrArticlesRequest {
    val page: Int
    val perPage: Int

    data class Latest(
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
        val period: HabrPeriod = HabrPeriod.Weekly,
    ) : HabrArticlesRequest

    data class Hub(
        val alias: String,
        val period: HabrPeriod,
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
    ) : HabrArticlesRequest

    data class Company(
        val alias: String,
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
    ) : HabrArticlesRequest

    data class Author(
        val alias: String,
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
    ) : HabrArticlesRequest

    data class Flow(
        val alias: String,
        val includeNews: Boolean,
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
    ) : HabrArticlesRequest

    data class Search(
        val query: String,
        val order: HabrSearchOrder,
        override val page: Int = 1,
        override val perPage: Int = HabrApiConstants.DefaultPerPage,
    ) : HabrArticlesRequest
}

enum class HabrPeriod(val wireValue: String) {
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly"),
    AllTime("alltime"),
}

enum class HabrSearchOrder(val wireValue: String) {
    Date("date"),
    Relevance("relevance"),
}

internal fun HttpRequestBuilder.applyRequest(request: HabrArticlesRequest) {
    parameter("fl", HabrApiConstants.DefaultLanguage)
    parameter("hl", HabrApiConstants.DefaultLanguage)
    parameter("page", request.page)
    parameter("perPage", request.perPage)

    when (request) {
        is HabrArticlesRequest.Latest -> {
            parameter("sort", "date")
            parameter("period", request.period.wireValue)
        }

        is HabrArticlesRequest.Hub -> {
            parameter("hub", request.alias)
            parameter("sort", "date")
            parameter("period", request.period.wireValue)
        }

        is HabrArticlesRequest.Company -> {
            parameter("company", request.alias)
        }

        is HabrArticlesRequest.Author -> {
            parameter("user", request.alias)
            parameter("order", "date")
        }

        is HabrArticlesRequest.Flow -> {
            parameter("flow", request.alias)
            parameter("flowNews", request.includeNews)
        }

        is HabrArticlesRequest.Search -> {
            parameter("query", request.query)
            parameter("order", request.order.wireValue)
        }
    }
}
