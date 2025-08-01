package com.wenubey.wenucommerce.onboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.wenubey.wenucommerce.onboard.util.UserRoleUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleDropdownMenu(
    modifier: Modifier = Modifier,
    onRoleSelected: (UserRoleUiModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val roleOptions = UserRoleUiModel.getSelectableRoles() // Only Customer and Seller
    var selectedRole by remember {
        mutableStateOf(roleOptions.first()) // Default to Customer
    }

    Column(modifier = modifier,horizontalAlignment = Alignment.Start) {
        Text("Account Type", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedRole.name,
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    Icon(
                        imageVector = selectedRole.icon,
                        contentDescription = selectedRole.name
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                supportingText = {
                    Text(
                        text = selectedRole.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable, true),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier,
            ) {
                roleOptions.forEach { role ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = role.icon,
                                        contentDescription = role.name
                                    )
                                    Text(
                                        text = role.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    text = role.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 32.dp)
                                )
                            }
                        },
                        onClick = {
                            selectedRole = role
                            onRoleSelected(role)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}