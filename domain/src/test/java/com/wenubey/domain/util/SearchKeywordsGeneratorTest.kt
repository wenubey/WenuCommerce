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

    // --- BUG documentation tests ---
    // These tests pin CURRENT behavior, which is a known issue:
    // the [^a-z0-9] regex strips Turkish characters (ı, ş, ç, ğ, ü, ö),
    // mangling words. Tracked in PRODUCT_BUGS_AND_GAPS.md (Search keyword
    // ASCII-only). If the regex is widened to support Unicode, these
    // tests must be UPDATED in lockstep with the search query side, since
    // the stored index and query tokenization must agree.

    @Test
    fun `bug pin - Turkish characters are stripped, mangling words`() {
        val keywords = generateSearchKeywords(
            title = "Akıllı Kalem",
            categoryName = "Kırtasiye",
            subcategoryName = "",
            tagNames = listOf("Şahin"),
        )
        assertThat(keywords).containsExactly("akll", "kalem", "krtasiye", "ahin")
    }

    @Test
    fun `bug pin - token that becomes single char after stripping is filtered out`() {
        val keywords = generateSearchKeywords(
            title = "ıı şş",
            categoryName = "",
            subcategoryName = "",
            tagNames = emptyList(),
        )
        // "ıı" -> "" (empty), "şş" -> "" (empty); both dropped by length > 1 filter
        assertThat(keywords).isEmpty()
    }
}
