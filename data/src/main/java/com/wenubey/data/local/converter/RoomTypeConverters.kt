package com.wenubey.data.local.converter

import androidx.room.TypeConverter
import com.wenubey.domain.model.Device
import com.wenubey.domain.model.Purchase
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.Subcategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // List<String> converters
    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrElse { emptyList() }

    // List<ProductImage> converters
    @TypeConverter
    fun fromProductImageList(value: List<ProductImage>): String = json.encodeToString(value)

    @TypeConverter
    fun toProductImageList(value: String): List<ProductImage> =
        runCatching { json.decodeFromString<List<ProductImage>>(value) }.getOrElse { emptyList() }

    // List<ProductVariant> converters
    @TypeConverter
    fun fromProductVariantList(value: List<ProductVariant>): String = json.encodeToString(value)

    @TypeConverter
    fun toProductVariantList(value: String): List<ProductVariant> =
        runCatching { json.decodeFromString<List<ProductVariant>>(value) }.getOrElse { emptyList() }

    // ProductShipping converters
    @TypeConverter
    fun fromProductShipping(value: ProductShipping): String = json.encodeToString(value)

    @TypeConverter
    fun toProductShipping(value: String): ProductShipping =
        runCatching { json.decodeFromString<ProductShipping>(value) }.getOrElse { ProductShipping() }

    // List<Subcategory> converters
    @TypeConverter
    fun fromSubcategoryList(value: List<Subcategory>): String = json.encodeToString(value)

    @TypeConverter
    fun toSubcategoryList(value: String): List<Subcategory> =
        runCatching { json.decodeFromString<List<Subcategory>>(value) }.getOrElse { emptyList() }

    // List<Purchase> converters
    @TypeConverter
    fun fromPurchaseList(value: List<Purchase>): String = json.encodeToString(value)

    @TypeConverter
    fun toPurchaseList(value: String): List<Purchase> =
        runCatching { json.decodeFromString<List<Purchase>>(value) }.getOrElse { emptyList() }

    // List<Device> converters
    @TypeConverter
    fun fromDeviceList(value: List<Device>): String = json.encodeToString(value)

    @TypeConverter
    fun toDeviceList(value: String): List<Device> =
        runCatching { json.decodeFromString<List<Device>>(value) }.getOrElse { emptyList() }

    // BusinessInfo? converters
    @TypeConverter
    fun fromBusinessInfo(value: BusinessInfo?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toBusinessInfo(value: String?): BusinessInfo? =
        value?.let { runCatching { json.decodeFromString<BusinessInfo>(it) }.getOrNull() }

    // Map<String, String> converters (for ProductVariant.attributes)
    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(value) }.getOrElse { emptyMap() }
}
