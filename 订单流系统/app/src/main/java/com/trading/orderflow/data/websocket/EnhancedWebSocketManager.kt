package com.trading.orderflow.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    
    companion object {
        private const val TAG = "EnhancedWebSocketManager"
        private const val BASE_WS_URL = "wss://stream.binance.com:9443/ws"
        private const val PING_INTERVAL = 30000L // 30秒心跳
        private const val RECONNECT_DELAY_BASE = 1000L // 基础重连延迟1秒
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val MAX_SUBSCRIPTIONS_PER_CONNECTION = 200
        private const val CONNECTION_TIMEOUT = 10000L // 10秒连接超时
    }
    
    // 连接池管理
    private val connections = ConcurrentHashMap<String, WebSocketConnection>()
    private val subscriptions = ConcurrentHashMap<String, SubscriptionInfo>()
    private val connectionCounter = AtomicInteger(0)
    private val isShuttingDown = AtomicBoolean(false)
    
    // 数据流
    private val _klineFlow = MutableSharedFlow<BinanceWebSocketKline>(replay = 0, extraBufferCapacity = 1000)
    val klineFlow: SharedFlow<BinanceWebSocketKline> = _klineFlow.asSharedFlow()
    
    private val _aggTradeFlow = MutableSharedFlow<BinanceAggTrade>(replay = 0, extraBufferCapacity = 1000)
    val aggTradeFlow: SharedFlow<BinanceAggTrade> = _aggTradeFlow.asSharedFlow()
    
    private val _depthFlow = MutableSharedFlow<BinanceDepthUpdate>(replay = 0, extraBufferCapacity = 1000)
    val depthFlow: SharedFlow<BinanceDepthUpdate> = _depthFlow.asSharedFlow()
    
    private val _connectionStateFlow = MutableSharedFlow<ConnectionStateUpdate>()
    val connectionStateFlow: SharedFlow<ConnectionStateUpdate> = _connectionStateFlow.asSharedFlow()
    
    private val _statisticsFlow = MutableStateFlow(WebSocketStatistics())
    val statisticsFlow: StateFlow<WebSocketStatistics> = _statisticsFlow.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 启动WebSocket管理器
    fun start() {
        if (isShuttingDown.get()) {
            Log.w(TAG, "Cannot start - manager is shutting down")
            return
        }
        
        Log.d(TAG, "Starting Enhanced WebSocket Manager")
        startStatisticsUpdater()
        startConnectionMonitor()
    }
    
    // 停止WebSocket管理器
    fun shutdown() {
        Log.d(TAG, "Shutting down Enhanced WebSocket Manager")
        isShuttingDown.set(true)
        
        // 关闭所有连接
        connections.values.forEach { connection ->
            connection.close()
        }
        connections.clear()
        subscriptions.clear()
        
        scope.cancel()
    }
    
    // 订阅K线数据
    fun subscribeKline(symbol: String, interval: String): Boolean {
        val stream = "${symbol.lowercase()}@kline_${interval}"
        return subscribe(stream, StreamType.KLINE)
    }
    
    // 订阅聚合交易数据
    fun subscribeAggTrade(symbol: String): Boolean {
        val stream = "${symbol.lowercase()}@aggTrade"
        return subscribe(stream, StreamType.AGG_TRADE)
    }
    
    // 订阅深度数据
    fun subscribeDepth(symbol: String, levels: Int = 20): Boolean {
        val stream = "${symbol.lowercase()}@depth${levels}@100ms"
        return subscribe(stream, StreamType.DEPTH)
    }
    
    // 取消订阅
    fun unsubscribe(stream: String): Boolean {
        val subscription = subscriptions.remove(stream) ?: return false
        val connection = connections[subscription.connectionId] ?: return false
        
        return connection.unsubscribe(stream)
    }
    
    // 批量订阅
    fun batchSubscribe(streams: List<String>, types: List<StreamType>): Map<String, Boolean> {
        require(streams.size == types.size) { "Streams and types must have the same size" }
        
        val results = mutableMapOf<String, Boolean>()
        streams.zip(types).forEach { (stream, type) ->
            results[stream] = subscribe(stream, type)
        }
        return results
    }
    
    // 获取连接统计
    fun getConnectionStatistics(): WebSocketStatistics {
        return _statisticsFlow.value
    }
    
    // 获取活跃订阅
    fun getActiveSubscriptions(): List<String> {
        return subscriptions.keys.toList()
    }
    
    // 私有方法：订阅流
    private fun subscribe(stream: String, type: StreamType): Boolean {
        if (isShuttingDown.get()) {
            Log.w(TAG, "Cannot subscribe - manager is shutting down")
            return false
        }
        
        // 检查是否已经订阅
        if (subscriptions.containsKey(stream)) {
            Log.d(TAG, "Stream $stream already subscribed")
            return true
        }
        
        // 找到合适的连接或创建新连接
        val connection = findOrCreateConnection()
        if (connection == null) {
            Log.e(TAG, "Failed to get connection for stream $stream")
            return false
        }
        
        // 添加订阅
        val success = connection.subscribe(stream)
        if (success) {
            subscriptions[stream] = SubscriptionInfo(
                stream = stream,
                type = type,
                connectionId = connection.id,
                subscribedAt = System.currentTimeMillis()
            )
            Log.d(TAG, "Successfully subscribed to $stream")
        } else {
            Log.e(TAG, "Failed to subscribe to $stream")
        }
        
        return success
    }
    
    // 找到或创建连接
    private fun findOrCreateConnection(): WebSocketConnection? {
        // 寻找有空余容量的现有连接
        val availableConnection = connections.values.find { connection ->
            connection.isConnected() && connection.getSubscriptionCount() < MAX_SUBSCRIPTIONS_PER_CONNECTION
        }
        
        if (availableConnection != null) {
            return availableConnection
        }
        
        // 创建新连接
        val connectionId = "conn_${connectionCounter.incrementAndGet()}"
        val connection = WebSocketConnection(
            id = connectionId,
            url = BASE_WS_URL,
            okHttpClient = okHttpClient,
            gson = gson,
            onMessage = ::handleMessage,
            onStateChange = ::handleConnectionStateChange
        )
        
        connections[connectionId] = connection
        
        // 启动连接
        scope.launch {
            connection.connect()
        }
        
        return connection
    }
    
    // 处理WebSocket消息
    private fun handleMessage(connectionId: String, message: String) {
        try {
            // 更新统计
            _statisticsFlow.value = _statisticsFlow.value.copy(
                messagesReceived = _statisticsFlow.value.messagesReceived + 1,
                lastMessageTime = System.currentTimeMillis()
            )
            
            // 解析消息类型
            val jsonObject = gson.fromJson(message, Map::class.java)
            val stream = jsonObject["stream"] as? String
            val data = jsonObject["data"] as? Map<*, *>
            
            if (stream != null && data != null) {
                handleStreamData(stream, gson.toJson(data))
            } else {
                // 处理直接的事件消息
                val eventType = jsonObject["e"] as? String
                if (eventType != null) {
                    handleEventData(eventType, message)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
            _statisticsFlow.value = _statisticsFlow.value.copy(
                parseErrors = _statisticsFlow.value.parseErrors + 1
            )
        }
    }
    
    // 处理流数据
    private fun handleStreamData(stream: String, dataJson: String) {
        val subscription = subscriptions.values.find { it.stream == stream } ?: return
        
        when (subscription.type) {
            StreamType.KLINE -> {
                try {
                    val klineData = gson.fromJson(dataJson, BinanceWebSocketKline::class.java)
                    _klineFlow.tryEmit(klineData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing kline data: ${e.message}")
                }
            }
            StreamType.AGG_TRADE -> {
                try {
                    val aggTradeData = gson.fromJson(dataJson, BinanceAggTrade::class.java)
                    _aggTradeFlow.tryEmit(aggTradeData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing aggTrade data: ${e.message}")
                }
            }
            StreamType.DEPTH -> {
                try {
                    val depthData = gson.fromJson(dataJson, BinanceDepthUpdate::class.java)
                    _depthFlow.tryEmit(depthData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing depth data: ${e.message}")
                }
            }
        }
    }
    
    // 处理事件数据
    private fun handleEventData(eventType: String, message: String) {
        when (eventType) {
            "kline" -> {
                try {
                    val klineData = gson.fromJson(message, BinanceWebSocketKline::class.java)
                    _klineFlow.tryEmit(klineData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing kline event: ${e.message}")
                }
            }
            "aggTrade" -> {
                try {
                    val aggTradeData = gson.fromJson(message, BinanceAggTrade::class.java)
                    _aggTradeFlow.tryEmit(aggTradeData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing aggTrade event: ${e.message}")
                }
            }
            "depthUpdate" -> {
                try {
                    val depthData = gson.fromJson(message, BinanceDepthUpdate::class.java)
                    _depthFlow.tryEmit(depthData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing depth event: ${e.message}")
                }
            }
        }
    }
    
    // 处理连接状态变化
    private fun handleConnectionStateChange(connectionId: String, state: ConnectionState, error: String?) {
        _connectionStateFlow.tryEmit(
            ConnectionStateUpdate(
                connectionId = connectionId,
                state = state,
                timestamp = System.currentTimeMillis(),
                error = error
            )
        )
        
        // 更新统计
        when (state) {
            ConnectionState.CONNECTED -> {
                _statisticsFlow.value = _statisticsFlow.value.copy(
                    activeConnections = connections.values.count { it.isConnected() },
                    totalConnections = _statisticsFlow.value.totalConnections + 1
                )
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                _statisticsFlow.value = _statisticsFlow.value.copy(
                    activeConnections = connections.values.count { it.isConnected() }
                )
                
                // 处理连接丢失的订阅
                handleConnectionLoss(connectionId)
            }
            else -> {}
        }
    }
    
    // 处理连接丢失
    private fun handleConnectionLoss(connectionId: String) {
        val lostSubscriptions = subscriptions.values.filter { it.connectionId == connectionId }
        
        if (lostSubscriptions.isNotEmpty() && !isShuttingDown.get()) {
            Log.w(TAG, "Connection $connectionId lost with ${lostSubscriptions.size} subscriptions")
            
            // 重新订阅到其他连接
            scope.launch {
                delay(RECONNECT_DELAY_BASE)
                
                lostSubscriptions.forEach { subscription ->
                    subscriptions.remove(subscription.stream)
                    subscribe(subscription.stream, subscription.type)
                }
            }
        }
    }
    
    // 启动统计更新器
    private fun startStatisticsUpdater() {
        scope.launch {
            while (!isShuttingDown.get()) {
                delay(5000) // 每5秒更新一次统计
                
                val currentStats = _statisticsFlow.value
                val now = System.currentTimeMillis()
                
                _statisticsFlow.value = currentStats.copy(
                    activeConnections = connections.values.count { it.isConnected() },
                    totalSubscriptions = subscriptions.size,
                    uptime = now - currentStats.startTime
                )
            }
        }
    }
    
    // 启动连接监控器
    private fun startConnectionMonitor() {
        scope.launch {
            while (!isShuttingDown.get()) {
                delay(30000) // 每30秒检查一次连接健康状态
                
                connections.values.forEach { connection ->
                    if (!connection.isHealthy()) {
                        Log.w(TAG, "Connection ${connection.id} is unhealthy, attempting recovery")
                        scope.launch {
                            connection.reconnect()
                        }
                    }
                }
            }
        }
    }
}

// WebSocket连接类
class WebSocketConnection(
    val id: String,
    private val url: String,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val onMessage: (String, String) -> Unit,
    private val onStateChange: (String, ConnectionState, String?) -> Unit
) {
    private var webSocket: WebSocket? = null
    private var connectionState = ConnectionState.DISCONNECTED
    private val subscribedStreams = ConcurrentHashMap<String, Long>()
    private var lastPingTime = 0L
    private var lastPongTime = 0L
    private var reconnectAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    suspend fun connect() {
        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING) {
            return
        }
        
        connectionState = ConnectionState.CONNECTING
        onStateChange(id, connectionState, null)
        
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, webSocketListener)
            
        } catch (e: Exception) {
            connectionState = ConnectionState.ERROR
            onStateChange(id, connectionState, e.message)
        }
    }
    
    fun subscribe(stream: String): Boolean {
        if (connectionState != ConnectionState.CONNECTED) {
            return false
        }
        
        val subscribeMessage = mapOf(
            "method" to "SUBSCRIBE",
            "params" to listOf(stream),
            "id" to System.currentTimeMillis()
        )
        
        val success = webSocket?.send(gson.toJson(subscribeMessage)) ?: false
        if (success) {
            subscribedStreams[stream] = System.currentTimeMillis()
        }
        
        return success
    }
    
    fun unsubscribe(stream: String): Boolean {
        if (connectionState != ConnectionState.CONNECTED) {
            return false
        }
        
        val unsubscribeMessage = mapOf(
            "method" to "UNSUBSCRIBE",
            "params" to listOf(stream),
            "id" to System.currentTimeMillis()
        )
        
        val success = webSocket?.send(gson.toJson(unsubscribeMessage)) ?: false
        if (success) {
            subscribedStreams.remove(stream)
        }
        
        return success
    }
    
    fun isConnected(): Boolean = connectionState == ConnectionState.CONNECTED
    
    fun isHealthy(): Boolean {
        val now = System.currentTimeMillis()
        return isConnected() && (now - lastPongTime) < 60000 // 1分钟内有pong响应
    }
    
    fun getSubscriptionCount(): Int = subscribedStreams.size
    
    suspend fun reconnect() {
        close()
        delay(minOf(1000L * (1 shl reconnectAttempts), 30000L)) // 指数退避，最大30秒
        reconnectAttempts++
        connect()
    }
    
    fun close() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        connectionState = ConnectionState.DISCONNECTED
        subscribedStreams.clear()
        scope.cancel()
    }
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connectionState = ConnectionState.CONNECTED
            reconnectAttempts = 0
            lastPongTime = System.currentTimeMillis()
            onStateChange(id, connectionState, null)
            
            // 启动心跳
            startHeartbeat()
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "pong") {
                lastPongTime = System.currentTimeMillis()
                return
            }
            
            onMessage(id, text)
        }
        
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            connectionState = ConnectionState.DISCONNECTING
            onStateChange(id, connectionState, reason)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connectionState = ConnectionState.DISCONNECTED
            onStateChange(id, connectionState, reason)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connectionState = ConnectionState.ERROR
            onStateChange(id, connectionState, t.message)
            
            // 自动重连
            if (reconnectAttempts < 10) {
                scope.launch {
                    reconnect()
                }
            }
        }
    }
    
    private fun startHeartbeat() {
        scope.launch {
            while (connectionState == ConnectionState.CONNECTED) {
                delay(30000) // 30秒心跳间隔
                
                if (connectionState == ConnectionState.CONNECTED) {
                    lastPingTime = System.currentTimeMillis()
                    webSocket?.send("ping")
                }
            }
        }
    }
}

// 数据类
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, RECONNECTING, ERROR
}

enum class StreamType {
    KLINE, AGG_TRADE, DEPTH, TICKER, BOOK_TICKER
}

data class SubscriptionInfo(
    val stream: String,
    val type: StreamType,
    val connectionId: String,
    val subscribedAt: Long
)

data class ConnectionStateUpdate(
    val connectionId: String,
    val state: ConnectionState,
    val timestamp: Long,
    val error: String? = null
)

data class WebSocketStatistics(
    val startTime: Long = System.currentTimeMillis(),
    val uptime: Long = 0,
    val activeConnections: Int = 0,
    val totalConnections: Int = 0,
    val totalSubscriptions: Int = 0,
    val messagesReceived: Long = 0,
    val parseErrors: Long = 0,
    val lastMessageTime: Long = 0
)

data class BinanceDepthUpdate(
    val symbol: String,
    val firstUpdateId: Long,
    val finalUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)