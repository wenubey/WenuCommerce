package com.wenubey.data.util

import android.os.Build
import java.time.Instant
import java.util.Date

fun getCurrentDate(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Date.from(Instant.now()).toString()
} else {
    Date().toString()
}