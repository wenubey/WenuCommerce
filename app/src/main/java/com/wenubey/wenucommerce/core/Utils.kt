package com.wenubey.wenucommerce.core

import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

fun formatDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"

    return try {
        // Define the input format pattern to match your date string
        val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault())

        // Define the output format pattern to display the date in the desired format
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        // Parse the input string to a Date object
        val date = inputFormat.parse(dateString)

        // Return the formatted date, or the original string if parsing failed
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        // Return the original string if there is an error parsing
        dateString
    }
}


fun generateDummyUser(): User {
    return User(
        uuid = UUID.randomUUID().toString(),
        role = UserRole.CUSTOMER,
        name = "John",
        surname = "Doe",
        phoneNumber = "+1-800-555-1234",
        dateOfBirth = "1990-01-01",
        gender = Gender.MALE,
        email = "john.doe@example.com",
        address = "1234 Elm Street, Springfield, IL, 62701",
        isEmailVerified = true,
        isPhoneNumberVerified = false,
        profilePhotoUri = "http://example.com/profile/john_doe.jpg",
        purchaseHistory = listOf(),
        createdAt = "2022-05-15T10:30:00",
        updatedAt = "2023-01-01T12:00:00",
        signedAt = "2021-06-10T09:00:00",
        signedDevices = listOf(),
        businessInfo = generateDummyBusinessInfo()
    )
}

fun generateDummyBusinessInfo(): BusinessInfo {
    return BusinessInfo(
        businessName = "Doe's Bakery",
        businessType = BusinessType.INDIVIDUAL,
        businessDescription = "A local bakery specializing in fresh pastries and cakes.",
        businessAddress = "4567 Maple Avenue, Springfield, IL, 62702",
        businessPhone = "+1-800-555-9876",
        businessEmail = "info@doesbakery.com",
        taxId = "12-3456789",
        businessLicense = "ABC1234567",
        bankAccountNumber = "1234567890",
        routingNumber = "987654321",
        taxDocumentUri = "http://example.com/tax_document.pdf",
        businessLicenseDocumentUri = "http://example.com/business_license.pdf",
        identityDocumentUri = "http://example.com/identity_document.pdf",
        isVerified = false,
        verificationStatus = VerificationStatus.REQUEST_MORE_INFO,
        verificationDate = null,
        verificationNotes = null,
        createdAt = "2022-05-15T10:30:00",
        updatedAt = "2023-01-01T12:00:00"
    )
}

