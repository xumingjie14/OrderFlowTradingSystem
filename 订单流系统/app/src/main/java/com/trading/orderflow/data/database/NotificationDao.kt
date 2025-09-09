package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    
    // 通知配置
    @Query("SELECT * FROM notification_config WHERE id = 'default' LIMIT 1")
    suspend fun getNotificationConfig(): NotificationConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationConfig(config: NotificationConfig)
    
    // 通知记录
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationRecord)
    
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getNotificationHistory(limit: Int): List<NotificationRecord>
    
    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY createdAt DESC")
    suspend fun getUnreadNotifications(): List<NotificationRecord>
    
    @Query("SELECT * FROM notifications WHERE type = :type ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getNotificationsByType(type: NotificationType, limit: Int): List<NotificationRecord>
    
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String)
    
    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()
    
    @Query("DELETE FROM notifications WHERE createdAt < :beforeTime")
    suspend fun deleteOldNotifications(beforeTime: Long)
    
    // 价格告警
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceAlert(alert: PriceAlert)
    
    @Query("SELECT * FROM price_alerts WHERE isActive = 1 AND isTriggered = 0")
    suspend fun getActivePriceAlerts(): List<PriceAlert>
    
    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol ORDER BY createdAt DESC")
    suspend fun getPriceAlertsBySymbol(symbol: String): List<PriceAlert>
    
    @Query("UPDATE price_alerts SET isTriggered = 1, triggeredAt = :triggeredAt, currentPrice = :currentPrice WHERE id = :alertId")
    suspend fun updatePriceAlertTriggered(alertId: String, currentPrice: Double, triggeredAt: Long)
    
    @Query("UPDATE price_alerts SET isActive = 0 WHERE id = :alertId")
    suspend fun deactivatePriceAlert(alertId: String)
    
    @Query("DELETE FROM price_alerts WHERE id = :alertId")
    suspend fun deletePriceAlert(alertId: String)
    
    // 统计查询
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    suspend fun getUnreadNotificationCount(): Int
    
    @Query("SELECT COUNT(*) FROM notifications WHERE type = :type AND createdAt >= :since")
    suspend fun getNotificationCountByTypeSince(type: NotificationType, since: Long): Int
    
    // Flow查询
    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY createdAt DESC")
    fun getUnreadNotificationsFlow(): Flow<List<NotificationRecord>>
    
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadNotificationCountFlow(): Flow<Int>
}