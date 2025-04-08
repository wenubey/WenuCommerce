package com.wenubey.domain.repository

import android.net.Uri
import com.wenubey.domain.model.Gender

interface ProfileRepository {
    suspend fun onboarding(
        name: String,
        surname: String,
        phoneNumber: String,
        dateOfBirth: String,
        address: String,
        gender: Gender,
        photoUrl: Uri,
    ): Result<Unit>
}