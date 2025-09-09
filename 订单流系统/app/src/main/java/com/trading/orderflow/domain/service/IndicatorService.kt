package com.trading.orderflow.domain.service

import android.util.Log
import com.trading.orderflow.data.database.IndicatorDao
import com.trading.orderflow.data.model.*
import com.trading.orderflow.domain.calculator.IndicatorCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndicatorService @Inject constructor(
    private val indicatorCalculator: IndicatorCalculator,
    private val indicatorDao: IndicatorDao
) {
    
    companion object {
        private const val TAG = "IndicatorService"
        private const val MIN_DATA_POINTS = 200 // 最少需要200个数据点才开始计算
    }
    
    // 批量计算所有指标
    suspend fun calculateAllIndicators(
        symbol: String,
        interval: String,
        klines: List<KlineData>
    ): List<IndicatorData> = withContext(Dispatchers.Default) {
        
        if (klines.size < MIN_DATA_POINTS) {
            Log.w(TAG, "Not enough data points for indicator calculation: ${klines.size}")
            return@withContext emptyList()
        }
        
        val prices = klines.map { it.close }
        val result = mutableListOf<IndicatorData>()
        
        try {
            // 计算各种EMA
            val ema12 = indicatorCalculator.calculateEMA(prices, 12)
            val ema26 = indicatorCalculator.calculateEMA(prices, 26)
            val ema50 = indicatorCalculator.calculateEMA(prices, 50)
            val ema200 = indicatorCalculator.calculateEMA(prices, 200)
            
            // 计算SMA
            val sma20 = calculateSMA(prices, 20)
            
            // 计算MACD
            val macdData = indicatorCalculator.calculateMACD(prices)
            
            // 计算RSI
            val rsiData = indicatorCalculator.calculateRSI(prices, 14)
            
            // 计算ATR
            val atrData = indicatorCalculator.calculateATR(klines, 14)
            
            // 计算布林带
            val bollingerData = indicatorCalculator.calculateBollingerBands(prices, 20, 2.0)
            
            // 计算VWAP
            val vwapData = indicatorCalculator.calculateVWAP(klines)
            
            // 组合所有指标数据
            val maxIndex = klines.size
            val startIndex = maxOf(
                maxIndex - ema200.size,
                maxIndex - macdData.size,
                maxIndex - rsiData.size,
                maxIndex - atrData.size,
                maxIndex - bollingerData.size
            )
            
            for (i in startIndex until maxIndex) {
                val kline = klines[i]
                val indicatorData = IndicatorData(
                    id = "${symbol}_${interval}_${kline.openTime}",
                    symbol = symbol,
                    interval = interval,
                    timestamp = kline.openTime,
                    price = kline.close,
                    volume = kline.volume,
                    
                    // EMA指标
                    ema12 = getValueAtIndex(ema12, i, startIndex),
                    ema26 = getValueAtIndex(ema26, i, startIndex),
                    ema50 = getValueAtIndex(ema50, i, startIndex),
                    ema200 = getValueAtIndex(ema200, i, startIndex),
                    sma20 = getValueAtIndex(sma20, i, startIndex),
                    
                    // MACD指标
                    macd = getMACDValueAtIndex(macdData, i, startIndex)?.macd,
                    macdSignal = getMACDValueAtIndex(macdData, i, startIndex)?.signal,
                    macdHistogram = getMACDValueAtIndex(macdData, i, startIndex)?.histogram,
                    
                    // RSI指标
                    rsi = getValueAtIndex(rsiData, i, startIndex),
                    
                    // ATR指标
                    atr = getValueAtIndex(atrData, i, startIndex),
                    
                    // 布林带指标
                    bollingerUpper = getBollingerValueAtIndex(bollingerData, i, startIndex)?.upperBand,
                    bollingerMiddle = getBollingerValueAtIndex(bollingerData, i, startIndex)?.middleBand,
                    bollingerLower = getBollingerValueAtIndex(bollingerData, i, startIndex)?.lowerBand,
                    
                    // VWAP指标
                    vwap = getValueAtIndex(vwapData, i, 0),
                    
                    // CVD将在实时数据中计算
                    cvd = kline.cvd
                )
                
                result.add(indicatorData)
            }
            
            // 保存到数据库
            indicatorDao.insertIndicators(result)
            Log.d(TAG, "Calculated and saved ${result.size} indicator data points")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating indicators: ${e.message}")
        }
        
        return@withContext result
    }
    
    // 增量计算指标（用于实时更新）
    suspend fun calculateIncrementalIndicators(
        symbol: String,
        interval: String,
        newKline: KlineData,
        previousIndicator: IndicatorData?
    ): IndicatorData = withContext(Dispatchers.Default) {
        
        val price = newKline.close
        
        // 增量计算EMA
        val ema12 = indicatorCalculator.calculateEMAIncremental(price, previousIndicator?.ema12, 12)
        val ema26 = indicatorCalculator.calculateEMAIncremental(price, previousIndicator?.ema26, 26)
        val ema50 = indicatorCalculator.calculateEMAIncremental(price, previousIndicator?.ema50, 50)
        val ema200 = indicatorCalculator.calculateEMAIncremental(price, previousIndicator?.ema200, 200)
        
        // 增量计算MACD
        val macd = ema12 - ema26
        val macdSignal = previousIndicator?.macdSignal?.let {
            indicatorCalculator.calculateEMAIncremental(macd, it, 9)
        } ?: macd
        val macdHistogram = macd - macdSignal
        
        val indicatorData = IndicatorData(
            id = "${symbol}_${interval}_${newKline.openTime}",
            symbol = symbol,
            interval = interval,
            timestamp = newKline.openTime,
            price = price,
            volume = newKline.volume,
            
            ema12 = ema12,
            ema26 = ema26,
            ema50 = ema50,
            ema200 = ema200,
            
            macd = macd,
            macdSignal = macdSignal,
            macdHistogram = macdHistogram,
            
            cvd = newKline.cvd
        )
        
        // 保存到数据库
        indicatorDao.insertIndicators(listOf(indicatorData))
        
        return@withContext indicatorData
    }
    
    // 获取指标数据流
    fun getIndicatorFlow(symbol: String, interval: String): Flow<List<IndicatorData>> {
        return indicatorDao.getIndicatorFlow(symbol, interval)
    }
    
    // 获取最新指标数据
    suspend fun getLatestIndicator(symbol: String, interval: String): IndicatorData? {
        return indicatorDao.getLatestIndicator(symbol, interval)
    }
    
    // 辅助函数
    private fun calculateSMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()
        
        val result = mutableListOf<Double>()
        for (i in period - 1 until prices.size) {
            val sma = prices.subList(i - period + 1, i + 1).average()
            result.add(sma)
        }
        return result
    }
    
    private fun getValueAtIndex(list: List<Double>, currentIndex: Int, startIndex: Int): Double? {
        val adjustedIndex = currentIndex - startIndex
        return if (adjustedIndex >= 0 && adjustedIndex < list.size) list[adjustedIndex] else null
    }
    
    private fun getMACDValueAtIndex(list: List<MACDIndicator>, currentIndex: Int, startIndex: Int): MACDIndicator? {
        val adjustedIndex = currentIndex - startIndex
        return if (adjustedIndex >= 0 && adjustedIndex < list.size) list[adjustedIndex] else null
    }
    
    private fun getBollingerValueAtIndex(list: List<BollingerBands>, currentIndex: Int, startIndex: Int): BollingerBands? {
        val adjustedIndex = currentIndex - startIndex
        return if (adjustedIndex >= 0 && adjustedIndex < list.size) list[adjustedIndex] else null
    }
}