package com.wenubey.wenucommerce.onboard.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.onboard.BusinessType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessTypeDropdownMenu(
    modifier: Modifier = Modifier,
    selectedBusinessType: BusinessType,
    onBusinessTypeSelected: (BusinessType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val businessTypeOptions = BusinessType.entries

    Spacer(modifier = Modifier.height(4.dp))

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedBusinessType.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Business Type") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryEditable, true),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier,
        ) {
            businessTypeOptions.forEach { businessType ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = businessType.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                    },
                    onClick = {
                        onBusinessTypeSelected(businessType)
                        expanded = false
                    },
                )
            }
        }
    }
}