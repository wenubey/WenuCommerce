package com.wenubey.domain.model.onboard

data class BusinessInfo(
    val businessName: String,
    val businessType: String, // Individual, LLC, Corporation, Partnership, Non-Profit
    val businessDescription: String,
    val businessAddress: String,
    val businessPhone: String,
    val businessEmail: String,
    val taxId: String, // EIN or Tax ID
    val businessLicense: String,
    val bankAccountNumber: String,
    val routingNumber: String,
    val taxDocumentUri: String,
    val businessLicenseDocumentUri: String,
    val identityDocumentUri: String,
    val isVerified: Boolean = false,
    val verificationStatus: String = "PENDING", // PENDING, APPROVED, REJECTED, UNDER_REVIEW
    val verificationDate: String? = null,
    val verificationNotes: String? = null,
    val createdAt: String,
    val updatedAt: String
)

fun BusinessInfo.toMap(): Map<String, Any> = mapOf(
    "businessName" to businessName,
    "businessType" to businessType,
    "businessDescription" to businessDescription,
    "businessAddress" to businessAddress,
    "businessPhone" to businessPhone,
    "businessEmail" to businessEmail,
    "taxId" to taxId,
    "businessLicense" to businessLicense,
    "bankAccountNumber" to bankAccountNumber,
    "routingNumber" to routingNumber,
    "taxDocumentUri" to taxDocumentUri,
    "businessLicenseDocumentUri" to businessLicenseDocumentUri,
    "identityDocumentUri" to identityDocumentUri,
    "isVerified" to isVerified,
    "verificationStatus" to verificationStatus,
    "verificationDate" to (verificationDate ?: ""),
    "verificationNotes" to (verificationNotes ?: ""),
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
)