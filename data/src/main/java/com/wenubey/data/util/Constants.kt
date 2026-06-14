package com.wenubey.data.util

const val USER_COLLECTION = "USERS"
const val CATEGORIES_COLLECTION = "CATEGORIES"
/**
 * Canonical Storage folder for user profile photos. Both
 * FirestoreRepositoryImpl.updateProfilePhoto and
 * ProfileRepositoryImpl.uploadProfilePhoto write here; objects are stored
 * under `profile_photos/{uid}/profile_image_{yyyyMMdd_HHmmss}.jpg`.
 *
 * Note: the legacy `profile_images` folder (with files named
 * `{uid}_profile_image.jpeg`) used to be written by FirestoreRepository
 * only. As of TB-9 fix both paths funnel into PROFILE_PHOTOS_FOLDER, so
 * fresh writes never produce the legacy folder, but historical objects
 * may still exist in production.
 */
const val PROFILE_PHOTOS_FOLDER = "profile_photos"
const val PRODUCTS_COLLECTION = "PRODUCTS"
const val REVIEWS_SUBCOLLECTION = "REVIEWS"
const val PRODUCT_IMAGES_FOLDER = "product_images"
const val TAGS_COLLECTION = "TAGS"