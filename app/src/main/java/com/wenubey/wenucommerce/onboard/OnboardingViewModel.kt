package com.wenubey.wenucommerce.onboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProfileRepository
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.convertMillisToDate
import com.wenubey.wenucommerce.onboard.util.toDomainModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    dispatcherProvider: DispatcherProvider,

    ) : ViewModel() {
    private val ioDispatcher = dispatcherProvider.io()
    private val mainDispatcher = dispatcherProvider.main()

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()


    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.OnDateOfBirthSelected -> dateOfBirthSelect(action.dateOfBirthMillis)
            is OnboardingAction.OnGenderSelected -> genderSelect(action.gender)
            is OnboardingAction.OnNameChange -> nameChange(action.name)
            is OnboardingAction.OnPhoneNumberChange -> phoneNumberChange(action.phoneNumber)
            is OnboardingAction.OnSurnameChange -> surnameChanged(action.surname)
            is OnboardingAction.OnAddressChange -> addressChange(action.address)
            is OnboardingAction.OnOnboardingComplete -> onBoardingComplete()
            is OnboardingAction.OnPhotoUrlChange -> photoUrlChange(action.photoUrl)
        }
        validateForm()
    }

    private fun photoUrlChange(photoUrl: Uri) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(photoUrl = photoUrl) }
    }

    private fun addressChange(address: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(address = address) }
    }

    private fun dateOfBirthSelect(millis: Long?) =
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(dateOfBirth = millis?.convertMillisToDate()) }
        }

    private fun genderSelect(gender: GenderUiModel) =
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(gender = gender) }
        }

    private fun nameChange(name: String) =
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(name = name,
                nameError = name.isBlank()) }
        }

    private fun phoneNumberChange(phoneNumber: String) =
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(phoneNumber = phoneNumber,
                phoneNumberError = phoneNumber.isBlank()) }
        }

    private fun surnameChanged(surname: String) =
        viewModelScope.launch(mainDispatcher) {
            _state.update {
                it.copy(
                    surname = surname,
                    surnameError = surname.isBlank(),
                )
            }
        }


    private fun onBoardingComplete() =
        viewModelScope.launch(ioDispatcher) {
            profileRepository.onboarding(
                name = _state.value.name,
                surname = _state.value.surname,
                phoneNumber = _state.value.phoneNumber,
                dateOfBirth = _state.value.dateOfBirth ?: "",
                gender = _state.value.gender.toDomainModel(),
                address = _state.value.address,
                photoUrl = _state.value.photoUrl
            )
        }

    private fun validateForm() {
        val isFormValid = _state.value.name.isNotBlank() &&
                _state.value.surname.isNotBlank() &&
                _state.value.phoneNumber.isNotBlank()

        _state.update {
            it.copy(
                isNextButtonEnabled = isFormValid,
            )
        }
    }

}




