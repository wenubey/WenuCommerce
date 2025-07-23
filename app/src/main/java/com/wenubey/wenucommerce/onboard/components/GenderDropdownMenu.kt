package com.wenubey.wenucommerce.onboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.Gender
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.toUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderDropdownMenu(modifier: Modifier = Modifier, onGenderSelected: (GenderUiModel) -> Unit ) {
    var expanded by remember { mutableStateOf(false) }
    val genderOptions = Gender.entries.toList().map { it.toUiModel() }
    var selectedGender by remember {
        mutableStateOf(
            genderOptions.last()
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedGender.name,
            onValueChange = {},
            readOnly = true,
            leadingIcon = {
                Icon(
                    imageVector = selectedGender.icon,
                    contentDescription = selectedGender.name
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable, true),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier,
        ) {
            genderOptions.forEach { gender ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = gender.icon,
                                contentDescription = gender.name
                            )
                            Text(text = gender.name)
                        }
                    },
                    onClick = {
                        selectedGender = gender
                        onGenderSelected(gender)
                        expanded = false
                    },
                )
            }
        }
    }
}