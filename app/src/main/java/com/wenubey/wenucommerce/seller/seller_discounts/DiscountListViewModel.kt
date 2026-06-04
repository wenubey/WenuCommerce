package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class DiscountListViewModel(
    private val discountRepository: DiscountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscountListState())
    val state: StateFlow<DiscountListState> = _state.asStateFlow()

    init {
        observeDiscounts()
    }

    private fun observeDiscounts() {
        val user = authRepository.currentUser.value
        val userId = user?.uuid.orEmpty()
        // For admins, pass empty string to observe all discount codes
        val sellerId = if (user?.role == UserRole.ADMIN) "" else userId

        viewModelScope.launch {
            discountRepository.observeDiscountCodes(sellerId)
                .catch { e ->
                    Timber.e(e, "DiscountListViewModel: observeDiscountCodes failed")
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { discounts ->
                    _state.update {
                        it.copy(
                            discounts = discounts,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun onAction(action: DiscountListAction) {
        when (action) {
            is DiscountListAction.Delete -> deleteDiscount(action.code)
            is DiscountListAction.Deactivate -> deactivateDiscount(action.code)
            is DiscountListAction.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun deleteDiscount(code: String) {
        viewModelScope.launch {
            discountRepository.deleteDiscountCode(code)
                .onFailure { e ->
                    Timber.e(e, "DiscountListViewModel: delete failed for $code")
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    private fun deactivateDiscount(code: String) {
        viewModelScope.launch {
            discountRepository.deactivateDiscountCode(code)
                .onFailure { e ->
                    Timber.e(e, "DiscountListViewModel: deactivate failed for $code")
                    _state.update { it.copy(error = e.message) }
                }
        }
    }
}
