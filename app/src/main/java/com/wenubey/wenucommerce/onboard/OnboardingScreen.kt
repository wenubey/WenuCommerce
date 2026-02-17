package com.wenubey.wenucommerce.onboard

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.onboard.components.DatePickerTextField
import com.wenubey.wenucommerce.onboard.components.DocumentType
import com.wenubey.wenucommerce.onboard.components.GenderDropdownMenu
import com.wenubey.wenucommerce.onboard.components.OnboardingDatePicker
import com.wenubey.wenucommerce.onboard.components.PhoneNumberTextField
import com.wenubey.wenucommerce.onboard.components.RoleDropdownMenu
import com.wenubey.wenucommerce.onboard.components.SellerFieldsSection
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

//TODO Refactor whole screen and extract components
@Composable
fun OnboardingScreen(onNavigateToTabScreen: (userRole: UserRole) -> Unit) {
    val viewModel: OnboardingViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()


    LaunchedEffect(state.completedUser) {
        state.completedUser?.let { user ->
            onNavigateToTabScreen(user.role)
        }
    }

    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var currentDocumentType by remember { mutableStateOf<DocumentType?>(null) }

    val painter = rememberAsyncImagePainter(
        model = state.photoUrl,
        error = rememberVectorPainter(
            image = Icons.Default.AccountCircle
        ),
        onError = { error ->
            Timber.e(error.result.throwable)
        }
    )

    // Document picker launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { documentUri ->
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(documentUri, flag)

                when (currentDocumentType) {
                    DocumentType.TAX_DOCUMENT -> {
                        viewModel.onAction(OnboardingAction.OnTaxDocumentUpload(documentUri.toString()))
                    }
                    DocumentType.BUSINESS_LICENSE_DOCUMENT -> {
                        viewModel.onAction(OnboardingAction.OnBusinessLicenseDocumentUpload(documentUri.toString()))
                    }
                    DocumentType.IDENTITY_DOCUMENT -> {
                        viewModel.onAction(OnboardingAction.OnIdentityDocumentUpload(documentUri.toString()))
                    }
                    null -> {}
                }
                currentDocumentType = null
            }
        }
    )

    // Permission launcher for documents
    val documentPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions.values.all { it }
            if (isGranted) {
                documentLauncher.launch(arrayOf("application/pdf", "image/*"))
            } else {
                Timber.d("Document permissions NOT GRANTED: $permissions")
            }
        }
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            // Profile Image Section
            Box {
                Image(
                    painter = painter,
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(120.dp)
                )
                ImagePicker(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onImageSelected = {
                        viewModel.onAction(OnboardingAction.OnPhotoUrlChange(it.toString()))
                    },
                )
            }

            // Basic Information Section
            Text(
                text = "Personal Information",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = {
                    viewModel.onAction(OnboardingAction.OnNameChange(it))
                },
                label = { Text("Name *") },
                isError = state.nameError
            )

            OutlinedTextField(
                value = state.surname,
                onValueChange = {
                    viewModel.onAction(OnboardingAction.OnSurnameChange(it))
                },
                label = { Text("Surname *") },
                isError = state.surnameError,
            )

            PhoneNumberTextField(
                phoneNumber = state.phoneNumber,
                onPhoneNumberChange = {
                    viewModel.onAction(OnboardingAction.OnPhoneNumberChange(it))
                },
                isError = state.phoneNumberError,
            )

            Column(horizontalAlignment = Alignment.Start) {
                Text("Date Of Birth", style = MaterialTheme.typography.labelSmall)
                DatePickerTextField(
                    modifier = Modifier.padding(top = 4.dp),
                    dateOfBirth = state.dateOfBirth,
                    onDatePickerOpened = {
                        showDatePicker = true
                    },
                )
            }

            OutlinedTextField(
                value = state.address,
                onValueChange = {
                    viewModel.onAction(OnboardingAction.OnAddressChange(it))
                },
                label = { Text("Address") },
            )

            Column(horizontalAlignment = Alignment.Start) {
                Text("Gender", style = MaterialTheme.typography.labelSmall)
                GenderDropdownMenu(
                    onGenderSelected = {
                        viewModel.onAction(OnboardingAction.OnGenderSelected(it))
                    },
                )
            }

            // Role Selection
            Column(horizontalAlignment = Alignment.Start) {
                Text("Account Type", style = MaterialTheme.typography.labelSmall)
                RoleDropdownMenu(
                    onRoleSelected = {
                        viewModel.onAction(OnboardingAction.OnRoleSelected(it))
                    },
                )
            }

            // Seller-specific fields (shown conditionally)
            AnimatedVisibility(
                visible = state.role.name == "Seller",
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SellerFieldsSection(
                    state = state,
                    onAction = viewModel::onAction,
                    onDocumentPicker = { documentType ->
                        currentDocumentType = documentType
                        checkAndRequestPermissions(
                            launcher = documentPermissionLauncher,
                            context = context,
                            permissionGranted = {
                                documentLauncher.launch(arrayOf("application/pdf", "image/*"))
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Submit Button
            Button(
                onClick = {
                    viewModel.onAction(OnboardingAction.OnOnboardingComplete)
                },
                content = {
                    Text("Complete Registration")
                },
                enabled = state.isNextButtonEnabled,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (showDatePicker) {
                OnboardingDatePicker(
                    onDismissRequest = { showDatePicker = false },
                    onDateSelected = {
                        viewModel.onAction(OnboardingAction.OnDateOfBirthSelected(it))
                    }
                )
            }
        }
    }
}

@Composable
fun ImagePicker(modifier: Modifier = Modifier, onImageSelected: (Uri) -> Unit) {
    val context = LocalContext.current

    val launcherImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            val contentResolver = context.contentResolver
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION

            uri?.let {
                onImageSelected(it)
                contentResolver.takePersistableUriPermission(it, flag)
            }
        }
    )

    val launcherPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions.values.all { it }
            if (isGranted) {
                launcherImage.launch(arrayOf("image/*"))
            } else {
                Timber.d("NOT GRANTED: $permissions")
                // Handle denied permission (e.g., show a message)
            }
        }
    )

    IconButton(
        modifier = modifier,
        onClick = {
            checkAndRequestPermissions(
                launcher = launcherPermission,
                context = context,
                permissionGranted = {
                    launcherImage.launch(arrayOf("image/*"))
                },
            )
        },
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "You can upload a new profile photo"
        )
    }
}

fun checkAndRequestPermissions(
    launcher: ActivityResultLauncher<Array<String>>,
    context: Context,
    permissionGranted: () -> Unit
) {
    val permissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            arrayOf(READ_MEDIA_VISUAL_USER_SELECTED)
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        }

        else -> {
            arrayOf(READ_EXTERNAL_STORAGE)
        }
    }

    val allGranted = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (allGranted) {
        permissionGranted()
    } else {
        launcher.launch(permissions)
    }
}