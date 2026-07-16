package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.data.local.entity.SellerOrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [SellerOrderDao] for ViewModel unit tests (no Room, no
 * Firestore). Backs the 07-02 delivered-order eligibility gate: seed rows with
 * [seed] / [upsert], then [getByUserAndStatus] filters by userId + status.
 */
class FakeSellerOrderDao : SellerOrderDao {

    private val rows = MutableStateFlow<List<SellerOrderEntity>>(emptyList())

    fun seed(vararg entities: SellerOrderEntity) {
        rows.value = rows.value + entities.toList()
    }

    override suspend fun upsert(entity: SellerOrderEntity) {
        rows.value = rows.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun upsertAll(entities: List<SellerOrderEntity>) {
        val ids = entities.map { it.id }.toSet()
        rows.value = rows.value.filterNot { it.id in ids } + entities
    }

    override fun observeById(id: String): Flow<SellerOrderEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): SellerOrderEntity? =
        rows.value.firstOrNull { it.id == id }

    override fun observeByParent(parentId: String): Flow<List<SellerOrderEntity>> =
        rows.map { list -> list.filter { it.parentOrderId == parentId } }

    override fun observeBySeller(sellerId: String): Flow<List<SellerOrderEntity>> =
        rows.map { list -> list.filter { it.sellerId == sellerId } }

    override suspend fun getByUserAndStatus(
        userId: String,
        status: String,
    ): List<SellerOrderEntity> =
        rows.value.filter { it.userId == userId && it.status == status }

    override suspend fun updateStatus(
        id: String,
        status: String,
        historyJson: String,
        tracking: String?,
        now: String,
    ) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(
                status = status,
                statusHistoryJson = historyJson,
                trackingNumber = tracking,
                updatedAt = now,
            ) else it
        }
    }
}
