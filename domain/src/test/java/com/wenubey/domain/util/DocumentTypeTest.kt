package com.wenubey.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the names and ordering of DocumentType. These names are likely persisted
 * (Firestore/Room) — accidentally renaming or reordering them will corrupt
 * stored documents. If you intentionally change a name, you must migrate.
 */
class DocumentTypeTest {

    @Test
    fun `enum names are stable`() {
        assertThat(DocumentType.entries.map { it.name }).containsExactly(
            "IDENTITY_DOCUMENTS",
            "TAX_DOCUMENTS",
            "BUSINESS_LICENSE",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        DocumentType.entries.forEach { type ->
            assertThat(DocumentType.valueOf(type.name)).isEqualTo(type)
        }
    }
}
