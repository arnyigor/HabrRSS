package com.arny.habrrss.domain.util

private val HabrArticlePathRegex = Regex("""/(?:articles|post|posts|news)/(\d+)(?:/|$)""")
private val HabrHostRegex = Regex("""^https?://(?:(?:www|m)\.)?habr\.com(?:/|$)""")

internal fun String.extractHabrArticleNumericId(): String? {
    val normalized = replace("&amp;", "&").trim()
    if (!HabrHostRegex.containsMatchIn(normalized)) return null
    return HabrArticlePathRegex.find(normalized)?.groupValues?.getOrNull(1)
}
