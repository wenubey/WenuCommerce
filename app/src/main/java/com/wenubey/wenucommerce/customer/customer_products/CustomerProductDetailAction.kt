package com.wenubey.wenucommerce.customer.customer_products

import com.wenubey.domain.model.product.ProductVariant

sealed interface CustomerProductDetailAction {
    data class OnVariantSelected(val variant: ProductVariant) : CustomerProductDetailAction
    data class OnMarkReviewHelpful(val reviewId: String) : CustomerProductDetailAction
}
