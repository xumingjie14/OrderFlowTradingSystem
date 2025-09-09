package com.trading.orderflow.domain.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.trading.orderflow.data.database.AuthDao
import com.trading.orderflow.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authDao: AuthDao
) {
    
    companion object {
        private const val TAG = "AuthService"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val SESSION_DURATION = 24 * 60 * 60 * 1000L // 24小时
        private const val REMEMBER_DURATION = 30 * 24 * 60 * 60 * 1000L // 30天
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    // 初始化认证服务
    suspend fun initialize() {
        // 初始化默认用户
        initializeDefaultUsers()
        
        // 检查已保存的会话
        checkSavedSession()
    }
    
    // 用户登录
    suspend fun login(request: LoginRequest): LoginResponse {
        try {
            val user = authDao.getUserByUsername(request.username)
            if (user == null) {
                return LoginResponse(
                    success = false,
                    message = "用户名不存在"
                )
            }
            
            if (!user.isActive) {
                return LoginResponse(
                    success = false,
                    message = "账户已被禁用"
                )
            }
            
            val passwordHash = hashPassword(request.password)
            if (user.passwordHash != passwordHash) {
                return LoginResponse(
                    success = false,
                    message = "密码错误"
                )
            }
            
            // 创建会话
            val sessionId = UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + if (request.rememberMe) REMEMBER_DURATION else SESSION_DURATION
            
            val session = UserSession(
                sessionId = sessionId,
                username = user.username,
                loginTime = currentTime,
                expiryTime = expiryTime,
                deviceInfo = getDeviceInfo(),
                isActive = true
            )
            
            // 保存会话
            authDao.insertSession(session)
            
            // 更新用户最后登录时间
            authDao.updateUser(user.copy(lastLoginTime = currentTime))
            
            // 保存到本地存储
            saveSession(sessionId, user.username, request.rememberMe)
            
            // 更新状态
            _currentUser.value = user
            _isLoggedIn.value = true
            
            Log.d(TAG, "User ${user.username} logged in successfully")
            
            return LoginResponse(
                success = true,
                user = user,
                sessionId = sessionId,
                message = "登录成功",
                expiryTime = expiryTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}")
            return LoginResponse(
                success = false,
                message = "登录失败: ${e.message}"
            )
        }
    }
    
    // 用户登出
    suspend fun logout() {
        try {
            val sessionId = prefs.getString(KEY_SESSION_ID, null)
            if (sessionId != null) {
                // 使会话失效
                authDao.deactivateSession(sessionId)
            }
            
            // 清除本地存储
            clearSession()
            
            // 更新状态
            _currentUser.value = null
            _isLoggedIn.value = false
            
            Log.d(TAG, "User logged out successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Logout error: ${e.message}")
        }
    }
    
    // 检查会话有效性
    suspend fun validateSession(): Boolean {
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return false
        
        val session = authDao.getActiveSession(sessionId)
        if (session == null || session.expiryTime < System.currentTimeMillis()) {
            clearSession()
            _currentUser.value = null
            _isLoggedIn.value = false
            return false
        }
        
        // 获取用户信息
        val user = authDao.getUserByUsername(session.username)
        if (user == null || !user.isActive) {
            clearSession()
            _currentUser.value = null
            _isLoggedIn.value = false
            return false
        }
        
        _currentUser.value = user
        _isLoggedIn.value = true
        return true
    }
    
    // 获取当前用户
    fun getCurrentUser(): User? = _currentUser.value
    
    // 检查是否已登录
    fun isUserLoggedIn(): Boolean = _isLoggedIn.value
    
    // 检查用户权限
    fun hasPermission(requiredRole: UserRole): Boolean {
        val user = getCurrentUser() ?: return false
        return when (requiredRole) {
            UserRole.GUEST -> true
            UserRole.USER -> user.role == UserRole.USER || user.role == UserRole.ADMIN
            UserRole.ADMIN -> user.role == UserRole.ADMIN
        }
    }
    
    // 私有方法
    private suspend fun initializeDefaultUsers() {
        // 检查是否已初始化
        val existingUsers = authDao.getAllUsers()
        if (existingUsers.isEmpty()) {
            // 插入默认用户
            DefaultUsers.users.forEach { user ->
                authDao.insertUser(user)
            }
            Log.d(TAG, "Default users initialized")
        }
    }
    
    private suspend fun checkSavedSession() {
        val sessionId = prefs.getString(KEY_SESSION_ID, null)
        if (sessionId != null) {
            validateSession()
        }
    }
    
    private fun saveSession(sessionId: String, username: String, rememberMe: Boolean) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .apply()
    }
    
    private fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_REMEMBER_ME)
            .apply()
    }
    
    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun getDeviceInfo(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }
}