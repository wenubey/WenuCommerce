package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus

sealed interface SellerProductListAction {
    data class OnSearchQueryChanged(val query: String) : SellerProductListAction
    data class OnStatusFilterSelected(val status: ProductStatus?) : SellerProductListAction
    data class OnSubmitForReview(val productId: String) : SellerProductListAction
    data class OnShowDeleteDialog(val product: Product) : SellerProductListAction
    data class OnUnarchiveProduct(val productId: String) : SellerProductListAction
    data object OnDismissDeleteDialog : SellerProductListAction
    data object OnConfirmDelete : SellerProductListAction
    // Category filter actions
    data class OnFilterCategorySelected(val categoryId: String?) : SellerProductListAction
    data class OnFilterSubcategorySelected(val subcategoryId: String?) : SellerProductListAction
    data object OnClearCategoryFilters : SellerProductListAction
    data object OnRequestCategoryLoad : SellerProductListAction
}
