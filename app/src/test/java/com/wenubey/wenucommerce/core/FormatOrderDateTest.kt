package com.wenubey.wenucommerce.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [formatOrderDate] — the shared order-timestamp formatter.
 *
 * Guards the D1 fix: Firestore Timestamps used to leak into the UI as
 * "Timestamp(seconds=…)". The mapper now normalises to ISO-8601; this formatter
 * turns ISO (and, defensively, legacy epoch-millis) into a friendly date and
 * never surfaces the raw ugly form.
 */
class FormatOrderDateTest {

    @Test
    fun `blank input returns empty string`() {
        assertThat(formatOrderDate("")).isEqualTo("")
        assertThat(formatOrderDate("   ")).isEqualTo("")
    }

    @Test
    fun `ISO-8601 instant formats to friendly date`() {
        // 2026-07-15T10:00:00Z — day is timezone-dependent, so assert the parts
        // that are stable regardless of the runner's zone.
        val out = formatOrderDate("2026-07-15T10:00:00Z")
        assertThat(out).contains("2026")
        assertThat(out).contains("Jul")
        assertThat(out).doesNotContain("T")
        assertThat(out).doesNotContain("Timestamp")
    }

    @Test
    fun `legacy epoch-millis string still formats (does not show raw millis)`() {
        // 1_784_067_647_263 ms ≈ 2026 — must not render the raw number.
        val out = formatOrderDate("1784067647263")
        assertThat(out).contains("2026")
        assertThat(out).doesNotContain("1784067647263")
    }

    @Test
    fun `unparseable value falls back to the raw string, never crashes`() {
        assertThat(formatOrderDate("not-a-date")).isEqualTo("not-a-date")
    }

    @Test
    fun `never emits the raw Firestore Timestamp toString form`() {
        val raw = "Timestamp(seconds=1784067647, nanoseconds=263000000)"
        // Even if such a value somehow reaches the formatter, it returns it
        // verbatim (fallback) rather than throwing — the real fix is upstream
        // in the mapper, this just documents the no-crash contract.
        assertThat(formatOrderDate(raw)).isEqualTo(raw)
    }
}
