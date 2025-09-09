package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

// 用户账户
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val email: String? = null,
    val phone: String? = null,
    val role: UserRole = UserRole.USER,
    val isActive: Boolean = true,
    val lastLoginTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    ADMIN, USER, GUEST
}

// 登录会话
@Entity(tableName = "user_sessions")
data class UserSession(
    @PrimaryKey
    val sessionId: String,
    val username: String,
    val loginTime: Long,
    val expiryTime: Long,
    val deviceInfo: String? = null,
    val ipAddress: String? = null,
    val isActive: Boolean = true
)

// 登录请求
data class LoginRequest(
    val username: String,
    val password: String,
    val rememberMe: Boolean = false
)

// 登录响应
data class LoginResponse(
    val success: Boolean,
    val user: User? = null,
    val sessionId: String? = null,
    val message: String? = null,
    val expiryTime: Long? = null
)

// 预设用户数据
object DefaultUsers {
    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    val users = listOf(
        User(
            username = "xumingjie2025",
            passwordHash = hashPassword("xmj624450"),
            displayName = "徐明杰",
            email = "mingjiefacai@gmail.com",
            phone = "19250282829",
            role = UserRole.ADMIN
        ),
        User(
            username = "zhengjinbin",
            passwordHash = hashPassword("zhengjinbin88888"),
            displayName = "郑金斌",
            role = UserRole.USER
        ),
        User(
            username = "xulongbin",
            passwordHash = hashPassword("xulongbin88888"),
            displayName = "徐龙斌",
            role = UserRole.USER
        )
    )
}