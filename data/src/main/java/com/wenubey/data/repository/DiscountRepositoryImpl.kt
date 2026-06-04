package com.wenubey.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.CouponValidationResult
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.repository.DiscountRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

class DiscountRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: DispatcherProvider,
) : DiscountRepository {

    private val discountCodesCollection = firestore.collection("discountCodes")

    override fun observeDiscountCodes(sellerId: String): Flow<List<DiscountCode>> =
        callbackFlow {
            val query = if (sellerId.isNotEmpty()) {
                discountCodesCollection.whereEqualTo("sellerId", sellerId)
            } else {
                discountCodesCollection
            }

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing discount codes")
                    close(error)
                    return@addSnapshotListener
                }

                val codes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        mapDocumentToDiscountCode(doc.id, doc.data)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to deserialize discount code: ${doc.id}")
                        null
                    }
                } ?: emptyList()

                trySend(codes)
            }

            awaitClose { listener.remove() }
        }

    override suspend fun createDiscountCode(discount: DiscountCode): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                val normalizedCode = discount.code.trim().uppercase()
                val now = Instant.now().toString()
                val data = mapOf(
                    "code" to normalizedCode,
                    "type" to discount.type.name,
                    "value" to discount.value,
                    "maxDiscountCap" to discount.maxDiscountCap,
                    "minimumOrderAmount" to discount.minimumOrderAmount,
                    "targetProductIds" to discount.targetProductIds,
                    "sellerId" to discount.sellerId,
                    "expiresAt" to discount.expiresAt,
                    "usageLimit" to discount.usageLimit,
                    "usageCount" to 0,
                    "isActive" to true,
                    "createdAt" to now,
                    "updatedAt" to now,
                )
                discountCodesCollection.document(normalizedCode).set(data).await()
                Unit
            }.onFailure { e ->
                Timber.e(e, "DiscountRepository: createDiscountCode failed")
            }
        }

    override suspend fun updateDiscountCode(discount: DiscountCode): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                val now = Instant.now().toString()
                val data = mapOf(
                    "type" to discount.type.name,
                    "value" to discount.value,
                    "maxDiscountCap" to discount.maxDiscountCap,
                    "minimumOrderAmount" to discount.minimumOrderAmount,
                    "targetProductIds" to discount.targetProductIds,
                    "expiresAt" to discount.expiresAt,
                    "usageLimit" to discount.usageLimit,
                    "isActive" to discount.isActive,
                    "updatedAt" to now,
                )
                discountCodesCollection.document(discount.code.uppercase()).update(data).await()
                Unit
            }.onFailure { e ->
                Timber.e(e, "DiscountRepository: updateDiscountCode failed")
            }
        }

    override suspend fun deleteDiscountCode(code: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                discountCodesCollection.document(code.uppercase()).delete().await()
                Unit
            }.onFailure { e ->
                Timber.e(e, "DiscountRepository: deleteDiscountCode failed")
            }
        }

    override suspend fun deactivateDiscountCode(code: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                val now = Instant.now().toString()
                discountCodesCollection.document(code.uppercase()).update(
                    mapOf(
                        "isActive" to false,
                        "updatedAt" to now,
                    )
                ).await()
                Unit
            }.onFailure { e ->
                Timber.e(e, "DiscountRepository: deactivateDiscountCode failed")
            }
        }

    override suspend fun validateCoupon(
        couponCode: String,
        cartItems: List<CartItem>,
        subtotalCents: Int,
    ): Result<CouponValidationResult> = withContext(dispatcherProvider.io()) {
        runCatching {
            val data = mapOf(
                "couponCode" to couponCode,
                "cartItems" to cartItems.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "productTitle" to item.productTitle,
                        "quantity" to item.quantity,
                        "price" to item.snapshotPrice,
                    )
                },
                "subtotalCents" to subtotalCents,
            )

            val result = Firebase.functions
                .getHttpsCallable("validateCoupon")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val responseData = result.getData() as? Map<String, Any>
                ?: error("Invalid validateCoupon response")

            CouponValidationResult(
                code = responseData["code"] as String,
                type = DiscountType.valueOf(responseData["type"] as String),
                discountAmountCents = (responseData["discountCents"] as Number).toInt(),
                description = responseData["description"] as String,
            )
        }.recoverCatching { e ->
            throw mapCouponException(e)
        }
    }

    override suspend fun decrementCouponUsage(code: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                Firebase.functions
                    .getHttpsCallable("decrementCouponUsage")
                    .call(mapOf("couponCode" to code))
                    .await()
                Unit
            }.onFailure { e ->
                Timber.e(e, "DiscountRepository: decrementCouponUsage failed")
            }
        }

    private fun mapDocumentToDiscountCode(
        docId: String,
        data: Map<String, Any>?,
    ): DiscountCode? {
        data ?: return null
        return DiscountCode(
            code = docId,
            type = runCatching {
                DiscountType.valueOf(data["type"] as? String ?: "PERCENTAGE")
            }.getOrElse { DiscountType.PERCENTAGE },
            value = (data["value"] as? Number)?.toDouble() ?: 0.0,
            maxDiscountCap = (data["maxDiscountCap"] as? Number)?.toDouble(),
            minimumOrderAmount = (data["minimumOrderAmount"] as? Number)?.toDouble(),
            targetProductIds = @Suppress("UNCHECKED_CAST")
            (data["targetProductIds"] as? List<String>) ?: emptyList(),
            sellerId = data["sellerId"] as? String ?: "",
            expiresAt = data["expiresAt"]?.toString(),
            usageLimit = (data["usageLimit"] as? Number)?.toInt(),
            usageCount = (data["usageCount"] as? Number)?.toInt() ?: 0,
            isActive = data["isActive"] as? Boolean ?: true,
            createdAt = data["createdAt"]?.toString() ?: "",
            updatedAt = data["updatedAt"]?.toString() ?: "",
        )
    }

    private fun mapCouponException(exception: Throwable): Throwable {
        val message = exception.message ?: return IllegalStateException("Invalid coupon code")
        val userMessage = when {
            message.contains("Code not found") || message.contains("not-found") ->
                "Code not found"
            message.contains("expired") ->
                "This code has expired"
            message.contains("Usage limit") ->
                "Usage limit reached"
            message.contains("No eligible items") ->
                "No eligible items in your cart"
            message.contains("Minimum order") ->
                message.substringAfter("Minimum order").let { "Minimum order$it" }
            else -> "Invalid coupon code"
        }
        return IllegalStateException(userMessage)
    }
}
