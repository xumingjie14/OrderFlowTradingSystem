package com.trading.orderflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trading.orderflow.data.model.SecurityQuestion
import com.trading.orderflow.domain.license.LicenseService
import com.trading.orderflow.domain.license.LicenseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val licenseService: LicenseService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()
    
    init {
        // 监听许可证状态
        viewModelScope.launch {
            licenseService.licenseStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    isActivated = status == LicenseStatus.ACTIVATED,
                    securityQuestions = licenseService.getSecurityQuestions()
                )
            }
        }
    }
    
    fun verifySerialNumber(serialNumber: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                remainingAttempts = null
            )
            
            val response = licenseService.verifySerialNumber(serialNumber)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (response.success) null else response.message,
                remainingAttempts = response.remainingAttempts,
                isActivated = response.success
            )
        }
    }
    
    fun verifySecurityQuestions(answers: Map<String, String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            
            val response = licenseService.verifySecurityQuestions(answers)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (response.success) null else response.message
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class ActivationUiState(
    val isLoading: Boolean = false,
    val isActivated: Boolean = false,
    val error: String? = null,
    val remainingAttempts: Int? = null,
    val securityQuestions: List<SecurityQuestion> = emptyList()
)