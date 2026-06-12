package com.wenubey.domain.util

/**
 * Tokenizes the input strings into a deduplicated list of search keywords.
 *
 * The tokenizer keeps all Unicode letters and digits (Turkish ı/ş/ç/ğ/ü/ö,
 * accented characters, Arabic/CJK/etc.) instead of stripping anything outside
 * `[a-z0-9]`. The query-side reader in `ProductRepositoryImpl.searchActive
 * Products` / `searchAllProducts` uses the same regex so the stored index
 * and the query tokenization agree exactly.
 */
fun generateSearchKeywords(
    title: String,
    categoryName: String,
    subcategoryName: String,
    tagNames: List<String>,
): List<String> {
    val sources = listOf(title, categoryName, subcategoryName) + tagNames
    return sources
        .flatMap { it.lowercase().trim().split(Regex("\\s+")) }
        .map { it.replace(SEARCH_KEYWORD_STRIP_REGEX, "").trim() }
        .filter { it.length > 1 }
        .distinct()
}

/** Strips characters that are neither Unicode letters nor digits. */
val SEARCH_KEYWORD_STRIP_REGEX = Regex("[^\\p{L}\\p{N}]")
