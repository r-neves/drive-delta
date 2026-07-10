package app.drivedelta.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(signedIn = authRepository.isSignedIn))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Called when the Google Sign-In sheet is launched. */
    fun onSignInLaunched() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    }

    /** Called with the Google ID token returned by the Sign-In flow. */
    fun onGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.signInWithGoogle(idToken)
                .onSuccess { _uiState.update { it.copy(isLoading = false, signedIn = true) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    /** Called when the Google Sign-In flow itself failed or was cancelled. */
    fun onSignInError(message: String?) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    /** Clears a shown error so the snackbar isn't re-shown on recomposition. */
    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
