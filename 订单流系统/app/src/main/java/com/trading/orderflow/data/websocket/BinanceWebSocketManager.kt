package com.trading.orderflow.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.trading.orderflow.data.model.BinanceWebSocketKline
import com.trading.orderflow.data.model.BinanceAggTrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BinanceWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    
    companion object {
        private const val TAG = "BinanceWebSocket"
        private const val BASE_WS_URL = "wss://stream.binance.com:9443/ws"
        private const val PING_INTERVAL = 30000L // 30秒心跳
        private const val RECONNECT_DELAY = 5000L // 5秒重连延迟
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 数据流
    private val _klineFlow = MutableSharedFlow<BinanceWebSocketKline>()
    val klineFlow: SharedFlow<BinanceWebSocketKline> = _klineFlow.asSharedFlow()
    
    private val _aggTradeFlow = MutableSharedFlow<BinanceAggTrade>()
    val aggTradeFlow: SharedFlow<BinanceAggTrade> = _aggTradeFlow.asSharedFlow()
    
    private val _connectionStateFlow = MutableSharedFlow<ConnectionState>()
    val connectionStateFlow: SharedFlow<ConnectionState> = _connectionStateFlow.asSharedFlow()
    
    // 订阅的流
    private val subscribedStreams = mutableSetOf<String>()
    
    enum class ConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, ERROR
    }
    
    fun connect() {
        if (isConnected) return
        
        Log.d(TAG, "Connecting to Binance WebSocket...")
        _connectionStateFlow.tryEmit(ConnectionState.CONNECTING)
        
        val request = Request.Builder()
            .url(BASE_WS_URL)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        
        // 启动心跳
        startHeartbeat()
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting from Binance WebSocket...")
        isConnected = false
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null
        scope.cancel()
        _connectionStateFlow.tryEmit(ConnectionState.DISCONNECTED)
    }
    
    // 订阅K线数据
    fun subscribeKline(symbol: String, interval: String) {
        val stream = "${symbol.lowercase()}@kline_${interval}"
        subscribeToStream(stream)
    }
    
    // 订阅聚合交易数据
    fun subscribeAggTrade(symbol: String) {
        val stream = "${symbol.lowercase()}@aggTrade"
        subscribeToStream(stream)
    }
    
    // 取消订阅
    fun unsubscribeKline(symbol: String, interval: String) {
        val stream = "${symbol.lowercase()}@kline_${interval}"
        unsubscribeFromStream(stream)
    }
    
    fun unsubscribeAggTrade(symbol: String) {
        val stream = "${symbol.lowercase()}@aggTrade"
        unsubscribeFromStream(stream)
    }
    
    private fun subscribeToStream(stream: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected, cannot subscribe to $stream")
            return
        }
        
        subscribedStreams.add(stream)
        val subscribeMessage = mapOf(
            "method" to "SUBSCRIBE",
            "params" to listOf(stream),
            "id" to System.currentTimeMillis()
        )
        
        val message = gson.toJson(subscribeMessage)
        webSocket?.send(message)
        Log.d(TAG, "Subscribed to stream: $stream")
    }
    
    private fun unsubscribeFromStream(stream: String) {
        if (!isConnected) return
        
        subscribedStreams.remove(stream)
        val unsubscribeMessage = mapOf(
            "method" to "UNSUBSCRIBE",
            "params" to listOf(stream),
            "id" to System.currentTimeMillis()
        )
        
        val message = gson.toJson(unsubscribeMessage)
        webSocket?.send(message)
        Log.d(TAG, "Unsubscribed from stream: $stream")
    }
    
    private fun startHeartbeat() {
        scope.launch {
            while (isConnected) {
                delay(PING_INTERVAL)
                if (isConnected) {
                    webSocket?.send("ping")
                }
            }
        }
    }
    
    private fun reconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached")
            _connectionStateFlow.tryEmit(ConnectionState.ERROR)
            return
        }
        
        reconnectAttempts++
        Log.d(TAG, "Reconnecting... Attempt $reconnectAttempts")
        _connectionStateFlow.tryEmit(ConnectionState.RECONNECTING)
        
        scope.launch {
            delay(RECONNECT_DELAY)
            connect()
        }
    }
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            isConnected = true
            reconnectAttempts = 0
            _connectionStateFlow.tryEmit(ConnectionState.CONNECTED)
            
            // 重新订阅之前的流
            subscribedStreams.toList().forEach { stream ->
                subscribeToStream(stream)
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                // 处理心跳响应
                if (text == "pong") {
                    return
                }
                
                // 解析JSON消息
                val jsonObject = gson.fromJson(text, Map::class.java)
                val eventType = jsonObject["e"] as? String
                
                when (eventType) {
                    "kline" -> {
                        val klineData = gson.fromJson(text, BinanceWebSocketKline::class.java)
                        _klineFlow.tryEmit(klineData)
                    }
                    "aggTrade" -> {
                        val aggTradeData = gson.fromJson(text, BinanceAggTrade::class.java)
                        _aggTradeFlow.tryEmit(aggTradeData)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
            }
        }
        
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            isConnected = false
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            isConnected = false
            _connectionStateFlow.tryEmit(ConnectionState.DISCONNECTED)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket error: ${t.message}")
            isConnected = false
            _connectionStateFlow.tryEmit(ConnectionState.ERROR)
            
            // 自动重连
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnect()
            }
        }
    }
}