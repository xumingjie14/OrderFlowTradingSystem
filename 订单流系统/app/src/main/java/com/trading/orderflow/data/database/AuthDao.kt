package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.User
import com.trading.orderflow.data.model.UserSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {
    
    // 用户管理
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
    
    @Update
    suspend fun updateUser(user: User)
    
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?
    
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>
    
    @Query("SELECT * FROM users WHERE isActive = 1")
    suspend fun getActiveUsers(): List<User>
    
    @Query("UPDATE users SET isActive = :isActive WHERE username = :username")
    suspend fun updateUserActiveStatus(username: String, isActive: Boolean)
    
    // 会话管理
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UserSession)
    
    @Query("SELECT * FROM user_sessions WHERE sessionId = :sessionId AND isActive = 1 LIMIT 1")
    suspend fun getActiveSession(sessionId: String): UserSession?
    
    @Query("SELECT * FROM user_sessions WHERE username = :username AND isActive = 1 ORDER BY loginTime DESC")
    suspend fun getActiveSessionsByUser(username: String): List<UserSession>
    
    @Query("UPDATE user_sessions SET isActive = 0 WHERE sessionId = :sessionId")
    suspend fun deactivateSession(sessionId: String)
    
    @Query("UPDATE user_sessions SET isActive = 0 WHERE username = :username")
    suspend fun deactivateAllUserSessions(username: String)
    
    @Query("DELETE FROM user_sessions WHERE expiryTime < :currentTime")
    suspend fun deleteExpiredSessions(currentTime: Long)
    
    // Flow查询
    @Query("SELECT * FROM users WHERE username = :username")
    fun getUserFlow(username: String): Flow<User?>
}