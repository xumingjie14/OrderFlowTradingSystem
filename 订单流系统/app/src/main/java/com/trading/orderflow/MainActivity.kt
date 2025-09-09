package com.trading.orderflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.trading.orderflow.domain.auth.AuthService
import com.trading.orderflow.domain.license.LicenseService
import com.trading.orderflow.domain.license.LicenseStatus
import com.trading.orderflow.ui.screens.ActivationScreen
import com.trading.orderflow.ui.screens.LoginScreen
import com.trading.orderflow.ui.theme.OrderFlowTheme
import com.trading.orderflow.ui.navigation.OrderFlowNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var authService: AuthService
    
    @Inject
    lateinit var licenseService: LicenseService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            OrderFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OrderFlowApp(authService, licenseService)
                }
            }
        }
    }
}

@Composable
fun OrderFlowApp(
    authService: AuthService,
    licenseService: LicenseService
) {
    val licenseStatus by licenseService.licenseStatus.collectAsState()
    val isLoggedIn by authService.isLoggedIn.collectAsState()
    
    LaunchedEffect(Unit) {
        licenseService.initialize()
        authService.initialize()
    }
    
    when (licenseStatus) {
        LicenseStatus.CHECKING -> {
            // 显示加载界面
            LoadingScreen()
        }
        LicenseStatus.NEED_ACTIVATION, 
        LicenseStatus.DEVICE_MISMATCH,
        LicenseStatus.EXPIRED -> {
            // 显示激活界面
            ActivationScreen(
                onActivationSuccess = {
                    // 激活成功后重新初始化
                }
            )
        }
        LicenseStatus.ACTIVATED -> {
            // 软件已激活，检查登录状态
            if (isLoggedIn) {
                OrderFlowNavigation()
            } else {
                LoginScreen(
                    onLoginSuccess = {
                        // 登录成功
                    }
                )
            }
        }
        LicenseStatus.ERROR -> {
            // 显示错误界面
            ErrorScreen()
        }
    }
}

@Composable
fun LoadingScreen() {
    // 实现加载界面
}

@Composable
fun ErrorScreen() {
    // 实现错误界面
}