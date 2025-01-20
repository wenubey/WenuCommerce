package com.wenubey.data.util

import timber.log.Timber

inline fun <T> safeApiCall(apiCall: () -> T): Result<T> {
    return try {
        Timber.d("safeApiCall:SUCCESS")
        Result.success(apiCall())
    } catch (e: Exception) {
        Timber.e(e, "safeApiCall:ERROR: ")
        Result.failure(exception = e)
    }
}