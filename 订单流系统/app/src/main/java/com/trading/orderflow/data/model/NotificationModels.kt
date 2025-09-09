package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 通知配置
@Entity(tableName = "notification_config")
data class NotificationConfig(
    @PrimaryKey
    val id: String = "default",
    
    // 信号通知设置
    val enableSignalNotifications: Boolean = true,
    val signalMinStrength: String = "MEDIUM", // WEAK, MEDIUM, STRONG
    val signalSoundEnabled: Boolean = true,
    val signalVibrationEnabled: Boolean = true,
    
    // 风控通知设置
    val enableRiskNotifications: Boolean = true,
    val riskSoundEnabled: Boolean = true,
    val riskVibrationEnabled: Boolean = true,
    
    // 价格告警设置
    val enablePriceAlerts: Boolean = true,
    val priceAlertSoundEnabled: Boolean = true,
    val priceAlertVibrationEnabled: Boolean = true,
    
    // 推送通知设置
    val enablePushNotifications: Boolean = true,
    val pushToken: String? = null,
    
    // 通知时间设置
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "08:00",
    
    // 通知频率限制
    val maxNotificationsPerHour: Int = 10,
    val duplicateNotificationDelay: Long = 5 * 60 * 1000L, // 5分钟
    
    val updatedAt: Long = System.currentTimeMillis()
)

// 通知记录
@Entity(tableName = "notifications")
data class NotificationRecord(
    @PrimaryKey
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val data: String? = null, // JSON格式的额外数据
    
    // 通知状态
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val deliveredAt: Long? = null,
    
    // 通知优先级
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val category: String? = null,
    
    // 关联信息
    val symbol: String? = null,
    val signalId: String? = null,
    val tradeId: String? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)

enum class NotificationType {
    SIGNAL_GENERATED, // 信号生成
    SIGNAL_EXPIRED, // 信号过期
    TRADE_OPENED, // 开仓
    TRADE_CLOSED, // 平仓
    STOP_LOSS_HIT, // 止损触发
    TAKE_PROFIT_HIT, // 止盈触发
    RISK_WARNING, // 风险警告
    RISK_LIMIT_EXCEEDED, // 风险超限
    COOLDOWN_ACTIVATED, // 冷却期激活
    PRICE_ALERT, // 价格告警
    MARKET_NEWS, // 市场新闻
    SYSTEM_UPDATE, // 系统更新
    CONNECTION_LOST, // 连接丢失
    CONNECTION_RESTORED // 连接恢复
}

enum class NotificationPriority {
    LOW, NORMAL, HIGH, URGENT
}

// 价格告警
@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val alertType: PriceAlertType,
    val targetPrice: Double,
    val currentPrice: Double,
    val isActive: Boolean = true,
    val isTriggered: Boolean = false,
    val triggeredAt: Long? = null,
    
    // 告警设置
    val message: String? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    
    // 重复设置
    val isRepeating: Boolean = false,
    val repeatInterval: Long = 0, // 毫秒
    val maxRepeats: Int = 1,
    val currentRepeats: Int = 0,
    
    val createdAt: Long = System.currentTimeMillis()
)

enum class PriceAlertType {
    ABOVE, // 价格高于目标价
    BELOW, // 价格低于目标价
    CHANGE_PERCENT_UP, // 涨幅超过百分比
    CHANGE_PERCENT_DOWN // 跌幅超过百分比
}

// 通知模板
data class NotificationTemplate(
    val type: NotificationType,
    val titleTemplate: String,
    val messageTemplate: String,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

// Firebase推送消息
data class PushNotificationMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val priority: String = "high",
    val sound: String = "default",
    val badge: Int? = null,
    val clickAction: String? = null
)

// 本地通知数据
data class LocalNotificationData(
    val id: Int,
    val title: String,
    val message: String,
    val channelId: String,
    val priority: Int,
    val autoCancel: Boolean = true,
    val ongoing: Boolean = false,
    val soundUri: String? = null,
    val vibrationPattern: LongArray? = null,
    val ledColor: Int? = null,
    val largeIcon: String? = null,
    val bigText: String? = null,
    val actions: List<NotificationAction> = emptyList()
)

data class NotificationAction(
    val id: String,
    val title: String,
    val icon: String? = null,
    val intent: String? = null
)