package com.arny.habrrss.data.remote.habr.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HabrArticlesPageDto(
    val pagesCount: Int = 0,
    val publicationIds: List<String> = emptyList(),
    val publicationRefs: Map<String, HabrArticleDto> = emptyMap(),
    val allowedFeatures: JsonObject? = null,
)

@Serializable
data class HabrArticleDto(
    val id: String,
    val timePublished: String? = null,
    val isCorporative: Boolean? = null,
    val lang: String? = null,
    val titleHtml: String? = null,
    val leadData: HabrLeadDataDto? = null,
    val editorVersion: String? = null,
    val postType: String? = null,
    val author: HabrAuthorDto? = null,
    val statistics: HabrStatisticsDto? = null,
    val hubs: List<HabrHubDto> = emptyList(),
    val flows: List<HabrFlowDto> = emptyList(),
    val readingTime: Int? = null,
    val complexity: String? = null,
    val textHtml: String? = null,
)

@Serializable
data class HabrLeadDataDto(
    val textHtml: String? = null,
    val imageUrl: String? = null,
    val buttonTextHtml: String? = null,
    val image: HabrImageDto? = null,
)

@Serializable
data class HabrImageDto(
    val url: String? = null,
    val fit: String? = null,
    val positionX: Int? = null,
    val positionY: Int? = null,
)

@Serializable
data class HabrAuthorDto(
    val id: String? = null,
    val alias: String? = null,
    val fullname: String? = null,
    val avatarUrl: String? = null,
    val speciality: String? = null,
    val deleted: Boolean? = null,
)

@Serializable
data class HabrStatisticsDto(
    val commentsCount: Long? = null,
    val favoritesCount: Long? = null,
    val readingCount: Long? = null,
    val score: Int? = null,
    val votesCount: Long? = null,
    val votesCountPlus: Long? = null,
    val votesCountMinus: Long? = null,
)

@Serializable
data class HabrHubDto(
    val id: String? = null,
    val alias: String? = null,
    val type: String? = null,
    val title: String? = null,
    val titleHtml: String? = null,
    val isProfiled: Boolean? = null,
)

@Serializable
data class HabrFlowDto(
    val id: String? = null,
    val alias: String? = null,
    val title: String? = null,
    val titleHtml: String? = null,
)

@Serializable
data class HabrErrorDto(
    val httpCode: Int? = null,
    val message: String? = null,
    val errorCode: String? = null,
    val data: JsonElement? = null,
)
