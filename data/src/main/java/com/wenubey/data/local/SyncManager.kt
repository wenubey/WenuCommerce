package com.wenubey.data.local

import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.data.util.CATEGORIES_COLLECTION
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

sealed interface SyncEvent {
    data class SyncFailed(val message: String) : SyncEvent
}

class SyncManager(
    private val firestore: FirebaseFirestore,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val syncScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io())

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    fun startSync() {
        syncScope.launch {
            callbackFlow {
                val listener = firestore.collection(PRODUCTS_COLLECTION)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Product sync error")
                            return@addSnapshotListener
                        }
                        val products = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                doc.toObject(Product::class.java)?.toEntity()
                            } catch (e: Exception) {
                                Timber.e(e, "Product sync deserialize failed: ${doc.id}")
                                null
                            }
                        } ?: emptyList()
                        trySend(products)
                    }
                awaitClose { listener.remove() }
            }.catch { e ->
                Timber.e(e, "Product sync flow failed")
                _syncEvents.tryEmit(SyncEvent.SyncFailed("Sync failed — showing cached data"))
            }.collect { entities -> productDao.upsertAll(entities) }
        }

        syncScope.launch {
            callbackFlow {
                val listener = firestore.collection(CATEGORIES_COLLECTION)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Category sync error")
                            return@addSnapshotListener
                        }
                        val categories = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                doc.toObject(Category::class.java)?.toEntity()
                            } catch (e: Exception) {
                                Timber.e(e, "Category sync deserialize failed: ${doc.id}")
                                null
                            }
                        } ?: emptyList()
                        trySend(categories)
                    }
                awaitClose { listener.remove() }
            }.catch { e ->
                Timber.e(e, "Category sync flow failed")
                _syncEvents.tryEmit(SyncEvent.SyncFailed("Sync failed — showing cached data"))
            }.collect { entities -> categoryDao.upsertAll(entities) }
        }
    }

    suspend fun manualSync() {
        try {
            val productsSnapshot = firestore.collection(PRODUCTS_COLLECTION).get().await()
            val products = productsSnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Product::class.java)?.toEntity()
                } catch (e: Exception) {
                    Timber.e(e, "Manual sync product deserialize failed: ${doc.id}")
                    null
                }
            }
            productDao.upsertAll(products)

            val categoriesSnapshot = firestore.collection(CATEGORIES_COLLECTION).get().await()
            val categories = categoriesSnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Category::class.java)?.toEntity()
                } catch (e: Exception) {
                    Timber.e(e, "Manual sync category deserialize failed: ${doc.id}")
                    null
                }
            }
            categoryDao.upsertAll(categories)
        } catch (e: Exception) {
            Timber.e(e, "Manual sync failed")
            _syncEvents.tryEmit(SyncEvent.SyncFailed("Sync failed — showing cached data"))
        }
    }
}
