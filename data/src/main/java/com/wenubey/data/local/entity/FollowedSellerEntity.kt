package com.wenubey.data.local.entity

import androidx.room.Entity

/**
 * Room row for the `followed_sellers` table. Composite PK (userId, sellerId)
 * lets a customer follow many sellers while keeping upserts idempotent.
 *
 * userId is REQUIRED (non-blank) — the follow feature is authenticated only
 * (D-03 in 09-CONTEXT.md).
 */
@Entity(
    tableName = "followed_sellers",
    primaryKeys = ["userId", "sellerId"]
)
data class FollowedSellerEntity(
    val userId: String,
    val sellerId: String,
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val followedAt: String = "",
)
