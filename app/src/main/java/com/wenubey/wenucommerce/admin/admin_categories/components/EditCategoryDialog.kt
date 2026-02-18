package com.wenubey.wenucommerce.admin.admin_categories.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import java.util.UUID

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category, newImageUri: String?) -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    var description by remember { mutableStateOf(category.description) }
    val subcategories = remember { mutableStateListOf(*category.subcategories.toTypedArray()) }
    var newSubcategoryName by remember { mutableStateOf("") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Show image preview: new pick takes priority, then existing URL
                val imageModel: Any? = selectedImageUri ?: category.imageUrl.ifBlank { null }
                if (imageModel != null) {
                    item {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Category image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (selectedImageUri != null || category.imageUrl.isNotBlank())
                                "Change Image"
                            else
                                "Select Image"
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                item {
                    Text(
                        text = "Subcategories",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                itemsIndexed(subcategories) { index, subcategory ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = subcategory.name,
                            onValueChange = { newName ->
                                subcategories[index] = subcategory.copy(name = newName)
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { subcategories.removeAt(index) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color(0xFFE53E3E),
                            )
                        }
                    }
                }

                // Add new subcategory row
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = newSubcategoryName,
                            onValueChange = { newSubcategoryName = it },
                            label = { Text("New Subcategory") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (newSubcategoryName.isNotBlank()) {
                                    subcategories.add(
                                        Subcategory(
                                            id = UUID.randomUUID().toString(),
                                            name = newSubcategoryName.trim(),
                                        )
                                    )
                                    newSubcategoryName = ""
                                }
                            },
                            enabled = newSubcategoryName.isNotBlank(),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Subcategory",
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedSubcategories = subcategories
                        .filter { it.name.isNotBlank() }
                        .map { it.copy(name = it.name.trim()) }
                    onSave(
                        category.copy(
                            name = name.trim(),
                            description = description.trim(),
                            subcategories = trimmedSubcategories,
                        ),
                        selectedImageUri?.toString(),
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
