package com.trading.orderflow.domain.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.trading.orderflow.data.database.LicenseDao
import com.trading.orderflow.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val licenseDao: LicenseDao
) {
    
    companion object {
        private const val TAG = "LicenseService"
        private const val PREFS_NAME = "license_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _licenseStatus = MutableStateFlow<LicenseStatus>(LicenseStatus.CHECKING)
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()
    
    // 初始化许可证检查
    suspend fun initialize() {
        try {
            val licenseInfo = licenseDao.getLicenseInfo()
            
            if (licenseInfo == null) {
                // 首次安装，需要激活
                _licenseStatus.value = LicenseStatus.NEED_ACTIVATION
            } else if (licenseInfo.isActivated) {
                // 验证设备ID
                val currentDeviceId = getDeviceId()
                if (licenseInfo.deviceId == currentDeviceId) {
                    // 检查是否过期
                    if (licenseInfo.expiryDate == null || licenseInfo.expiryDate > System.currentTimeMillis()) {
                        _licenseStatus.value = LicenseStatus.ACTIVATED
                    } else {
                        _licenseStatus.value = LicenseStatus.EXPIRED
                    }
                } else {
                    // 设备不匹配，需要重新激活
                    _licenseStatus.value = LicenseStatus.DEVICE_MISMATCH
                }
            } else {
                _licenseStatus.value = LicenseStatus.NEED_ACTIVATION
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "License initialization error: ${e.message}")
            _licenseStatus.value = LicenseStatus.ERROR
        }
    }
    
    // 验证序列号
    suspend fun verifySerialNumber(serialNumber: String): SerialVerificationResponse {
        try {
            val currentTime = System.currentTimeMillis()
            val deviceId = getDeviceId()
            
            // 获取当前许可证信息
            val existingLicense = licenseDao.getLicenseInfo()
            
            // 检查激活尝试次数
            if (existingLicense != null) {
                val hoursSinceLastAttempt = existingLicense.lastAttemptTime?.let {
                    (currentTime - it) / (1000 * 60 * 60)
                } ?: Long.MAX_VALUE
                
                if (existingLicense.activationAttempts >= LicenseConfig.MAX_ACTIVATION_ATTEMPTS && 
                    hoursSinceLastAttempt < LicenseConfig.ATTEMPT_RESET_HOURS) {
                    return SerialVerificationResponse(
                        success = false,
                        message = "激活尝试次数过多，请${LicenseConfig.ATTEMPT_RESET_HOURS}小时后重试",
                        remainingAttempts = 0
                    )
                }
            }
            
            // 验证序列号
            if (serialNumber.trim() == LicenseConfig.VALID_SERIAL_NUMBER) {
                // 序列号正确，激活软件
                val licenseInfo = LicenseInfo(
                    serialNumber = serialNumber,
                    isActivated = true,
                    activatedAt = currentTime,
                    deviceId = deviceId,
                    activationAttempts = 0,
                    lastAttemptTime = currentTime
                )
                
                licenseDao.insertLicenseInfo(licenseInfo)
                _licenseStatus.value = LicenseStatus.ACTIVATED
                
                Log.d(TAG, "Software activated successfully")
                
                return SerialVerificationResponse(
                    success = true,
                    message = "软件激活成功！"
                )
            } else {
                // 序列号错误，记录尝试
                val attempts = (existingLicense?.activationAttempts ?: 0) + 1
                val remainingAttempts = maxOf(0, LicenseConfig.MAX_ACTIVATION_ATTEMPTS - attempts)
                
                val licenseInfo = existingLicense?.copy(
                    activationAttempts = attempts,
                    lastAttemptTime = currentTime
                ) ?: LicenseInfo(
                    serialNumber = "",
                    isActivated = false,
                    activationAttempts = attempts,
                    lastAttemptTime = currentTime,
                    deviceId = deviceId
                )
                
                licenseDao.insertLicenseInfo(licenseInfo)
                
                return SerialVerificationResponse(
                    success = false,
                    message = "序列号错误，剩余尝试次数：$remainingAttempts",
                    remainingAttempts = remainingAttempts
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Serial verification error: ${e.message}")
            return SerialVerificationResponse(
                success = false,
                message = "验证失败：${e.message}"
            )
        }
    }
    
    // 验证安全问题
    suspend fun verifySecurityQuestions(answers: Map<String, String>): SerialVerificationResponse {
        try {
            var correctAnswers = 0
            
            for (question in LicenseConfig.SECURITY_QUESTIONS) {
                val userAnswer = answers[question.id]?.trim() ?: ""
                if (userAnswer == question.answer) {
                    correctAnswers++
                }
            }
            
            if (correctAnswers == LicenseConfig.SECURITY_QUESTIONS.size) {
                // 所有问题回答正确，重置激活尝试次数
                val existingLicense = licenseDao.getLicenseInfo()
                if (existingLicense != null) {
                    val resetLicense = existingLicense.copy(
                        activationAttempts = 0,
                        lastAttemptTime = System.currentTimeMillis()
                    )
                    licenseDao.insertLicenseInfo(resetLicense)
                }
                
                return SerialVerificationResponse(
                    success = true,
                    message = "安全验证通过，激活尝试次数已重置，请重新输入序列号"
                )
            } else {
                return SerialVerificationResponse(
                    success = false,
                    message = "安全问题回答错误，正确答案：${correctAnswers}/${LicenseConfig.SECURITY_QUESTIONS.size}"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Security question verification error: ${e.message}")
            return SerialVerificationResponse(
                success = false,
                message = "验证失败：${e.message}"
            )
        }
    }
    
    // 获取许可证状态
    fun getLicenseStatus(): LicenseStatus = _licenseStatus.value
    
    // 检查是否已激活
    fun isActivated(): Boolean = _licenseStatus.value == LicenseStatus.ACTIVATED
    
    // 获取安全问题
    fun getSecurityQuestions(): List<SecurityQuestion> = LicenseConfig.SECURITY_QUESTIONS
    
    // 获取设备ID
    private fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            // 生成设备ID
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val deviceInfo = "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.SERIAL}"
            
            deviceId = hashString("$androidId$deviceInfo")
            
            // 保存设备ID
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
    
    private fun hashString(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    // 重置许可证（仅用于测试）
    suspend fun resetLicense() {
        licenseDao.deleteLicenseInfo()
        _licenseStatus.value = LicenseStatus.NEED_ACTIVATION
    }
}

enum class LicenseStatus {
    CHECKING,           // 检查中
    NEED_ACTIVATION,    // 需要激活
    ACTIVATED,          // 已激活
    EXPIRED,            // 已过期
    DEVICE_MISMATCH,    // 设备不匹配
    ERROR               // 错误
}