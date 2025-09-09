package com.trading.orderflow.domain.calculator

import com.trading.orderflow.data.model.*
import kotlin.math.*

// 指标计算器接口
interface IndicatorCalculator {
    fun calculateEMA(prices: List<Double>, period: Int): List<Double>
    fun calculateEMAIncremental(currentPrice: Double, previousEMA: Double?, period: Int): Double
    fun calculateMACD(prices: List<Double>): List<MACDIndicator>
    fun calculateRSI(prices: List<Double>, period: Int = 14): List<Double>
    fun calculateATR(klines: List<KlineData>, period: Int = 14): List<Double>
    fun calculateBollingerBands(prices: List<Double>, period: Int = 20, stdDev: Double = 2.0): List<BollingerBands>
    fun calculateVWAP(klines: List<KlineData>): List<Double>
    fun calculateCVD(aggTrades: List<BinanceAggTrade>): Double
}

// 指标计算器实现
class IndicatorCalculatorImpl : IndicatorCalculator {
    
    companion object {
        private const val MIN_PERIODS_FOR_CALCULATION = 2
    }
    
    // EMA计算（批量）
    override fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()
        
        val alpha = 2.0 / (period + 1)
        val emaValues = mutableListOf<Double>()
        
        // 第一个EMA值使用SMA
        val firstSMA = prices.take(period).average()
        emaValues.add(firstSMA)
        
        // 后续EMA值使用递推公式
        for (i in period until prices.size) {
            val ema = alpha * prices[i] + (1 - alpha) * emaValues.last()
            emaValues.add(ema)
        }
        
        return emaValues
    }
    
    // EMA增量计算（实时更新）
    override fun calculateEMAIncremental(currentPrice: Double, previousEMA: Double?, period: Int): Double {
        val alpha = 2.0 / (period + 1)
        return if (previousEMA == null) {
            currentPrice // 第一个值
        } else {
            alpha * currentPrice + (1 - alpha) * previousEMA
        }
    }
    
    // MACD计算
    override fun calculateMACD(prices: List<Double>): List<MACDIndicator> {
        if (prices.size < 26) return emptyList()
        
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        
        if (ema12.isEmpty() || ema26.isEmpty()) return emptyList()
        
        val macdLine = mutableListOf<Double>()
        val startIndex = 26 - 12 // EMA26比EMA12晚开始
        
        for (i in startIndex until ema12.size) {
            val macd = ema12[i] - ema26[i - startIndex]
            macdLine.add(macd)
        }
        
        // 计算MACD信号线（MACD的9期EMA）
        val signalLine = calculateEMA(macdLine, 9)
        
        val result = mutableListOf<MACDIndicator>()
        val signalStartIndex = macdLine.size - signalLine.size
        
        for (i in signalLine.indices) {
            val macdIndex = signalStartIndex + i
            val ema12Index = startIndex + macdIndex
            val ema26Index = macdIndex
            
            result.add(
                MACDIndicator(
                    macd = macdLine[macdIndex],
                    signal = signalLine[i],
                    histogram = macdLine[macdIndex] - signalLine[i],
                    ema12 = ema12[ema12Index],
                    ema26 = ema26[ema26Index]
                )
            )
        }
        
        return result
    }
    
    // RSI计算
    override fun calculateRSI(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period + 1) return emptyList()
        
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        
        // 计算价格变化
        for (i in 1 until prices.size) {
            val change = prices[i] - prices[i - 1]
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)
        }
        
        val rsiValues = mutableListOf<Double>()
        
        // 第一个RSI使用简单平均
        val firstAvgGain = gains.take(period).average()
        val firstAvgLoss = losses.take(period).average()
        
        val firstRS = if (firstAvgLoss != 0.0) firstAvgGain / firstAvgLoss else 100.0
        val firstRSI = 100.0 - (100.0 / (1.0 + firstRS))
        rsiValues.add(firstRSI)
        
        // 后续RSI使用Wilder平滑
        var avgGain = firstAvgGain
        var avgLoss = firstAvgLoss
        
        for (i in period until gains.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            
            val rs = if (avgLoss != 0.0) avgGain / avgLoss else 100.0
            val rsi = 100.0 - (100.0 / (1.0 + rs))
            rsiValues.add(rsi)
        }
        
        return rsiValues
    }
    
    // ATR计算
    override fun calculateATR(klines: List<KlineData>, period: Int): List<Double> {
        if (klines.size < period + 1) return emptyList()
        
        val trueRanges = mutableListOf<Double>()
        
        for (i in 1 until klines.size) {
            val current = klines[i]
            val previous = klines[i - 1]
            
            val tr1 = current.high - current.low
            val tr2 = abs(current.high - previous.close)
            val tr3 = abs(current.low - previous.close)
            
            val trueRange = maxOf(tr1, tr2, tr3)
            trueRanges.add(trueRange)
        }
        
        // ATR是真实波幅的移动平均
        return calculateEMA(trueRanges, period)
    }
    
    // 布林带计算
    override fun calculateBollingerBands(prices: List<Double>, period: Int, stdDev: Double): List<BollingerBands> {
        if (prices.size < period) return emptyList()
        
        val result = mutableListOf<BollingerBands>()
        
        for (i in period - 1 until prices.size) {
            val window = prices.subList(i - period + 1, i + 1)
            val sma = window.average()
            
            // 计算标准差
            val variance = window.map { (it - sma).pow(2) }.average()
            val standardDeviation = sqrt(variance)
            
            val upperBand = sma + (stdDev * standardDeviation)
            val lowerBand = sma - (stdDev * standardDeviation)
            val bandwidth = (upperBand - lowerBand) / sma * 100
            val percentB = (prices[i] - lowerBand) / (upperBand - lowerBand)
            
            result.add(
                BollingerBands(
                    upperBand = upperBand,
                    middleBand = sma,
                    lowerBand = lowerBand,
                    bandwidth = bandwidth,
                    percentB = percentB
                )
            )
        }
        
        return result
    }
    
    // VWAP计算
    override fun calculateVWAP(klines: List<KlineData>): List<Double> {
        if (klines.isEmpty()) return emptyList()
        
        var cumulativePriceVolume = 0.0
        var cumulativeVolume = 0.0
        val vwapValues = mutableListOf<Double>()
        
        for (kline in klines) {
            val typicalPrice = (kline.high + kline.low + kline.close) / 3.0
            cumulativePriceVolume += typicalPrice * kline.volume
            cumulativeVolume += kline.volume
            
            val vwap = if (cumulativeVolume > 0) cumulativePriceVolume / cumulativeVolume else typicalPrice
            vwapValues.add(vwap)
        }
        
        return vwapValues
    }
    
    // CVD计算（基于聚合交易数据）
    override fun calculateCVD(aggTrades: List<BinanceAggTrade>): Double {
        var cvd = 0.0
        
        for (trade in aggTrades) {
            val volume = trade.quantity.toDouble()
            if (trade.isBuyerMaker) {
                cvd -= volume // 卖单
            } else {
                cvd += volume // 买单
            }
        }
        
        return cvd
    }
}