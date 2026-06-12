package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.product.Tag
import com.wenubey.domain.repository.TagRepository

class FakeTagRepository : TagRepository {

    val resolveOrCreateCalls = mutableListOf<String>()
    val getByIdsCalls = mutableListOf<List<String>>()
    val searchByPrefixCalls = mutableListOf<Pair<String, Long>>()

    var resolveOrCreateResult: (String) -> Result<Tag> = { rawName ->
        Result.success(Tag(id = "tag-${rawName.lowercase()}", name = rawName.lowercase(), displayName = rawName))
    }
    var getByIdsResult: Result<List<Tag>> = Result.success(emptyList())
    var searchByPrefixResult: Result<List<Tag>> = Result.success(emptyList())

    override suspend fun resolveOrCreateTag(rawName: String): Result<Tag> {
        resolveOrCreateCalls.add(rawName)
        return resolveOrCreateResult(rawName)
    }

    override suspend fun getTagsByIds(ids: List<String>): Result<List<Tag>> {
        getByIdsCalls.add(ids)
        return getByIdsResult
    }

    override suspend fun searchTagsByPrefix(prefix: String, limit: Long): Result<List<Tag>> {
        searchByPrefixCalls.add(prefix to limit)
        return searchByPrefixResult
    }
}
