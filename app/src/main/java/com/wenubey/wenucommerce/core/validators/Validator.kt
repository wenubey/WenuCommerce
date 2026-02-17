package com.wenubey.wenucommerce.core.validators

import android.util.Patterns

// TODO change more complicated validation methods and send repo to validate externally if possible.
// Validation helper functions
fun isValidEmail(email: String): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

fun isValidPhoneNumber(phone: String): Boolean {
    return phone.length >= 10 && phone.all { it.isDigit() || it in arrayOf(' ', '-', '(', ')') }
}

fun isValidTaxId(taxId: String): Boolean {
    // Basic validation - should be 9 digits (EIN format: XX-XXXXXXX)
    val cleanTaxId = taxId.replace("-", "")
    return cleanTaxId.length == 9 && cleanTaxId.all { it.isDigit() }
}

fun isValidBankAccount(accountNumber: String): Boolean {
    // Basic validation - should be 8-17 digits
    return accountNumber.length in 8..17 && accountNumber.all { it.isDigit() }
}

fun isValidRoutingNumber(routingNumber: String): Boolean {
    // US routing numbers are 9 digits
    return routingNumber.length == 9 && routingNumber.all { it.isDigit() }
}