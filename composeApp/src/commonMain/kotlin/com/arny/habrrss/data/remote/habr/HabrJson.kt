package com.arny.habrrss.data.remote.habr

import kotlinx.serialization.json.Json

internal val habrJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = false
}
