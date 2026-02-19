package com.wenubey.domain.util

fun generateSearchKeywords(
    title: String,
    categoryName: String,
    subcategoryName: String,
    tagNames: List<String>,
): List<String> {
    val sources = listOf(title, categoryName, subcategoryName) + tagNames
    return sources
        .flatMap { it.lowercase().trim().split(Regex("\\s+")) }
        .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
        .filter { it.length > 1 }
        .distinct()
}
