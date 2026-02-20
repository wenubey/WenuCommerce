package com.wenubey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenubey.data.local.converter.RoomTypeConverters
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.dao.UserDao
import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.data.local.entity.ProductEntity
import com.wenubey.data.local.entity.UserEntity

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        UserEntity::class,
        PendingOperationEntity::class
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun categoryDao(): CategoryDao

    abstract fun userDao(): UserDao

    abstract fun pendingOperationDao(): PendingOperationDao

    companion object {
        /**
         * Migration from v1 to v2: Add pending_operations table for offline write queue.
         *
         * This migration is required for release builds (fallbackToDestructiveMigration
         * is DEBUG-only per 01-01 decision). Without this migration, existing users
         * would crash on app update.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_operations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` TEXT NOT NULL,
                        `lastAttemptAt` TEXT,
                        `errorMessage` TEXT
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
