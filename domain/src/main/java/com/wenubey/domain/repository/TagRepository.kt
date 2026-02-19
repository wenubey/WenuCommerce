package com.wenubey.domain.repository

import com.wenubey.domain.model.product.Tag

interface TagRepository {
    /**
     * Given a raw tag name string, returns the existing Tag if one exists
     * with the same normalised name, or creates and returns a new Tag document.
     */
    suspend fun resolveOrCreateTag(rawName: String): Result<Tag>
    suspend fun getTagsByIds(ids: List<String>): Result<List<Tag>>

    /**
     * Returns all Tags whose normalized name starts with [prefix].
     * Used for autocomplete. Returns empty list for blank prefix.
     * Limited to [limit] results (default 10).
     */
    suspend fun searchTagsByPrefix(prefix: String, limit: Long = 10L): Result<List<Tag>>
}
