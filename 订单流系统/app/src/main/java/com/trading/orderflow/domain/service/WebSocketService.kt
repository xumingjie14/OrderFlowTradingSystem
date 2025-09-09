package com.trading.orderflow.domain.service

import android.util.Log
import com.trading.orderflow.data.websocket.EnhancedWebSocketManager
import com.trading.orderflow.data.websocket.StreamType
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val webSocketManager: EnhancedWebSocketManager
) {
    
    companion object {
        private const val TAG = "WebSocketService"
    }
    
    // 启动服务
    fun start() {
        Log.d(TAG, "Starting WebSocket Service")
        webSocketManager.start()
    }
    
    // 停止服务
    fun stop() {
        Log.d(TAG, "Stopping WebSocket Service")
        webSocketManager.shutdown()
    }
    
    // 订阅交易对的所有数据流
    fun subscribeSymbol(symbol: String, intervals: List<String> = listOf("1h", "4h")): Boolean {
        var allSuccess = true
        
        // 订阅K线数据
        intervals.forEach { interval ->
            if (!webSocketManager.subscribeKline(symbol, interval)) {
                allSuccess = false
                Log.e(TAG, "Failed to subscribe kline for $symbol $interval")
            }
        }
        
        // 订阅聚合交易数据
        if (!webSocketManager.subscribeAggTrade(symbol)) {
            allSuccess = false
            Log.e(TAG, "Failed to subscribe aggTrade for $symbol")
        }
        
        // 订阅深度数据
        if (!webSocketManager.subscribeDepth(symbol, 20)) {
            allSuccess = false
            Log.e(TAG, "Failed to subscribe depth for $symbol")
        }
        
        return allSuccess
    }
    
    // 取消订阅交易对
    fun unsubscribeSymbol(symbol: String, intervals: List<String> = listOf("1h", "4h")): Boolean {
        var allSuccess = true
        
        intervals.forEach { interval ->
            val stream = "${symbol.lowercase()}@kline_${interval}"
            if (!webSocketManager.unsubscribe(stream)) {
                allSuccess = false
            }
        }
        
        val aggTradeStream = "${symbol.lowercase()}@aggTrade"
        if (!webSocketManager.unsubscribe(aggTradeStream)) {
            allSuccess = false
        }
        
        val depthStream = "${symbol.lowercase()}@depth20@100ms"
        if (!webSocketManager.unsubscribe(depthStream)) {
            allSuccess = false
        }
        
        return allSuccess
    }
    
    // 批量订阅多个交易对
    fun subscribeMultipleSymbols(symbols: List<String>, intervals: List<String> = listOf("4h")): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        
        symbols.forEach { symbol ->
            results[symbol] = subscribeSymbol(symbol, intervals)
        }
        
        return results
    }
    
    // 获取K线数据流
    fun getKlineFlow(symbol: String? = null, interval: String? = null): Flow<BinanceWebSocketKline> {
        return webSocketManager.klineFlow
            .filter { kline ->
                (symbol == null || kline.symbol.equals(symbol, ignoreCase = true)) &&
                (interval == null || kline.kline.interval == interval)
            }
    }
    
    // 获取聚合交易数据流
    fun getAggTradeFlow(symbol: String? = null): Flow<BinanceAggTrade> {
        return webSocketManager.aggTradeFlow
            .filter { trade ->
                symbol == null || trade.symbol.equals(symbol, ignoreCase = true)
            }
    }
    
    // 获取深度数据流
    fun getDepthFlow(symbol: String? = null): Flow<BinanceDepthUpdate> {
        return webSocketManager.depthFlow
            .filter { depth ->
                symbol == null || depth.symbol.equals(symbol, ignoreCase = true)
            }
    }
    
    // 获取连接状态流
    fun getConnectionStateFlow() = webSocketManager.connectionStateFlow
    
    // 获取统计信息
    fun getStatistics() = webSocketManager.getConnectionStatistics()
    
    // 获取活跃订阅
    fun getActiveSubscriptions() = webSocketManager.getActiveSubscriptions()
    
    // 健康检查
    fun isHealthy(): Boolean {
        val stats = getStatistics()
        val now = System.currentTimeMillis()
        
        return stats.activeConnections > 0 && 
               (now - stats.lastMessageTime) < 60000 && // 1分钟内有消息
               stats.parseErrors < stats.messagesReceived * 0.01 // 错误率小于1%
    }
}