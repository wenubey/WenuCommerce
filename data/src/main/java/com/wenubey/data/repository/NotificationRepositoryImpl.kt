package com.wenubey.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.wenubey.data.local.dao.NotificationDao
import com.wenubey.data.local.entity.NotificationEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.Notification
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.NotificationRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Room-first notification-history repository (D-01). Firestore
 * `notifications/{uid}/items` is the server-authoritative source (written only
 * by Cloud Functions, plan 08-02); Room mirrors it and the UI reads Room.
 *
 * - [observeNotifications] : channelFlow that launches a per-user Firestore
 *   snapshot collector writing each snapshot through to the DAO, then emits the
 *   Room rows (offline-first) — clones ProductReviewRepositoryImpl.
 * - [startListener] / [stopListener] : long-lived sync driven off the auth
 *   state (called from AuthRepositoryImpl). Gated on a non-blank userId so the
 *   listener never queries with an empty uid (RESEARCH §Pitfall 4).
 * - [markAsRead] : optimistic Room write, then best-effort Firestore `read`
 *   flip (swallowed on failure — next sync re-authorities, RESEARCH Open Q3).
 */
class NotificationRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val notificationDao: NotificationDao,
    dispatcherProvider: DispatcherProvider,
) : NotificationRepository {

    private val ioDispatcher = dispatcherProvider.io()

    /** Internal scope for the auth-driven sync listener (start/stop). */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Room primary-key -> owning userId of the currently-synced notifications,
     * so [markAsRead] can resolve which Firestore doc to flip. */
    @Volatile
    private var listenerJob: Job? = null

    @Volatile
    private var currentUserId: String? = null

    private fun itemsCollection(userId: String) =
        firestore.collection("notifications")
            .document(userId)
            .collection("items")

    // ── Observe (Room-first + per-user write-through listener) ───────────

    override fun observeNotifications(userId: String): Flow<List<Notification>> =
        channelFlow {
            if (userId.isBlank()) {
                // Never open a listener without a uid — Firestore rules deny it.
                send(emptyList())
                return@channelFlow
            }
            launch(ioDispatcher) {
                notificationSnapshots(userId).collect { entities ->
                    notificationDao.upsertAll(entities)
                }
            }
            notificationDao.observeByUser(userId)
                .map { list -> list.map { it.toDomain() } }
                .collect { send(it) }
        }

    override fun observeUnreadCount(userId: String): Flow<Int> =
        notificationDao.observeUnreadCount(userId)

    private fun notificationSnapshots(userId: String): Flow<List<NotificationEntity>> =
        callbackFlow {
            val listener = itemsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Error observing notifications for user: $userId")
                        return@addSnapshotListener
                    }
                    val entities = snapshot?.documents?.mapNotNull { doc ->
                        doc.toNotificationEntity(userId)
                    } ?: emptyList()
                    trySend(entities)
                }
            awaitClose {
                Timber.d("Removing notifications listener for user: $userId")
                listener.remove()
            }
        }

    // ── Writes (mark-as-read: Room optimistic + best-effort Firestore) ───

    override suspend fun markAsRead(notificationId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            // Optimistic local write first — the UI reflects read state instantly.
            notificationDao.markAsRead(notificationId)
            // Best-effort Firestore mirror so read state survives reinstall /
            // cross-device (swallow failure: next sync re-authorities).
            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                runCatching {
                    itemsCollection(uid).document(notificationId)
                        .update("read", true)
                        .await()
                }.onFailure {
                    Timber.w(it, "Best-effort Firestore mark-as-read failed for $notificationId")
                }
            }
            Unit
        }

    // ── Auth-driven listener lifecycle (called from AuthRepositoryImpl) ──

    override fun startListener(userId: String) {
        if (userId.isBlank()) {
            Timber.w("startListener called with blank userId — ignoring")
            return
        }
        listenerJob?.cancel()
        currentUserId = userId
        listenerJob = scope.launch {
            notificationSnapshots(userId).collect { entities ->
                notificationDao.upsertAll(entities)
            }
        }
    }

    override fun stopListener() {
        listenerJob?.cancel()
        listenerJob = null
        currentUserId = null
    }
}

/**
 * Deserialize a Firestore notifications doc into a [NotificationEntity]. The
 * `read` boolean maps to `isRead`; the `createdAt` Timestamp is stored as an
 * epoch-millis String (consistent with the other entities). The document id is
 * the Room primary key. `userId` is supplied by the caller (the subcollection
 * owner) since it is not stored on the doc.
 */
private fun DocumentSnapshot.toNotificationEntity(userId: String): NotificationEntity? =
    try {
        val createdAtMillis = when (val raw = get("createdAt")) {
            is Timestamp -> raw.toDate().time.toString()
            is Number -> raw.toLong().toString()
            is String -> raw
            else -> ""
        }
        NotificationEntity(
            id = getString("id") ?: id,
            userId = userId,
            type = getString("type") ?: "",
            title = getString("title") ?: "",
            body = getString("body") ?: "",
            orderId = getString("orderId") ?: "",
            sellerOrderId = getString("sellerOrderId") ?: "",
            productId = getString("productId") ?: "",
            productTitle = getString("productTitle") ?: "",
            isRead = getBoolean("read") ?: false,
            createdAt = createdAtMillis,
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to deserialize notification doc: $id")
        null
    }
