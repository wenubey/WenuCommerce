package com.wenubey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenubey.data.local.converter.RoomTypeConverters
import com.wenubey.data.local.dao.CartItemDao
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.dao.UserDao
import com.wenubey.data.local.dao.WishlistItemDao
import com.wenubey.data.local.entity.CartItemEntity
import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.data.local.entity.ProductEntity
import com.wenubey.data.local.entity.UserEntity
import com.wenubey.data.local.entity.WishlistItemEntity

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        UserEntity::class,
        PendingOperationEntity::class,
        CartItemEntity::class,
        WishlistItemEntity::class
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun categoryDao(): CategoryDao

    abstract fun userDao(): UserDao

    abstract fun pendingOperationDao(): PendingOperationDao

    abstract fun cartItemDao(): CartItemDao

    abstract fun wishlistItemDao(): WishlistItemDao

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

        /**
         * Migration from v2 to v3: Add cart_items and wishlist_items tables.
         *
         * Both tables use composite primary keys (userId, productId) to prevent
         * duplicate entries per user/product combination.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cart_items` (
                        `userId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `productTitle` TEXT NOT NULL DEFAULT '',
                        `productImageUrl` TEXT NOT NULL DEFAULT '',
                        `quantity` INTEGER NOT NULL DEFAULT 1,
                        `snapshotPrice` REAL NOT NULL DEFAULT 0.0,
                        `availableStock` INTEGER NOT NULL DEFAULT 0,
                        `isProductDeleted` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` TEXT NOT NULL DEFAULT '',
                        `updatedAt` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`userId`, `productId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wishlist_items` (
                        `userId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `productTitle` TEXT NOT NULL DEFAULT '',
                        `productImageUrl` TEXT NOT NULL DEFAULT '',
                        `productPrice` REAL NOT NULL DEFAULT 0.0,
                        `availableStock` INTEGER NOT NULL DEFAULT 0,
                        `isProductDeleted` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`userId`, `productId`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
