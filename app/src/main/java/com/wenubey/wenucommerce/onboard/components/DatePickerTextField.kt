package com.wenubey.wenucommerce.onboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DatePickerTextField(
    modifier: Modifier = Modifier,
    dateOfBirth: String?,
    onDatePickerOpened: () -> Unit
) {
    OutlinedButton(
        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
        modifier = modifier
            .size(
                width = OutlinedTextFieldDefaults.MinWidth,
                height = OutlinedTextFieldDefaults.MinHeight
            )
            .clickable { onDatePickerOpened.invoke() },
        shape = OutlinedTextFieldDefaults.shape,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateOfBirth ?: "MM/DD/YYYY",
                    color = OutlinedTextFieldDefaults.colors().unfocusedLabelColor,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Icon(imageVector = Icons.Filled.DateRange, contentDescription = null)
            }

        },
        onClick = onDatePickerOpened
    )
}
