package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.Subcategory

sealed interface SellerProductCreateAction {
    data class OnTitleChanged(val value: String) : SellerProductCreateAction
    data class OnDescriptionChanged(val value: String) : SellerProductCreateAction
    data class OnPriceChanged(val value: String) : SellerProductCreateAction
    data class OnComparePriceChanged(val value: String) : SellerProductCreateAction
    data class OnConditionSelected(val condition: ProductCondition) : SellerProductCreateAction
    data class OnCategorySelected(val category: Category) : SellerProductCreateAction
    data class OnSubcategorySelected(val subcategory: Subcategory) : SellerProductCreateAction
    data class OnTagAdded(val tag: String) : SellerProductCreateAction
    data class OnTagRemoved(val tag: String) : SellerProductCreateAction
    data class OnTagInputChanged(val input: String) : SellerProductCreateAction
    data class OnImagesSelected(val localUris: List<String>) : SellerProductCreateAction
    data class OnImageRemoved(val index: Int) : SellerProductCreateAction
    data class OnVariantAdded(val variant: ProductVariant) : SellerProductCreateAction
    data class OnVariantUpdated(val variant: ProductVariant) : SellerProductCreateAction
    data class OnVariantRemoved(val variantId: String) : SellerProductCreateAction
    data class OnShippingUpdated(val shipping: ProductShipping) : SellerProductCreateAction
    data object OnHasVariantsToggled : SellerProductCreateAction
    data object OnSaveDraft : SellerProductCreateAction
    data object OnSubmitForReview : SellerProductCreateAction
    data object OnDismissError : SellerProductCreateAction
}
