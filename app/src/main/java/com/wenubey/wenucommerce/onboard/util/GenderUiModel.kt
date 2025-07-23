package com.wenubey.wenucommerce.onboard.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.ui.graphics.vector.ImageVector
import com.wenubey.domain.model.Gender

data class GenderUiModel(
    val name: String,
    val icon: ImageVector,
) {
    companion object {
        fun default(): GenderUiModel {
            return GenderUiModel(
                name = "Not Specified",
                icon = Icons.Default.Close
            )
        }
    }
}

fun Gender.toUiModel(): GenderUiModel = when (this) {
    Gender.MALE -> {
        GenderUiModel(
            name = "Male",
            icon = Icons.Default.Male
        )
    }

    Gender.FEMALE -> {
        GenderUiModel(
            name = "Female",
            icon = Icons.Default.Female
        )
    }

    Gender.OTHER -> {
        GenderUiModel(
            name = "Other",
            icon = Icons.Default.Transgender
        )
    }

    Gender.NOT_SPECIFIED -> {
        GenderUiModel(
            name = "Not Specified",
            icon = Icons.Default.Close
        )
    }

}

fun GenderUiModel.toDomainModel(): Gender = when(this) {
    GenderUiModel(
        name = "Male",
        icon = Icons.Default.Male
    ) -> Gender.MALE
    GenderUiModel(
        name = "Female",
        icon = Icons.Default.Female
    ) -> Gender.FEMALE
    GenderUiModel(
        name = "Other",
        icon = Icons.Default.Transgender
    ) -> Gender.OTHER
    else -> Gender.NOT_SPECIFIED
}
