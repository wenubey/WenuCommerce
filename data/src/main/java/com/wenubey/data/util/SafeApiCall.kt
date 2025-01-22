package com.wenubey.data.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

suspend fun <T> safeApiCall(dispatcher: CoroutineDispatcher, apiCall: suspend () -> T): Result<T> =
    withContext(dispatcher) {
        try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Timber.e("Error during API call: ${e.message}")
            Result.failure(e)
        }
    }