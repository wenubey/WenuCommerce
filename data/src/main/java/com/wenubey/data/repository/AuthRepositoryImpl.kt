package com.wenubey.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class AuthRepositoryImpl(
    private val credentialManager: CredentialManager,
    private val firebaseAuth: FirebaseAuth,
    private val context: Context,
    private val googleIdOption: GetGoogleIdOption,
    dispatcherProvider: DispatcherProvider,
    private val firestoreRepository: FirestoreRepository,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    private val ioDispatcher = dispatcherProvider.io()

    override val currentFirebaseUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private var userListener: ListenerRegistration? = null

    init {
        CoroutineScope(ioDispatcher).launch {

        }
        initializeUserState()
        firebaseAuth.addAuthStateListener { auth ->
            if (auth.currentUser == null) {
                Timber.d("CurrentUser is null")
                stopUserListener()
                _currentUser.value = null
            } else {
                startUserListener(auth.currentUser!!.uid)
            }
        }
    }

    private fun startUserListener(uid: String) {
        userListener?.remove()

        userListener = firestore.collection(USER_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Failed to load user data")
                    return@addSnapshotListener
                }

                val user = try {
                    snapshot?.toObject(User::class.java)
                } catch (e: RuntimeException) {
                    Timber.e(e, "Failed to deserialize current user document")
                    null
                }
                _currentUser.value = user
            }
    }

    private fun stopUserListener() {
        userListener?.remove()
        userListener = null
    }

    private fun initializeUserState() {
        firebaseAuth.currentUser?.let { firebaseUser ->
            CoroutineScope(ioDispatcher).launch {
                try {
                    val userResult = firestoreRepository.getUser(firebaseUser.uid)
                    userResult.fold(
                        onSuccess = { user ->
                            Timber.d("CurrentUser is : ${user.name}")
                            _currentUser.value = user
                        },
                        onFailure = {
                            Timber.w("Failed to load user data on initialization: ${it.message}")
                            _currentUser.value = null
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error initializing user state")
                    _currentUser.value = null
                }
            }
        }
    }

    private fun updateCurrentUser(firebaseUser: FirebaseUser): User? {
        startUserListener(firebaseUser.uid)
        return _currentUser.value
    }

    override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
        safeApiCall(dispatcher = ioDispatcher) {
            val firebaseUser: FirebaseUser? = when (val credential = credentialResponse.credential) {
                is PasswordCredential -> {
                    val email = credential.id
                    val password = credential.password
                    val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                    authResult.user?.also { user ->
                        firestoreRepository.updateSignedDevice(user.uid)
                    }
                }

                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val firebaseGoogleCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(firebaseGoogleCredential).await()
                        authResult.user?.also { user ->
                            firestoreRepository.updateSignedDevice(user.uid)
                        }
                    } else {
                        throw IllegalArgumentException("Unrecognized custom credential type: ${credential.type}")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported credential type: ${credential::class.java.name}")
            }

            if (firebaseUser != null) {
                val user = updateCurrentUser(firebaseUser)
                user ?: throw Exception("Failed to fetch user data after authentication")
            } else {
                throw Exception("Firebase authentication failed or user is null.")
            }
        }

    override suspend fun getCredential(): Result<GetCredentialResponse?> =
        safeApiCall(dispatcher = ioDispatcher) {
            val passwordOption = GetPasswordOption()
            val credentialRequest = GetCredentialRequest(
                credentialOptions = listOf(passwordOption, googleIdOption)
            )
            credentialManager.getCredential(
                context = context,
                request = credentialRequest
            )
        }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        saveCredentials: Boolean
    ): SignUpResult = withContext(ioDispatcher) {
        try {
            if (saveCredentials) {
                val passwordRequest = CreatePasswordRequest(id = email, password = password)
                credentialManager.createCredential(context = context, request = passwordRequest)
            }

            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                sendEmailVerificationIfNeeded(user)
                // Note: Don't update currentUser here as user hasn't completed onboarding
                // The user will be set when they complete the onboarding process
            }
            SignUpResult.Success
        } catch (e: CreateCredentialCancellationException) {
            Timber.e(e)
            SignUpResult.Cancelled
        } catch (e: CreateCredentialException) {
            Timber.e(e)
            SignUpResult.Failure(e.message)
        } catch (e: FirebaseAuthException) {
            Timber.e(e)
            SignUpResult.Failure(e.message)
        }
    }

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String,
        saveCredentials: Boolean,
    ): SignInResult = withContext(ioDispatcher) {
        try {
            if (saveCredentials) {
                val passwordRequest = CreatePasswordRequest(id = email, password = password)
                credentialManager.createCredential(context = context, request = passwordRequest)
            }

            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                sendEmailVerificationIfNeeded(firebaseUser)
                firestoreRepository.updateSignedDevice(firebaseUser.uid)

                val user = updateCurrentUser(firebaseUser)
                if (user != null) {
                    SignInResult.Success(user = user)
                } else {
                    SignInResult.Failure("Failed to retrieve user data")
                }
            } else {
                SignInResult.Failure("Authentication failed: User data not available.")
            }
        } catch (e: CreateCredentialCancellationException) {
            Timber.e(e)
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Timber.e(e)
            SignInResult.NoCredentials
        } catch (e: CreateCredentialException) {
            Timber.e(e, "Failed to save credentials.")
            SignInResult.Failure("Failed to save credentials: ${e.message}")
        } catch (e: FirebaseAuthException) {
            Timber.e(e, "Firebase authentication failed.")
            SignInResult.Failure("Authentication failed: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "An unexpected error occurred during sign in.")
            SignInResult.Failure("An unexpected error occurred: ${e.message}")
        }
    }

    override suspend fun isPhoneNumberVerified(): Result<Boolean> = safeApiCall(ioDispatcher) {
        currentFirebaseUser?.phoneNumber != null
    }

    override suspend fun isUserAuthenticated(): Result<Boolean> = safeApiCall(ioDispatcher) {
        currentFirebaseUser != null
    }

    override suspend fun isEmailVerified(): Result<Boolean> = safeApiCall(ioDispatcher) {
        currentFirebaseUser?.isEmailVerified ?: false
    }

    override suspend fun resendVerificationEmail(): Result<Unit> = safeApiCall(ioDispatcher) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user found")
        sendEmailVerificationIfNeeded(user)
    }

    override suspend fun logOut(): Result<Unit> = safeApiCall(ioDispatcher) {
        stopUserListener()
        firebaseAuth.signOut()
        _currentUser.value = null // Clear the current user

        val clearCredentialRequest = ClearCredentialStateRequest()
        credentialManager.clearCredentialState(clearCredentialRequest)
    }

    override suspend fun deleteAccount(): Result<Unit> = safeApiCall(ioDispatcher) {
        stopUserListener()
        firebaseAuth.currentUser?.delete()
        _currentUser.value = null // Clear the current user

        val clearCredentialRequest = ClearCredentialStateRequest()
        credentialManager.clearCredentialState(clearCredentialRequest)
    }

    /**
     * Refreshes the current user data from Firestore
     * Useful when user data might have been updated elsewhere
     */
    override suspend fun refreshCurrentUser(): Result<User?> = safeApiCall(ioDispatcher) {
        val firebaseUser = currentFirebaseUser
        if (firebaseUser != null) {
            updateCurrentUser(firebaseUser)
        } else {
            _currentUser.value = null
            null
        }
    }

    /**
     * Updates the current user in the StateFlow after onboarding completion
     * This should be called from FirestoreRepository after successful onboarding
     */
    override suspend fun setCurrentUserAfterOnboarding(user: User) {
        _currentUser.value = user
    }

    private suspend fun sendEmailVerificationIfNeeded(user: FirebaseUser?) {
        if (user != null && !user.isEmailVerified) {
            user.sendEmailVerification().await()
            Timber.d("Verification email sent to ${user.email}")
        }
    }
}