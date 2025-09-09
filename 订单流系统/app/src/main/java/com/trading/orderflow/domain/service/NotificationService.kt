package com.trading.orderflow.domain.service

import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.trading.orderflow.R
import com.trading.orderflow.data.database.NotificationDao
import com.trading.orderflow.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao
) {
    
    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "orderflow_notifications"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    // 通知模板
    private val notificationTemplates = mapOf(
        NotificationType.SIGNAL_GENERATED to NotificationTemplate(
            type = NotificationType.SIGNAL_GENERATED,
            titleTemplate = "交易信号：{symbol}",
            messageTemplate = "{signalType} 信号，强度：{strength}，价格：{price}",
            priority = NotificationPriority.HIGH
        ),
        NotificationType.RISK_WARNING to NotificationTemplate(
            type = NotificationType.RISK_WARNING,
            titleTemplate = "风险警告",
            messageTemplate = "{message}",
            priority = NotificationPriority.URGENT
        ),
        NotificationType.TRADE_OPENED to NotificationTemplate(
            type = NotificationType.TRADE_OPENED,
            titleTemplate = "开仓成功",
            messageTemplate = "{symbol} {side} {quantity}@{price}",
            priority = NotificationPriority.NORMAL
        ),
        NotificationType.TRADE_CLOSED to NotificationTemplate(
            type = NotificationType.TRADE_CLOSED,
            titleTemplate = "平仓完成",
            messageTemplate = "{symbol} 盈亏：{pnl}",
            priority = NotificationPriority.NORMAL
        ),
        NotificationType.PRICE_ALERT to NotificationTemplate(
            type = NotificationType.PRICE_ALERT,
            titleTemplate = "价格告警：{symbol}",
            messageTemplate = "价格{alertType}{targetPrice}，当前价格：{currentPrice}",
            priority = NotificationPriority.HIGH
        )
    )
    
    // 发送信号通知
    suspend fun sendSignalNotification(
        signal: TradingSignal,
        config: NotificationConfig? = null
    ) {
        val notificationConfig = config ?: getNotificationConfig()
        
        if (!notificationConfig.enableSignalNotifications) return
        
        // 检查信号强度过滤
        val minStrength = SignalStrength.valueOf(notificationConfig.signalMinStrength)
        if (signal.strength.ordinal < minStrength.ordinal) return
        
        // 检查通知频率限制
        if (!checkNotificationRateLimit(NotificationType.SIGNAL_GENERATED)) return
        
        val template = notificationTemplates[NotificationType.SIGNAL_GENERATED]!!
        val title = template.titleTemplate.replace("{symbol}", signal.symbol)
        val message = template.messageTemplate
            .replace("{signalType}", signal.signalType.name)
            .replace("{strength}", signal.strength.name)
            .replace("{price}", String.format("%.2f", signal.price))
        
        val notification = NotificationRecord(
            id = "signal_${signal.id}",
            type = NotificationType.SIGNAL_GENERATED,
            title = title,
            message = message,
            symbol = signal.symbol,
            signalId = signal.id,
            priority = NotificationPriority.HIGH
        )
        
        showLocalNotification(notification, notificationConfig)
        recordNotification(notification)
        
        // 发送推送通知
        if (notificationConfig.enablePushNotifications) {
            sendPushNotification(
                PushNotificationMessage(
                    title = title,
                    body = message,
                    data = mapOf(
                        "type" to "signal",
                        "symbol" to signal.symbol,
                        "signalId" to signal.id,
                        "strength" to signal.strength.name
                    )
                )
            )
        }
    }
    
    // 发送风险通知
    suspend fun sendRiskNotification(
        riskEvent: RiskEvent,
        config: NotificationConfig? = null
    ) {
        val notificationConfig = config ?: getNotificationConfig()
        
        if (!notificationConfig.enableRiskNotifications) return
        
        val template = notificationTemplates[NotificationType.RISK_WARNING]!!
        val title = template.titleTemplate
        val message = template.messageTemplate.replace("{message}", riskEvent.message)
        
        val notification = NotificationRecord(
            id = "risk_${riskEvent.id}",
            type = NotificationType.RISK_WARNING,
            title = title,
            message = message,
            priority = NotificationPriority.URGENT
        )
        
        showLocalNotification(notification, notificationConfig)
        recordNotification(notification)
        
        // 紧急风险事件总是发送推送
        if (riskEvent.severity == RiskSeverity.CRITICAL) {
            sendPushNotification(
                PushNotificationMessage(
                    title = title,
                    body = message,
                    priority = "high",
                    data = mapOf(
                        "type" to "risk",
                        "severity" to riskEvent.severity.name,
                        "eventType" to riskEvent.eventType.name
                    )
                )
            )
        }
    }
    
    // 发送价格告警通知
    suspend fun sendPriceAlertNotification(
        alert: PriceAlert,
        currentPrice: Double,
        config: NotificationConfig? = null
    ) {
        val notificationConfig = config ?: getNotificationConfig()
        
        if (!notificationConfig.enablePriceAlerts) return
        
        val template = notificationTemplates[NotificationType.PRICE_ALERT]!!
        val title = template.titleTemplate.replace("{symbol}", alert.symbol)
        val alertTypeText = when (alert.alertType) {
            PriceAlertType.ABOVE -> "突破"
            PriceAlertType.BELOW -> "跌破"
            PriceAlertType.CHANGE_PERCENT_UP -> "涨幅超过"
            PriceAlertType.CHANGE_PERCENT_DOWN -> "跌幅超过"
        }
        val message = template.messageTemplate
            .replace("{alertType}", alertTypeText)
            .replace("{targetPrice}", String.format("%.2f", alert.targetPrice))
            .replace("{currentPrice}", String.format("%.2f", currentPrice))
        
        val notification = NotificationRecord(
            id = "price_alert_${alert.id}",
            type = NotificationType.PRICE_ALERT,
            title = title,
            message = message,
            symbol = alert.symbol,
            priority = NotificationPriority.HIGH
        )
        
        showLocalNotification(notification, notificationConfig)
        recordNotification(notification)
        
        // 更新告警状态
        updatePriceAlertTriggered(alert.id, currentPrice)
    }
    
    // 显示本地通知
    private fun showLocalNotification(
        notification: NotificationRecord,
        config: NotificationConfig
    ) {
        // 检查静音时间
        if (config.quietHoursEnabled && isInQuietHours(config)) {
            return
        }
        
        val notificationId = notification.id.hashCode()
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(getNotificationIcon(notification.type))
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setPriority(getNotificationPriority(notification.priority))
            .setAutoCancel(true)
        
        // 设置声音
        if (shouldPlaySound(notification.type, config)) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        
        // 设置振动
        if (shouldVibrate(notification.type, config)) {
            val vibrationPattern = getVibrationPattern(notification.type)
            builder.setVibrate(vibrationPattern)
            
            // Android O+ 使用VibrationEffect
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        }
        
        // 设置颜色
        builder.setColor(getNotificationColor(notification.type))
        
        // 显示通知
        notificationManager.notify(notificationId, builder.build())
        
        Log.d(TAG, "Local notification shown: ${notification.title}")
    }
    
    // 发送推送通知
    private fun sendPushNotification(message: PushNotificationMessage) {
        scope.launch {
            try {
                // 这里可以调用后端API发送推送
                // 或者使用Firebase Admin SDK
                Log.d(TAG, "Push notification sent: ${message.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send push notification: ${e.message}")
            }
        }
    }
    
    // 记录通知
    suspend fun recordNotification(notification: NotificationRecord) {
        notificationDao.insertNotification(notification.copy(
            isDelivered = true,
            deliveredAt = System.currentTimeMillis()
        ))
    }
    
    // 获取通知配置
    suspend fun getNotificationConfig(): NotificationConfig {
        return notificationDao.getNotificationConfig() ?: NotificationConfig()
    }
    
    // 更新通知配置
    suspend fun updateNotificationConfig(config: NotificationConfig) {
        notificationDao.insertNotificationConfig(config)
    }
    
    // 更新推送token
    fun updatePushToken(token: String) {
        scope.launch {
            val config = getNotificationConfig()
            updateNotificationConfig(config.copy(pushToken = token))
        }
    }
    
    // 获取通知历史
    suspend fun getNotificationHistory(limit: Int = 100): List<NotificationRecord> {
        return notificationDao.getNotificationHistory(limit)
    }
    
    // 标记通知为已读
    suspend fun markNotificationAsRead(notificationId: String) {
        notificationDao.markAsRead(notificationId)
    }
    
    // 清除所有通知
    fun clearAllNotifications() {
        notificationManager.cancelAll()
    }
    
    // 创建价格告警
    suspend fun createPriceAlert(alert: PriceAlert) {
        notificationDao.insertPriceAlert(alert)
    }
    
    // 获取活跃的价格告警
    suspend fun getActivePriceAlerts(): List<PriceAlert> {
        return notificationDao.getActivePriceAlerts()
    }
    
    // 更新价格告警触发状态
    private suspend fun updatePriceAlertTriggered(alertId: String, currentPrice: Double) {
        notificationDao.updatePriceAlertTriggered(alertId, currentPrice, System.currentTimeMillis())
    }
    
    // 辅助方法
    private fun checkNotificationRateLimit(type: NotificationType): Boolean {
        // 实现通知频率限制逻辑
        return true
    }
    
    private fun isInQuietHours(config: NotificationConfig): Boolean {
        // 实现静音时间检查逻辑
        return false
    }
    
    private fun shouldPlaySound(type: NotificationType, config: NotificationConfig): Boolean {
        return when (type) {
            NotificationType.SIGNAL_GENERATED -> config.signalSoundEnabled
            NotificationType.RISK_WARNING -> config.riskSoundEnabled
            NotificationType.PRICE_ALERT -> config.priceAlertSoundEnabled
            else -> true
        }
    }
    
    private fun shouldVibrate(type: NotificationType, config: NotificationConfig): Boolean {
        return when (type) {
            NotificationType.SIGNAL_GENERATED -> config.signalVibrationEnabled
            NotificationType.RISK_WARNING -> config.riskVibrationEnabled
            NotificationType.PRICE_ALERT -> config.priceAlertVibrationEnabled
            else -> true
        }
    }
    
    private fun getNotificationIcon(type: NotificationType): Int {
        return when (type) {
            NotificationType.SIGNAL_GENERATED -> R.drawable.ic_signal
            NotificationType.RISK_WARNING -> R.drawable.ic_warning
            NotificationType.PRICE_ALERT -> R.drawable.ic_price_alert
            NotificationType.TRADE_OPENED, NotificationType.TRADE_CLOSED -> R.drawable.ic_trade
            else -> R.drawable.ic_notification
        }
    }
    
    private fun getNotificationPriority(priority: NotificationPriority): Int {
        return when (priority) {
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.URGENT -> NotificationCompat.PRIORITY_MAX
        }
    }
    
    private fun getVibrationPattern(type: NotificationType): LongArray {
        return when (type) {
            NotificationType.SIGNAL_GENERATED -> longArrayOf(0, 500, 200, 500)
            NotificationType.RISK_WARNING -> longArrayOf(0, 1000, 500, 1000, 500, 1000)
            NotificationType.PRICE_ALERT -> longArrayOf(0, 300, 200, 300)
            else -> longArrayOf(0, 250, 250, 250)
        }
    }
    
    private fun getNotificationColor(type: NotificationType): Int {
        return when (type) {
            NotificationType.SIGNAL_GENERATED -> context.getColor(R.color.signal_color)
            NotificationType.RISK_WARNING -> context.getColor(R.color.risk_color)
            NotificationType.PRICE_ALERT -> context.getColor(R.color.price_alert_color)
            else -> context.getColor(R.color.notification_default)
        }
    }
}