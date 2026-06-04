package com.wenubey.domain.repository

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.CouponValidationResult
import com.wenubey.domain.model.discount.DiscountCode
import kotlinx.coroutines.flow.Flow

interface DiscountRepository {

    fun observeDiscountCodes(sellerId: String): Flow<List<DiscountCode>>

    suspend fun createDiscountCode(discount: DiscountCode): Result<Unit>

    suspend fun updateDiscountCode(discount: DiscountCode): Result<Unit>

    suspend fun deleteDiscountCode(code: String): Result<Unit>

    suspend fun deactivateDiscountCode(code: String): Result<Unit>

    suspend fun validateCoupon(
        couponCode: String,
        cartItems: List<CartItem>,
        subtotalCents: Int,
    ): Result<CouponValidationResult>

    suspend fun decrementCouponUsage(code: String): Result<Unit>
}
