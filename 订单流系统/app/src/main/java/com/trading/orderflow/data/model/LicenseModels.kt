package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 软件许可证信息
@Entity(tableName = "license_info")
data class LicenseInfo(
    @PrimaryKey
    val id: String = "default",
    val serialNumber: String,
    val isActivated: Boolean = false,
    val activatedAt: Long? = null,
    val deviceId: String? = null,
    val activationAttempts: Int = 0,
    val lastAttemptTime: Long? = null,
    val expiryDate: Long? = null, // 如果有期限
    val createdAt: Long = System.currentTimeMillis()
)

// 安全问题配置
data class SecurityQuestion(
    val id: String,
    val question: String,
    val answer: String
)

// 序列号验证请求
data class SerialVerificationRequest(
    val serialNumber: String,
    val deviceId: String
)

// 序列号验证响应
data class SerialVerificationResponse(
    val success: Boolean,
    val message: String,
    val remainingAttempts: Int? = null
)

// 安全问题验证请求
data class SecurityQuestionRequest(
    val answers: Map<String, String> // questionId -> answer
)

// 预设配置
object LicenseConfig {
    const val VALID_SERIAL_NUMBER = "xmjlwy350525199711201614"
    const val MAX_ACTIVATION_ATTEMPTS = 5
    const val ATTEMPT_RESET_HOURS = 24
    
    val SECURITY_QUESTIONS = listOf(
        SecurityQuestion(
            id = "q1",
            question = "许铭杰的电话号码是？",
            answer = "18250282829"
        ),
        SecurityQuestion(
            id = "q2",
            question = "许青山的电话号码是？",
            answer = "13859799638"
        ),
        SecurityQuestion(
            id = "q3",
            question = "林秀珠的电话号码是？",
            answer = "13960438467"
        )
    )
}