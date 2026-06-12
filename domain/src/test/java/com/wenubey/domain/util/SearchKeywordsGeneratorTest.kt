package com.wenubey.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchKeywordsGeneratorTest {

    @Test
    fun `returns empty list when all inputs are empty`() {
        val keywords = generateSearchKeywords(
            title = "",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).isEmpty()
    }

    @Test
    fun `splits multi-word title into separate keywords`() {
        val keywords = generateSearchKeywords(
            title = "Red Cotton Shirt",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("red", "cotton", "shirt")
    }

    @Test
    fun `lowercases all keywords`() {
        val keywords = generateSearchKeywords(
            title = "RED",
            categoryName = "CLOTHING",
            subcategoryName = "TOPS",
            tagNames = listOf("SUMMER"),
        )
        assertThat(keywords).containsExactly("red", "clothing", "tops", "summer")
    }

    @Test
    fun `merges keywords from title category subcategory and tags`() {
        val keywords = generateSearchKeywords(
            title = "Shirt",
            categoryName = "Clothing",
            subcategoryName = "Tops",
            tagNames = listOf("Cotton", "Summer"),
        )
        assertThat(keywords)
            .containsExactly("shirt", "clothing", "tops", "cotton", "summer")
    }

    @Test
    fun `deduplicates keywords that appear in multiple sources`() {
        val keywords = generateSearchKeywords(
            title = "Cotton Shirt",
            categoryName = "Cotton",
            subcategoryName = "Shirt",
            tagNames = listOf("cotton"),
        )
        assertThat(keywords).containsExactly("cotton", "shirt")
    }

    @Test
    fun `filters out single-character tokens`() {
        val keywords = generateSearchKeywords(
            title = "a b cc",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("cc")
    }

    @Test
    fun `strips punctuation from tokens`() {
        val keywords = generateSearchKeywords(
            title = "Shirt!!! (red), 100% cotton.",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("shirt", "red", "100", "cotton")
    }

    @Test
    fun `handles extra whitespace between words`() {
        val keywords = generateSearchKeywords(
            title = "Red    Cotton\tShirt\n\nSummer",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("red", "cotton", "shirt", "summer")
    }

    @Test
    fun `keeps alphanumeric tokens with digits`() {
        val keywords = generateSearchKeywords(
            title = "iPhone 15 Pro",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("iphone", "15", "pro")
    }

    @Test
    fun `drops empty tokens produced by stripping all-punctuation segments`() {
        val keywords = generateSearchKeywords(
            title = "shirt --- pants",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("shirt", "pants")
    }

    @Test
    fun `tag list with empty strings does not contribute keywords`() {
        val keywords = generateSearchKeywords(
            title = "Shirt",
            categoryName = "",
            subcategoryName = "",
            tagNames = listOf("", "  ", "Cotton"),
        )
        assertThat(keywords).containsExactly("shirt", "cotton")
    }

    @Test
    fun `tag list keywords are split on whitespace just like title`() {
        val keywords = generateSearchKeywords(
            title = "Item",
            categoryName = "",
            subcategoryName = "",
            tagNames = listOf("organic cotton"),
        )
        assertThat(keywords).containsExactly("item", "organic", "cotton")
    }

    // --- Unicode coverage (regression for TB-1) ---
    // The regex is `[^\p{L}\p{N}]`, so Turkish (ı/ş/ç/ğ/ü/ö) and other
    // non-ASCII letters survive. The query side in
    // ProductRepositoryImpl.{searchActiveProducts, searchAllProducts}
    // uses the same regex via the shared SEARCH_KEYWORD_STRIP_REGEX
    // constant — index and query MUST stay in lockstep.

    @Test
    fun `Turkish characters survive tokenization unchanged`() {
        val keywords = generateSearchKeywords(
            title = "Akıllı Kalem",
            categoryName = "Kırtasiye",
            subcategoryName = "",
            tagNames = listOf("Şahin"),
        )
        assertThat(keywords).containsExactly("akıllı", "kalem", "kırtasiye", "şahin")
    }

    @Test
    fun `Turkish two-char words survive the length-greater-than-1 filter`() {
        val keywords = generateSearchKeywords(
            title = "ıı şş",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("ıı", "şş")
    }

    @Test
    fun `punctuation around Turkish words is still stripped`() {
        val keywords = generateSearchKeywords(
            title = "Akıllı, kalem!!! (yeni)",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("akıllı", "kalem", "yeni")
    }

    @Test
    fun `other non-ASCII letters and digits also survive`() {
        val keywords = generateSearchKeywords(
            title = "Café 1990 — naïve",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        assertThat(keywords).containsExactly("café", "1990", "naïve")
    }
}
