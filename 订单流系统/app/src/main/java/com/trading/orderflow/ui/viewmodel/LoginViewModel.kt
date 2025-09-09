package com.trading.orderflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trading.orderflow.data.model.LoginRequest
import com.trading.orderflow.data.model.User
import com.trading.orderflow.domain.auth.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    init {
        // 监听认证状态
        viewModelScope.launch {
            authService.isLoggedIn.collect { isLoggedIn ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = isLoggedIn,
                    currentUser = if (isLoggedIn) authService.getCurrentUser() else null
                )
            }
        }
    }
    
    fun login(username: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            
            val request = LoginRequest(
                username = username.trim(),
                password = password,
                rememberMe = rememberMe
            )
            
            val response = authService.login(request)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = response.success,
                currentUser = response.user,
                error = if (response.success) null else response.message
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val error: String? = null
)