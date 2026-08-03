package com.arny.habrrss.data.remote.habr

import kotlinx.serialization.json.JsonObject

class HabrDirectorySource(
    private val api: HabrApiClient,
) {
    suspend fun getHubsPageRaw(page: Int = 1): DirectoryPageRaw =
        api.getHubsRaw(page.coerceAtLeast(1)).toDirectoryPageRaw()

    suspend fun getCompaniesPageRaw(page: Int = 1): DirectoryPageRaw =
        api.getCompaniesRaw(page.coerceAtLeast(1)).toDirectoryPageRaw()

    private fun JsonObject.toDirectoryPageRaw(): DirectoryPageRaw = DirectoryPageRaw(
        rootKeys = keys.sorted(),
        payload = this,
    )
}

data class DirectoryPageRaw(
    val rootKeys: List<String>,
    val payload: JsonObject,
)
