package com.wenubey.wenucommerce.screens.tab

data class NotificationBarState(
    val isVisible: Boolean = false,
    val isHiddenForSession: Boolean = false,
    val isPermanentlyHidden: Boolean = false
)