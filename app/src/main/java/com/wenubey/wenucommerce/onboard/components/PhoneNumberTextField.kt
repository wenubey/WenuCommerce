package com.wenubey.wenucommerce.onboard.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.wenubey.wenucommerce.onboard.util.EuropeanPhoneVisualTransformation

@Composable
fun PhoneNumberTextField(
    modifier: Modifier = Modifier,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    isError: Boolean,
) {

    OutlinedTextField(
        modifier = modifier,
        value = phoneNumber,
        onValueChange = {
            if (it.length <= 12) {
                onPhoneNumberChange(it)
            }
        },
        label = {
            Text("Phone number")
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
        ),
        visualTransformation = EuropeanPhoneVisualTransformation(),
        isError = isError,
    )
}