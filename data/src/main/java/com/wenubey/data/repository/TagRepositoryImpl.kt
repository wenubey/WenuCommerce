package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.util.TAGS_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.Tag
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.TagRepository
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class TagRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
) : TagRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private val tagsCollection
        get() = firestore.collection(TAGS_COLLECTION)

    override suspend fun resolveOrCreateTag(rawName: String): Result<Tag> =
        safeApiCall(ioDispatcher) {
            val normalised = rawName.trim().lowercase()

            // Query for existing tag by normalised name
            val snapshot = tagsCollection
                .whereEqualTo("name", normalised)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val existing = snapshot.documents.first().toObject(Tag::class.java)
                    ?: throw Exception("Failed to parse existing tag")
                Timber.d("Reusing existing tag: ${existing.id} for name '$normalised'")
                return@safeApiCall existing
            }

            // Create new tag
            val tagId = UUID.randomUUID().toString()
            val createdBy = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val currentTime = System.currentTimeMillis().toString()

            val newTag = Tag(
                id = tagId,
                name = normalised,
                displayName = rawName.trim(),
                createdBy = createdBy,
                createdAt = currentTime,
            )

            tagsCollection.document(tagId).set(newTag.toMap()).await()
            Timber.d("Created new tag: $tagId for name '$normalised'")
            newTag
        }

    override suspend fun searchTagsByPrefix(prefix: String, limit: Long): Result<List<Tag>> =
        safeApiCall(ioDispatcher) {
            val normalised = prefix.trim().lowercase()
            if (normalised.isBlank()) return@safeApiCall emptyList()

            val snapshot = tagsCollection
                .orderBy("name")
                .startAt(normalised)
                .endAt(normalised + "\uF8FF")
                .limit(limit)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Tag::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse tag: ${doc.id}")
                    null
                }
            }
        }

    override suspend fun getTagsByIds(ids: List<String>): Result<List<Tag>> =
        safeApiCall(ioDispatcher) {
            if (ids.isEmpty()) return@safeApiCall emptyList()

            // Firestore `whereIn` supports up to 30 items per query
            ids.chunked(30).flatMap { chunk ->
                val snapshot = tagsCollection
                    .whereIn("id", chunk)
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Tag::class.java)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse tag: ${doc.id}")
                        null
                    }
                }
            }
        }
}
