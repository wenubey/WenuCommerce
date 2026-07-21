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
import com.wenubey.data.local.dao.FollowedSellerDao
import com.wenubey.data.local.dao.NotificationDao
import com.wenubey.data.local.dao.OrderDao
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.dao.ReviewDao
import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.data.local.dao.UserDao
import com.wenubey.data.local.dao.WishlistItemDao
import com.wenubey.data.local.entity.AddressEntity
import com.wenubey.data.local.entity.CartItemEntity
import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.data.local.entity.FollowedSellerEntity
import com.wenubey.data.local.entity.NotificationEntity
import com.wenubey.data.local.entity.OrderEntity
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.data.local.entity.ProductEntity
import com.wenubey.data.local.entity.ReviewEntity
import com.wenubey.data.local.entity.SellerOrderEntity
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
        AddressEntity::class,
        SellerOrderEntity::class,
        ReviewEntity::class,
        NotificationEntity::class,
        FollowedSellerEntity::class,
    ],
    version = 11,
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

    abstract fun sellerOrderDao(): SellerOrderDao

    abstract fun reviewDao(): ReviewDao

    abstract fun notificationDao(): NotificationDao

    abstract fun followedSellerDao(): FollowedSellerDao

    companion object {
        /**
         * Migration from v1 to v2: Add pending_operations table for offline write queue.
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
         * Migration from v4 to v5: Add discount fields to orders table.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `orders` ADD COLUMN `discountAmount` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `orders` ADD COLUMN `discountCode` TEXT NOT NULL DEFAULT ''")
            }
        }

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

        /**
         * Migration from v5 to v6: Phase 6 — add sellerOrderIds + aggregateStatus
         * to `orders` and create the `seller_orders` table with indexes on
         * parentOrderId and sellerId. JSON columns for items + statusHistory
         * follow OrderEntity.itemsJson pattern (no @Relation — RESEARCH §2.5 / W6).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `orders` ADD COLUMN `sellerOrderIdsJson` TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE `orders` ADD COLUMN `aggregateStatus` TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seller_orders` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `parentOrderId` TEXT NOT NULL DEFAULT '',
                        `sellerId` TEXT NOT NULL DEFAULT '',
                        `sellerName` TEXT NOT NULL DEFAULT '',
                        `sellerLogoUrl` TEXT NOT NULL DEFAULT '',
                        `subtotal` REAL NOT NULL DEFAULT 0.0,
                        `shippingShare` REAL NOT NULL DEFAULT 0.0,
                        `discountShare` REAL NOT NULL DEFAULT 0.0,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `trackingNumber` TEXT,
                        `refundId` TEXT,
                        `refundedAmount` REAL,
                        `itemsJson` TEXT NOT NULL DEFAULT '[]',
                        `statusHistoryJson` TEXT NOT NULL DEFAULT '[]',
                        `createdAt` TEXT NOT NULL DEFAULT '',
                        `updatedAt` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_seller_orders_parentOrderId` " +
                        "ON `seller_orders` (`parentOrderId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_seller_orders_sellerId` " +
                        "ON `seller_orders` (`sellerId`)"
                )
            }
        }

        /**
         * Migration from v6 to v7: denormalise the customer `userId` onto
         * seller_orders (DM1). ADD COLUMN appends at the end; Room validates by
         * column name so position is irrelevant.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `seller_orders` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Migration from v7 to v8: drop the dead `purchaseHistoryJson` column
         * from `users` (DM2). SQLite before 3.35 (reachable at minSdk 24) has no
         * DROP COLUMN, so recreate the table without it and copy the rows.
         * Column list mirrors the v8 `users` schema exactly (no SQL defaults —
         * the entity uses constructor defaults, not @ColumnInfo(defaultValue)).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `surname` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `dateOfBirth` TEXT NOT NULL,
                        `gender` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `isEmailVerified` INTEGER NOT NULL,
                        `isPhoneNumberVerified` INTEGER NOT NULL,
                        `profilePhotoUri` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `signedAt` TEXT NOT NULL,
                        `signedDevicesJson` TEXT NOT NULL,
                        `businessInfoJson` TEXT,
                        `productsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `users_new` (
                        `id`, `role`, `name`, `surname`, `phoneNumber`, `dateOfBirth`,
                        `gender`, `email`, `address`, `isEmailVerified`,
                        `isPhoneNumberVerified`, `profilePhotoUri`, `createdAt`,
                        `updatedAt`, `signedAt`, `signedDevicesJson`, `businessInfoJson`,
                        `productsJson`
                    ) SELECT
                        `id`, `role`, `name`, `surname`, `phoneNumber`, `dateOfBirth`,
                        `gender`, `email`, `address`, `isEmailVerified`,
                        `isPhoneNumberVerified`, `profilePhotoUri`, `createdAt`,
                        `updatedAt`, `signedAt`, `signedDevicesJson`, `businessInfoJson`,
                        `productsJson`
                    FROM `users`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")
            }
        }

        /**
         * Migration from v8 to v9: Phase 7 — create the `reviews` table (Room
         * mirror of PRODUCTS/{id}/REVIEWS) with indices on productId + reviewerId.
         * All-scalar columns (no JSON) — same CREATE TABLE shape as MIGRATION_5_6.
         * Column NOT NULL DEFAULTs match ReviewEntity constructor defaults so
         * Room's schema validation passes.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reviews` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `productId` TEXT NOT NULL DEFAULT '',
                        `reviewerId` TEXT NOT NULL DEFAULT '',
                        `reviewerName` TEXT NOT NULL DEFAULT '',
                        `reviewerPhotoUrl` TEXT NOT NULL DEFAULT '',
                        `purchaseId` TEXT NOT NULL DEFAULT '',
                        `rating` INTEGER NOT NULL DEFAULT 0,
                        `title` TEXT NOT NULL DEFAULT '',
                        `body` TEXT NOT NULL DEFAULT '',
                        `isVerifiedPurchase` INTEGER NOT NULL DEFAULT 1,
                        `helpfulCount` INTEGER NOT NULL DEFAULT 0,
                        `isVisible` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` TEXT NOT NULL DEFAULT '',
                        `updatedAt` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reviews_productId` " +
                        "ON `reviews` (`productId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reviews_reviewerId` " +
                        "ON `reviews` (`reviewerId`)"
                )
            }
        }

        /**
         * Migration from v9 to v10: Phase 8 — create the `notifications` table
         * (Room mirror of notifications/{uid}/items) with indices on userId +
         * createdAt. All-scalar columns (no JSON) — same CREATE TABLE shape as
         * MIGRATION_8_9. Column NOT NULL DEFAULTs match NotificationEntity
         * constructor defaults so Room's schema validation passes.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `userId` TEXT NOT NULL DEFAULT '',
                        `type` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL DEFAULT '',
                        `body` TEXT NOT NULL DEFAULT '',
                        `orderId` TEXT NOT NULL DEFAULT '',
                        `sellerOrderId` TEXT NOT NULL DEFAULT '',
                        `productId` TEXT NOT NULL DEFAULT '',
                        `productTitle` TEXT NOT NULL DEFAULT '',
                        `isRead` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_userId` " +
                        "ON `notifications` (`userId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_createdAt` " +
                        "ON `notifications` (`createdAt`)"
                )
            }
        }

        /**
         * Migration from v10 to v11: Phase 9 — create the `followed_sellers`
         * table (Room mirror of USERS/{uid}/followed_sellers). Composite PK
         * (userId, sellerId) matches the entity, and index_followed_sellers_userId
         * accelerates the observeFollowedSellers query. All-scalar columns; NOT
         * NULL DEFAULTs mirror FollowedSellerEntity constructor defaults so
         * Room's schema validation passes.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `followed_sellers` (
                        `userId` TEXT NOT NULL,
                        `sellerId` TEXT NOT NULL,
                        `sellerName` TEXT NOT NULL DEFAULT '',
                        `sellerLogoUrl` TEXT NOT NULL DEFAULT '',
                        `followedAt` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`userId`, `sellerId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_followed_sellers_userId` " +
                        "ON `followed_sellers` (`userId`)"
                )
            }
        }
    }
}
