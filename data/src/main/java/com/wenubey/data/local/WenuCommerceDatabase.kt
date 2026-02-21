package com.wenubey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenubey.data.local.converter.RoomTypeConverters
import com.wenubey.data.local.dao.AddressDao
import com.wenubey.data.local.dao.CartItemDao
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.dao.OrderDao
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.dao.UserDao
import com.wenubey.data.local.dao.WishlistItemDao
import com.wenubey.data.local.entity.AddressEntity
import com.wenubey.data.local.entity.CartItemEntity
import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.data.local.entity.OrderEntity
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
        WishlistItemEntity::class,
        OrderEntity::class,
        AddressEntity::class
    ],
    version = 4,
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

    abstract fun orderDao(): OrderDao

    abstract fun addressDao(): AddressDao

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

        /**
         * Migration from v3 to v4: Add orders and addresses tables.
         *
         * Orders use a single primary key (id = Firestore document ID).
         * Order items are stored as JSON in itemsJson (embedded list pattern per research).
         * Addresses use composite primary keys (userId, addressId) matching the
         * cart_items and wishlist_items pattern.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `orders` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `userId` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `subtotal` REAL NOT NULL DEFAULT 0.0,
                        `shippingTotal` REAL NOT NULL DEFAULT 0.0,
                        `totalAmount` REAL NOT NULL DEFAULT 0.0,
                        `currency` TEXT NOT NULL DEFAULT 'USD',
                        `stripePaymentIntentId` TEXT NOT NULL DEFAULT '',
                        `shippingAddressJson` TEXT NOT NULL DEFAULT '',
                        `itemsJson` TEXT NOT NULL DEFAULT '[]',
                        `createdAt` TEXT NOT NULL DEFAULT '',
                        `updatedAt` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `addresses` (
                        `userId` TEXT NOT NULL,
                        `addressId` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL DEFAULT '',
                        `line1` TEXT NOT NULL DEFAULT '',
                        `line2` TEXT NOT NULL DEFAULT '',
                        `city` TEXT NOT NULL DEFAULT '',
                        `state` TEXT NOT NULL DEFAULT '',
                        `postalCode` TEXT NOT NULL DEFAULT '',
                        `country` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`userId`, `addressId`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
