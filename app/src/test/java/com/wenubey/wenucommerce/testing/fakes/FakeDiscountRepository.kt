package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.CouponValidationResult
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDiscountRepository : DiscountRepository {

    private val codesBySeller = MutableStateFlow<Map<String, List<DiscountCode>>>(emptyMap())

    val createCalls = mutableListOf<DiscountCode>()
    val updateCalls = mutableListOf<DiscountCode>()
    val deleteCalls = mutableListOf<String>()
    val deactivateCalls = mutableListOf<String>()
    val validateCalls = mutableListOf<Triple<String, List<CartItem>, Int>>()
    val decrementCalls = mutableListOf<String>()

    var createResult: Result<Unit> = Result.success(Unit)
    var updateResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var deactivateResult: Result<Unit> = Result.success(Unit)
    var validateResult: Result<CouponValidationResult> = Result.success(
        CouponValidationResult(
            code = "STUB",
            type = DiscountType.PERCENTAGE,
            discountAmountCents = 0,
            description = "",
        )
    )
    var decrementResult: Result<Unit> = Result.success(Unit)

    var observeFlow: Flow<List<DiscountCode>>? = null

    fun emit(sellerId: String, list: List<DiscountCode>) {
        codesBySeller.value = codesBySeller.value.toMutableMap().apply { put(sellerId, list) }
    }

    override fun observeDiscountCodes(sellerId: String): Flow<List<DiscountCode>> {
        observeFlow?.let { return it }
        return kotlinx.coroutines.flow.flow {
            codesBySeller.collect { snapshot ->
                emit(snapshot[sellerId].orEmpty())
            }
        }
    }

    override suspend fun createDiscountCode(discount: DiscountCode): Result<Unit> {
        createCalls.add(discount)
        return createResult
    }

    override suspend fun updateDiscountCode(discount: DiscountCode): Result<Unit> {
        updateCalls.add(discount)
        return updateResult
    }

    override suspend fun deleteDiscountCode(code: String): Result<Unit> {
        deleteCalls.add(code)
        return deleteResult
    }

    override suspend fun deactivateDiscountCode(code: String): Result<Unit> {
        deactivateCalls.add(code)
        return deactivateResult
    }

    override suspend fun validateCoupon(
        couponCode: String,
        cartItems: List<CartItem>,
        subtotalCents: Int,
    ): Result<CouponValidationResult> {
        validateCalls.add(Triple(couponCode, cartItems, subtotalCents))
        return validateResult
    }

    override suspend fun decrementCouponUsage(code: String): Result<Unit> {
        decrementCalls.add(code)
        return decrementResult
    }
}
