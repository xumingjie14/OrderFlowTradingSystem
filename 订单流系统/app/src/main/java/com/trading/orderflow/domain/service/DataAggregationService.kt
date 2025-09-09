package com.trading.orderflow.domain.service

import android.util.Log
import com.trading.orderflow.data.model.*
import com.trading.orderflow.data.database.KlineDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataAggregationService @Inject constructor(
    private val webSocketService: WebSocketService,
    private val klineDao: KlineDao
) {
    
    companion object {
        private const val TAG = "DataAggregationService"
        private const val CVD_WINDOW_SIZE = 1000 // CVD计算窗口大小
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cvdCalculators = ConcurrentHashMap<String, CVDCalculator>()
    private val priceAggregators = ConcurrentHashMap<String, PriceAggregator>()
    
    // 启动数据聚合服务
    fun start() {
        Log.d(TAG, "Starting Data Aggregation Service")
        startKlineAggregation()
        startCVDCalculation()
        startPriceAggregation()
    }
    
    // 停止服务
    fun stop() {
        Log.d(TAG, "Stopping Data Aggregation Service")
        scope.cancel()
        cvdCalculators.clear()
        priceAggregators.clear()
    }
    
    // 获取实时价格流
    fun getRealTimePriceFlow(symbol: String): Flow<Double> {
        return priceAggregators[symbol]?.priceFlow ?: flowOf()
    }
    
    // 获取CVD流
    fun getCVDFlow(symbol: String): Flow<Double> {
        return cvdCalculators[symbol]?.cvdFlow ?: flowOf()
    }
    
    // 获取聚合K线流
    fun getAggregatedKlineFlow(symbol: String, interval: String): Flow<KlineData> {
        return webSocketService.getKlineFlow(symbol, interval)
            .filter { it.kline.isClosed }
            .map { wsKline ->
                wsKline.kline.toKlineData()
            }
            .onEach { klineData ->
                // 保存到数据库
                try {
                    klineDao.insertKlines(listOf(klineData))
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving kline data: ${e.message}")
                }
            }
    }
    
    // 启动K线数据聚合
    private fun startKlineAggregation() {
        scope.launch {
            webSocketService.getKlineFlow()
                .collect { wsKline ->
                    try {
                        // 只处理已关闭的K线
                        if (wsKline.kline.isClosed) {
                            val klineData = wsKline.kline.toKlineData()
                            
                            // 异步保存到数据库
                            launch {
                                klineDao.insertKlines(listOf(klineData))
                            }
                            
                            Log.d(TAG, "Processed kline: ${klineData.symbol} ${klineData.interval} ${klineData.close}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing kline: ${e.message}")
                    }
                }
        }
    }
    
    // 启动CVD计算
    private fun startCVDCalculation() {
        scope.launch {
            webSocketService.getAggTradeFlow()
                .collect { aggTrade ->
                    try {
                        val calculator = cvdCalculators.getOrPut(aggTrade.symbol) {
                            CVDCalculator(aggTrade.symbol, CVD_WINDOW_SIZE)
                        }
                        
                        calculator.addTrade(
                            price = aggTrade.price.toDouble(),
                            quantity = aggTrade.quantity.toDouble(),
                            isBuyerMaker = aggTrade.isBuyerMaker,
                            timestamp = aggTrade.tradeTime
                        )
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating CVD: ${e.message}")
                    }
                }
        }
    }
    
    // 启动价格聚合
    private fun startPriceAggregation() {
        scope.launch {
            webSocketService.getAggTradeFlow()
                .collect { aggTrade ->
                    try {
                        val aggregator = priceAggregators.getOrPut(aggTrade.symbol) {
                            PriceAggregator(aggTrade.symbol)
                        }
                        
                        aggregator.updatePrice(
                            price = aggTrade.price.toDouble(),
                            timestamp = aggTrade.tradeTime
                        )
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error aggregating price: ${e.message}")
                    }
                }
        }
    }
}

// CVD计算器
class CVDCalculator(private val symbol: String, private val windowSize: Int) {
    private val trades = mutableListOf<TradeData>()
    private var currentCVD = 0.0
    
    private val _cvdFlow = MutableSharedFlow<Double>()
    val cvdFlow: SharedFlow<Double> = _cvdFlow.asSharedFlow()
    
    fun addTrade(price: Double, quantity: Double, isBuyerMaker: Boolean, timestamp: Long) {
        val trade = TradeData(price, quantity, isBuyerMaker, timestamp)
        trades.add(trade)
        
        // 维护窗口大小
        if (trades.size > windowSize) {
            val removedTrade = trades.removeAt(0)
            // 从CVD中减去移除的交易
            currentCVD -= if (removedTrade.isBuyerMaker) -removedTrade.quantity else removedTrade.quantity
        }
        
        // 更新CVD
        currentCVD += if (isBuyerMaker) -quantity else quantity
        
        _cvdFlow.tryEmit(currentCVD)
    }
    
    data class TradeData(
        val price: Double,
        val quantity: Double,
        val isBuyerMaker: Boolean,
        val timestamp: Long
    )
}

// 价格聚合器
class PriceAggregator(private val symbol: String) {
    private var lastPrice = 0.0
    private var lastUpdateTime = 0L
    
    private val _priceFlow = MutableSharedFlow<Double>()
    val priceFlow: SharedFlow<Double> = _priceFlow.asSharedFlow()
    
    fun updatePrice(price: Double, timestamp: Long) {
        if (price != lastPrice || timestamp - lastUpdateTime > 1000) {
            lastPrice = price
            lastUpdateTime = timestamp
            _priceFlow.tryEmit(price)
        }
    }
}