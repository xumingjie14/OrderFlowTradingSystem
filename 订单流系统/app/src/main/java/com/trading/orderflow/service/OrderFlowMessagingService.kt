package com.trading.orderflow.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.trading.orderflow.MainActivity
import com.trading.orderflow.R
import com.trading.orderflow.data.model.*
import com.trading.orderflow.domain.service.NotificationService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OrderFlowMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "OrderFlowMessaging"
        private const val CHANNEL_ID = "orderflow_notifications"
        private const val CHANNEL_NAME = "订单流通知"
        private const val CHANNEL_DESCRIPTION = "交易信号和风控提醒"
    }
    
    @Inject
    lateinit var notificationService: NotificationService
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // 处理数据消息
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // 处理通知消息
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(
                title = it.title ?: "订单流系统",
                body = it.body ?: "",
                data = remoteMessage.data
            )
        }
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        
        // 保存新的FCM token
        notificationService.updatePushToken(token)
        
        // 发送token到服务器（如果有的话）
        sendTokenToServer(token)
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return
        val title = data["title"] ?: "订单流系统"
        val message = data["message"] ?: ""
        val symbol = data["symbol"]
        val signalId = data["signalId"]
        val priority = data["priority"] ?: "normal"
        
        // 根据消息类型处理
        when (type) {
            "signal" -> {
                handleSignalNotification(title, message, symbol, signalId, data)
            }
            "risk" -> {
                handleRiskNotification(title, message, data)
            }
            "price_alert" -> {
                handlePriceAlertNotification(title, message, symbol, data)
            }
            "trade" -> {
                handleTradeNotification(title, message, data)
            }
            else -> {
                showNotification(title, message, data)
            }
        }
    }
    
    private fun handleSignalNotification(
        title: String,
        message: String,
        symbol: String?,
        signalId: String?,
        data: Map<String, String>
    ) {
        val notificationId = signalId?.hashCode() ?: System.currentTimeMillis().toInt()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "signals")
            putExtra("signal_id", signalId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 500, 200, 500)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_signal)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(getColor(R.color.signal_color))
            .addAction(
                R.drawable.ic_chart,
                "查看图表",
                createActionIntent("view_chart", symbol)
            )
            .addAction(
                R.drawable.ic_close,
                "忽略",
                createActionIntent("dismiss", signalId)
            )
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
        
        // 记录通知
        notificationService.recordNotification(
            NotificationRecord(
                id = "push_${System.currentTimeMillis()}",
                type = NotificationType.SIGNAL_GENERATED,
                title = title,
                message = message,
                symbol = symbol,
                signalId = signalId,
                priority = NotificationPriority.HIGH,
                isDelivered = true,
                deliveredAt = System.currentTimeMillis()
            )
        )
    }
    
    private fun handleRiskNotification(
        title: String,
        message: String,
        data: Map<String, String>
    ) {
        val notificationId = "risk_${System.currentTimeMillis()}".hashCode()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "dashboard")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(getColor(R.color.risk_color))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
    
    private fun handlePriceAlertNotification(
        title: String,
        message: String,
        symbol: String?,
        data: Map<String, String>
    ) {
        val notificationId = "price_${symbol}_${System.currentTimeMillis()}".hashCode()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "chart")
            putExtra("symbol", symbol)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_price_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(getColor(R.color.price_alert_color))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
    
    private fun handleTradeNotification(
        title: String,
        message: String,
        data: Map<String, String>
    ) {
        val tradeId = data["tradeId"]
        val notificationId = tradeId?.hashCode() ?: System.currentTimeMillis().toInt()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "dashboard")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trade)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
    
    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val notificationId = System.currentTimeMillis().toInt()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
    
    private fun createActionIntent(action: String, data: String?): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            putExtra("action", action)
            putExtra("data", data)
        }
        
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = getColor(R.color.notification_light)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun sendTokenToServer(token: String) {
        // 如果有后端服务，在这里发送token
        Log.d(TAG, "Sending token to server: $token")
    }
}