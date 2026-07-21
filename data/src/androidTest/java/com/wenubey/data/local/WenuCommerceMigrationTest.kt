package com.wenubey.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase.Companion.MIGRATION_10_11
import com.wenubey.data.local.WenuCommerceDatabase.Companion.MIGRATION_6_7
import com.wenubey.data.local.WenuCommerceDatabase.Companion.MIGRATION_7_8
import com.wenubey.data.local.WenuCommerceDatabase.Companion.MIGRATION_8_9
import com.wenubey.data.local.WenuCommerceDatabase.Companion.MIGRATION_9_10
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the Phase-6-hardening Room migrations against the exported schemas.
 *
 * Requires a device/emulator: run `./gradlew :data:connectedDebugAndroidTest`.
 * `runMigrationsAndValidate` re-derives the expected schema from the JSON in
 * data/schemas/ and fails if a migration's output diverges.
 */
@RunWith(AndroidJUnit4::class)
class WenuCommerceMigrationTest {

    private val dbName = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WenuCommerceDatabase::class.java,
    )

    @Test
    fun migrate6To7_addsUserIdToSellerOrders_preservingRows() {
        helper.createDatabase(dbName, 6).use { db ->
            // The v6 (entity-derived) schema has no SQL defaults — every NOT NULL
            // column must be supplied.
            db.execSQL(
                "INSERT INTO seller_orders (id, parentOrderId, sellerId, sellerName, " +
                    "sellerLogoUrl, subtotal, shippingShare, discountShare, status, " +
                    "itemsJson, statusHistoryJson, createdAt, updatedAt) VALUES " +
                    "('so-1','ord-1','seller-1','Acme','',10.0,2.0,0.0,'PENDING'," +
                    "'[]','[]','2026-06-15T10:00:00Z','2026-06-15T10:00:00Z')",
            )
        }
        helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT userId FROM seller_orders WHERE id = 'so-1'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("") // NOT NULL DEFAULT ''
            }
        }
    }

    @Test
    fun migrate7To8_dropsPurchaseHistory_preservingUserData() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                "INSERT INTO users (id, role, name, surname, phoneNumber, dateOfBirth, " +
                    "gender, email, address, isEmailVerified, isPhoneNumberVerified, " +
                    "profilePhotoUri, createdAt, updatedAt, signedAt, purchaseHistoryJson, " +
                    "signedDevicesJson, businessInfoJson, productsJson) VALUES " +
                    "('u-1','CUSTOMER','Jane','Doe','','','MALE','j@x.co','',0,0,'','','','', " +
                    "'[{\"purchaseId\":\"p\"}]','[]',NULL,'[]')",
            )
        }
        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).use { db ->
            db.query("PRAGMA table_info(users)").use { c ->
                val cols = buildList {
                    while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertThat(cols).doesNotContain("purchaseHistoryJson")
                assertThat(cols).contains("signedDevicesJson")
            }
            db.query("SELECT name FROM users WHERE id = 'u-1'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Jane") // row preserved
            }
        }
    }

    @Test
    fun migrate8To9_createsReviewsTable() {
        helper.createDatabase(dbName, 8).close()
        helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9).use { db ->
            // The reviews table exists and is writable after the migration.
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='reviews'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
        }
    }

    @Test
    fun migrate6To9_chainsCleanly() {
        helper.createDatabase(dbName, 6).close()
        helper.runMigrationsAndValidate(
            dbName, 9, true, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        ).close()
    }

    @Test
    fun migrate10To11_createsFollowedSellersTable() {
        helper.createDatabase(dbName, 10).close()
        helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11).use { db ->
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='followed_sellers'",
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' " +
                    "AND name='index_followed_sellers_userId'",
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
        }
    }

    @Test
    fun migrate6To11_chainsCleanly() {
        helper.createDatabase(dbName, 6).close()
        helper.runMigrationsAndValidate(
            dbName, 11, true,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        ).close()
    }
}
