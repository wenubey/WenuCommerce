package com.wenubey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wenubey.data.local.converter.RoomTypeConverters
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.dao.UserDao
import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.data.local.entity.ProductEntity
import com.wenubey.data.local.entity.UserEntity

@Database(
    entities = [ProductEntity::class, CategoryEntity::class, UserEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun categoryDao(): CategoryDao

    abstract fun userDao(): UserDao
}
