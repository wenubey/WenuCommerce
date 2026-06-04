package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Stub - will be fully implemented in Task 2
@Composable
fun SellerDiscountCreateEditScreen(
    code: String?,
    isSeller: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = if (code != null) "Edit Discount: $code" else "Create Discount")
    }
}
