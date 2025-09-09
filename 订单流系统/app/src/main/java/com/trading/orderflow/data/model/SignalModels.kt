package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 交易信号
@Entity(tableName = "trading_signals")
data class TradingSignal(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val interval: String,
    val timestamp: Long,
    val price: Double,
    
    // 信号基本信息
    val signalType: SignalType,
    val strength: SignalStrength,
    val confidence: Double, // 0.0 - 1.0
    val totalScore: Double,
    
    // 各因子得分
    val trendScore: Double,
    val momentumScore: Double,
    val volumeScore: Double,
    val volatilityScore: Double,
    val derivativesScore: Double,
    
    // 具体指标值
    val ema12: Double? = null,
    val ema26: Double? = null,
    val ema50: Double? = null,
    val ema200: Double? = null,
    val macd: Double? = null,
    val macdSignal: Double? = null,
    val rsi: Double? = null,
    val atr: Double? = null,
    val cvd: Double? = null,
    val vwap: Double? = null,
    val fundingRate: Double? = null,
    val openInterest: Double? = null,
    
    // 建议交易参数
    val suggestedStopLoss: Double? = null,
    val suggestedTakeProfit: Double? = null,
    val suggestedLeverage: Double = 1.0,
    val riskRewardRatio: Double? = null,
    
    // 信号状态
    val isActive: Boolean = true,
    val expiryTime: Long? = null,
    val notes: String? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)

enum class SignalType {
    LONG, SHORT, NEUTRAL, CLOSE_LONG, CLOSE_SHORT
}

enum class SignalStrength {
    WEAK, MEDIUM, STRONG, VERY_STRONG
}

// 信号配置
@Entity(tableName = "signal_config")
data class SignalConfig(
    @PrimaryKey
    val id: String = "default",
    
    // 因子权重配置
    val trendWeight: Double = 0.30, // 趋势权重30%
    val momentumWeight: Double = 0.25, // 动量权重25%
    val volumeWeight: Double = 0.20, // 成交量权重20%
    val volatilityWeight: Double = 0.15, // 波动性权重15%
    val derivativesWeight: Double = 0.10, // 衍生品权重10%
    
    // 信号阈值
    val strongSignalThreshold: Double = 7.0, // 强信号阈值
    val mediumSignalThreshold: Double = 5.0, // 中等信号阈值
    val weakSignalThreshold: Double = 3.0, // 弱信号阈值
    
    // 过滤条件
    val minConfidence: Double = 0.6, // 最小置信度
    val requireTrendConfirmation: Boolean = true, // 需要趋势确认
    val requireVolumeConfirmation: Boolean = true, // 需要成交量确认
    
    // 信号有效期
    val signalExpiryMinutes: Int = 240, // 4小时信号有效期
    
    // 风险参数
    val defaultStopLossATRMultiple: Double = 1.5, // 默认止损ATR倍数
    val defaultTakeProfitRatio: Double = 3.0, // 默认止盈比例
    val maxLeverageForSignal: Double = 3.0, // 信号最大杠杆
    
    val updatedAt: Long = System.currentTimeMillis()
)

// 因子评分详情
data class FactorScore(
    val factorName: String,
    val score: Double,
    val weight: Double,
    val weightedScore: Double,
    val description: String,
    val indicators: Map<String, Double> = emptyMap()
)

// 信号生成结果
data class SignalGenerationResult(
    val signal: TradingSignal?,
    val factorScores: List<FactorScore>,
    val totalScore: Double,
    val confidence: Double,
    val reasons: List<String>,
    val warnings: List<String> = emptyList()
)