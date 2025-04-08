package com.wenubey.wenucommerce.onboard.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import timber.log.Timber

class EuropeanPhoneVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length > 12) text.text.substring(0..11) else text.text

        var out = ""
        for (i in trimmed.indices) {
            if (i == 3 || i == 6 || i == 9) out += " "
            out += trimmed[i]
        }
        return TransformedText(AnnotatedString(out), phoneNumberOffsetTranslator)
    }

    private val phoneNumberOffsetTranslator = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            var transformedOffset = offset
            var spacesCount = 0

            // Adding spaces after the 3rd, 6th, and 9th digits
            for (i in 0 until offset) {
                if (i == 3 || i == 6 || i == 9) spacesCount++
            }
            transformedOffset += spacesCount
            return transformedOffset
        }

        override fun transformedToOriginal(offset: Int): Int {
            var originalOffset = offset
            var spacesCount = 0

            // Removing spaces from the transformed offset for correct original mapping
            for (i in 0 until offset) {
                if (i == 3 || i == 6 || i == 9) spacesCount++
            }
            originalOffset -= spacesCount
            return originalOffset
        }
    }
}