package com.wenubey.wenucommerce.notification.notification_history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression tests for [relativeTimestamp] (CR-01).
 *
 * The data layer stores `createdAt` as an epoch-millis String (NotificationMapper /
 * NotificationRepositoryImpl), NOT ISO-8601. The original implementation parsed only ISO and
 * fell back to the raw input, so every row rendered a raw number like "1752750000000" to the
 * user. These tests pin the epoch-millis contract and guard the "never leak a raw number" rule.
 */
class RelativeTimestampTest {

    @Test
    fun `epoch-millis a few seconds ago renders Just now`() {
        val tenSecondsAgo = (System.currentTimeMillis() - 10_000).toString()
        assertThat(relativeTimestamp(tenSecondsAgo)).isEqualTo("Just now")
    }

    @Test
    fun `epoch-millis minutes ago renders N min ago (not the raw number)`() {
        val rawInput = (System.currentTimeMillis() - (10 * 60 + 30) * 1000L).toString()
        val result = relativeTimestamp(rawInput)
        assertThat(result).endsWith("min ago")
        // The exact CR-01 defect: the raw epoch-millis string must never be shown.
        assertThat(result).isNotEqualTo(rawInput)
    }

    @Test
    fun `epoch-millis hours ago renders hours ago`() {
        val threeHoursAgo = (System.currentTimeMillis() - 3 * 60 * 60 * 1000L).toString()
        assertThat(relativeTimestamp(threeHoursAgo)).isEqualTo("3 hours ago")
    }

    @Test
    fun `an ISO-8601 value still parses via the fallback`() {
        // Server-provided ISO strings must remain supported and must not render raw.
        val result = relativeTimestamp("2020-06-15T00:00:00Z")
        assertThat(result).isNotEmpty()
        assertThat(result).isNotEqualTo("2020-06-15T00:00:00Z")
    }

    @Test
    fun `an unparseable value renders empty rather than a raw string`() {
        assertThat(relativeTimestamp("not-a-timestamp")).isEmpty()
    }
}
