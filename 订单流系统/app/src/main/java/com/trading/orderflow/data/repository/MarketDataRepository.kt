package com.trading.orderflow.data.repository

import android.util.Log
import com.trading.orderflow.data.api.BinanceApiService
import com.trading.orderflow.data.api.BinanceFuturesApiService
import com.trading.orderflow.data.database.KlineDao
import com.trading.orderflow.data.model.*
import com.trading.orderflow.data.websocket.BinanceWebSocketManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketDataRepository @Inject constructor(
    private val binanceApi: BinanceApiService,
    private val binanceFuturesApi: BinanceFuturesApiService,
    private val klineDao: KlineDao,
    private val webSocketManager: BinanceWebSocketManager
) {
    
    companion object {
        private const val TAG = "MarketDataRepository"
        private const val DEFAULT_KLINE_LIMIT = 1000
    }
    
    // 获取历史K线数据（优先从本地缓存获取）
    suspend fun getHistoricalKlines(
        symbol: String,
        interval: String,
        limit: Int = DEFAULT_KLINE_LIMIT,
        forceRefresh: Boolean = false
    ): Result<List<KlineData>> = withContext(Dispatchers.IO) {
        try {
            // 如果不强制刷新，先尝试从本地获取
            if (!forceRefresh) {
                val localKlines = klineDao.getKlines(symbol, interval, limit)
                if (localKlines.isNotEmpty()) {
                    Log.d(TAG, "Retrieved ${localKlines.size} klines from local cache")
                    return@withContext Result.success(localKlines)
                }
            }
            
            // 从API获取数据
            val response = binanceApi.getKlines(symbol, interval, limit)
            if (response.isSuccessful && response.body() != null) {
                val klineDataList = response.body()!!.map { rawKline ->
                    rawKline.toKlineData(symbol, interval)
                }
                
                // 保存到本地数据库
                klineDao.insertKlines(klineDataList)
                
                // 清理旧数据（保留最新的2000条）
                if (klineDataList.isNotEmpty()) {
                    val oldestTimeToKeep = klineDataList.sortedByDescending { it.openTime }[minOf(1999, klineDataList.size - 1)].openTime
                    klineDao.deleteOldKlines(symbol, interval, oldestTimeToKeep)
                }
                
                Log.d(TAG, "Retrieved ${klineDataList.size} klines from API")
                Result.success(klineDataList)
            } else {
                Result.failure(Exception("API request failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting historical klines: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取实时K线数据流
    fun getRealTimeKlines(symbol: String, interval: String): Flow<KlineData> {
        return webSocketManager.klineFlow
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
            .filter { it.kline.interval == interval }
            .map { it.kline.toKlineData() }
            .onEach { klineData ->
                // 实时更新本地数据库
                try {
                    klineDao.insertKlines(listOf(klineData))
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving real-time kline: ${e.message}")
                }
            }
    }
    
    // 获取聚合交易数据流（用于CVD计算）
    fun getAggTradeFlow(symbol: String): Flow<BinanceAggTrade> {
        return webSocketManager.aggTradeFlow
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
    }
    
    // 获取24小时价格统计
    suspend fun get24hrTicker(symbol: String): Result<BinanceTicker24hr> = withContext(Dispatchers.IO) {
        try {
            val response = binanceApi.get24hrTicker(symbol)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get 24hr ticker: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting 24hr ticker: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取深度数据
    suspend fun getDepth(symbol: String, limit: Int = 1000): Result<BinanceDepthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = binanceApi.getDepth(symbol, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get depth: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting depth: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取合约持仓量
    suspend fun getOpenInterest(symbol: String): Result<BinanceFuturesOpenInterest> = withContext(Dispatchers.IO) {
        try {
            val response = binanceFuturesApi.getOpenInterest(symbol)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get open interest: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting open interest: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取资金费率
    suspend fun getFundingRate(symbol: String): Result<List<BinanceFundingRate>> = withContext(Dispatchers.IO) {
        try {
            val response = binanceFuturesApi.getFundingRate(symbol, 1)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get funding rate: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting funding rate: ${e.message}")
            Result.failure(e)
        }
    }
    
    // WebSocket连接管理
    fun connectWebSocket() {
        webSocketManager.connect()
    }
    
    fun disconnectWebSocket() {
        webSocketManager.disconnect()
    }
    
    fun subscribeToSymbol(symbol: String, interval: String) {
        webSocketManager.subscribeKline(symbol, interval)
        webSocketManager.subscribeAggTrade(symbol)
    }
    
    fun unsubscribeFromSymbol(symbol: String, interval: String) {
        webSocketManager.unsubscribeKline(symbol, interval)
        webSocketManager.unsubscribeAggTrade(symbol)
    }
    
    // WebSocket连接状态
    fun getConnectionState(): Flow<BinanceWebSocketManager.ConnectionState> {
        return webSocketManager.connectionStateFlow
    }
}