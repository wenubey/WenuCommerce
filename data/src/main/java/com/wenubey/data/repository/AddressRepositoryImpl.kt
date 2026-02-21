package com.wenubey.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.AddressDao
import com.wenubey.data.local.entity.AddressEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.AddressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class AddressRepositoryImpl(
    private val addressDao: AddressDao,
    private val firestore: FirebaseFirestore,
) : AddressRepository {

    // Coroutine scope for the Firestore snapshot listener writes to Room
    private val repositoryScope = CoroutineScope(SupervisorJob())

    // Tracks active Firestore listeners per userId so we don't duplicate them
    private val activeListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    override fun observeSavedAddresses(userId: String): Flow<List<ShippingAddress>> {
        startFirestoreSync(userId)
        return addressDao.observeByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Sets up a Firestore snapshot listener for the user's addresses.
     * The listener writes through to Room so that [observeSavedAddresses] emits
     * fresh data automatically (Room-first pattern).
     *
     * Only one listener per userId is registered at a time.
     */
    private fun startFirestoreSync(userId: String) {
        if (userId.isEmpty() || activeListeners.containsKey(userId)) return

        val registration = firestore
            .collection("users")
            .document(userId)
            .collection("addresses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "AddressRepository: Firestore snapshot error for user $userId")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repositoryScope.launch {
                    try {
                        val entities = snapshot.documents.mapNotNull { doc ->
                            try {
                                AddressEntity(
                                    userId = userId,
                                    addressId = doc.id,
                                    fullName = doc.getString("fullName") ?: "",
                                    line1 = doc.getString("line1") ?: "",
                                    line2 = doc.getString("line2") ?: "",
                                    city = doc.getString("city") ?: "",
                                    state = doc.getString("state") ?: "",
                                    postalCode = doc.getString("postalCode") ?: "",
                                    country = doc.getString("country") ?: ""
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "AddressRepository: failed to parse address doc ${doc.id}")
                                null
                            }
                        }
                        addressDao.deleteAllForUser(userId)
                        addressDao.upsertAll(entities)
                    } catch (e: Exception) {
                        Timber.e(e, "AddressRepository: failed to write Firestore snapshot to Room for user $userId")
                    }
                }
            }

        activeListeners[userId] = registration
    }

    override suspend fun saveAddress(userId: String, address: ShippingAddress) {
        val addressId = address.id.ifEmpty { UUID.randomUUID().toString() }
        val addressWithId = address.copy(id = addressId)

        // Write to Firestore
        try {
            firestore.collection("users")
                .document(userId)
                .collection("addresses")
                .document(addressId)
                .set(addressWithId.toMap())
                .await()
        } catch (e: Exception) {
            Timber.e(e, "AddressRepository: failed to save address to Firestore for user $userId")
        }

        // Write to Room immediately (ensures instant local availability)
        addressDao.upsert(addressWithId.toEntity(userId))
    }

    override suspend fun deleteAddress(userId: String, addressId: String) {
        // Delete from Firestore
        try {
            firestore.collection("users")
                .document(userId)
                .collection("addresses")
                .document(addressId)
                .delete()
                .await()
        } catch (e: Exception) {
            Timber.e(e, "AddressRepository: failed to delete address from Firestore for user $userId")
        }

        // Delete from Room
        addressDao.delete(userId, addressId)
    }
}
