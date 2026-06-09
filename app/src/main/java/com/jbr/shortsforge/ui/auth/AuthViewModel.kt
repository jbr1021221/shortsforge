package com.jbr.shortsforge.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.jbr.shortsforge.data.firebase.CloudSync
import com.jbr.shortsforge.data.firebase.FirebaseAuthRepository
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.engine.ProfileScheduleRestorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val currentUser: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val cloudSync: CloudSync,
    private val settingsRepository: AppSettingsRepository,
    private val profileRepository: ProfileRepository,
    private val profileScheduleRestorer: ProfileScheduleRestorer
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(currentUser = authRepository.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isSignedIn: Boolean get() = authRepository.currentUser != null

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.signIn(email, password)
                .onSuccess { user ->
                    val restored = cloudSync.restoreFromCloud(settingsRepository, profileRepository)
                    ensureDefaultProfile(user)
                    if (!restored) cloudSync.backupNow()
                    cloudSync.start()
                    profileScheduleRestorer.restoreEnabledSchedules()
                    _uiState.value = _uiState.value.copy(currentUser = user, isLoading = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.register(email, password)
                .onSuccess { user ->
                    ensureDefaultProfile(user)
                    cloudSync.backupNow()
                    cloudSync.start()
                    profileScheduleRestorer.restoreEnabledSchedules()
                    _uiState.value = _uiState.value.copy(currentUser = user, isLoading = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState(currentUser = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun ensureDefaultProfile(user: FirebaseUser) {
        if (profileRepository.getProfileCount() > 0) return
        val emailName = user.email
            ?.substringBefore("@")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?.takeIf { it.isNotBlank() }
        profileRepository.createProfile(emailName ?: "Default Profile")
    }
}
