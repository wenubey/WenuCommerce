package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.Subcategory

sealed interface SellerProductEditAction {
    // --- existing (unchanged) ---
    data class OnProductUpdated(val product: Product) : SellerProductEditAction
    data object OnSave : SellerProductEditAction
    data object OnSubmitForReview : SellerProductEditAction
    data object OnDismissError : SellerProductEditAction

    // --- new: images ---
    data class OnImagesSelected(val localUris: List<String>) : SellerProductEditAction
    data class OnImageRemoved(val index: Int) : SellerProductEditAction

    // --- new: tags ---
    data class OnTagAdded(val tag: String) : SellerProductEditAction
    data class OnTagRemoved(val tag: String) : SellerProductEditAction
    data class OnTagInputChanged(val input: String) : SellerProductEditAction

    // --- new: category ---
    data class OnCategorySelected(val category: Category) : SellerProductEditAction
    data class OnSubcategorySelected(val subcategory: Subcategory) : SellerProductEditAction

    // --- new: variants ---
    data class OnVariantAdded(val variant: ProductVariant) : SellerProductEditAction
    data class OnVariantUpdated(val variant: ProductVariant) : SellerProductEditAction
    data class OnVariantRemoved(val variantId: String) : SellerProductEditAction
    data object OnHasVariantsToggled : SellerProductEditAction

    // --- new: shipping ---
    data class OnShippingUpdated(val shipping: ProductShipping) : SellerProductEditAction
}
