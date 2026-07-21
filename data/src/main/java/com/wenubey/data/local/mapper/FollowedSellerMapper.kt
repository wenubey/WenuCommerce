package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.FollowedSellerEntity
import com.wenubey.domain.model.FollowedSeller

fun FollowedSellerEntity.toDomain(): FollowedSeller = FollowedSeller(
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    followedAt = followedAt,
)

fun FollowedSeller.toEntity(userId: String): FollowedSellerEntity = FollowedSellerEntity(
    userId = userId,
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    followedAt = followedAt,
)
