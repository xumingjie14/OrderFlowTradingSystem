package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 技术指标基础数据结构
data class IndicatorValue(
    val timestamp: Long,
    val value: Double,
    val isValid: Boolean = true
)

// EMA指标数据
data class EMAIndicator(
    val period: Int,
    val currentValue: Double,
    val previousValue: Double? = null,
    val alpha: Double = 2.0 / (period + 1)
)

// MACD指标数据
data class MACDIndicator(
    val macd: Double,
    val signal: Double,
    val histogram: Double,
    val ema12: Double,
    val ema26: Double
)

// RSI指标数据
data class RSIIndicator(
    val rsi: Double,
    val avgGain: Double,
    val avgLoss: Double,
    val period: Int = 14
)

// ATR指标数据
data class ATRIndicator(
    val atr: Double,
    val trueRange: Double,
    val period: Int = 14
)

// Bollinger Bands指标数据
data class BollingerBands(
    val upperBand: Double,
    val middleBand: Double, // SMA
    val lowerBand: Double,
    val bandwidth: Double,
    val percentB: Double
)

// VWAP指标数据
data class VWAPIndicator(
    val vwap: Double,
    val cumulativeVolume: Double,
    val cumulativePriceVolume: Double
)

// CVD (Cumulative Volume Delta) 指标数据
data class CVDIndicator(
    val cvd: Double,
    val buyVolume: Double,
    val sellVolume: Double,
    val netVolume: Double
)

// 综合指标数据（存储到数据库）
@Entity(tableName = "indicators")
data class IndicatorData(
    @PrimaryKey
    val id: String, // symbol_interval_timestamp
    val symbol: String,
    val interval: String,
    val timestamp: Long,
    val price: Double,
    val volume: Double,
    
    // 趋势指标
    val ema12: Double? = null,
    val ema26: Double? = null,
    val ema50: Double? = null,
    val ema200: Double? = null,
    val sma20: Double? = null,
    
    // 动量指标
    val macd: Double? = null,
    val macdSignal: Double? = null,
    val macdHistogram: Double? = null,
    val rsi: Double? = null,
    
    // 波动性指标
    val atr: Double? = null,
    val bollingerUpper: Double? = null,
    val bollingerMiddle: Double? = null,
    val bollingerLower: Double? = null,
    
    // 成交量指标
    val vwap: Double? = null,
    val cvd: Double? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)