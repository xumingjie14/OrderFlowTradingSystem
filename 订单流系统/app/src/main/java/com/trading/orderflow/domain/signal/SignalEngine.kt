package com.trading.orderflow.domain.signal

import android.util.Log
import com.trading.orderflow.data.database.SignalDao
import com.trading.orderflow.data.model.*
import com.trading.orderflow.data.repository.MarketDataRepository
import com.trading.orderflow.domain.service.IndicatorService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SignalEngine @Inject constructor(
    private val factorCalculator: FactorCalculator,
    private val signalDao: SignalDao,
    private val indicatorService: IndicatorService,
    private val marketDataRepository: MarketDataRepository
) {
    
    companion object {
        private const val TAG = "SignalEngine"
        private const val LOOKBACK_PERIODS = 20 // 回看周期数
    }
    
    private val _signalFlow = MutableSharedFlow<TradingSignal>()
    val signalFlow: SharedFlow<TradingSignal> = _signalFlow.asSharedFlow()
    
    // 生成交易信号
    suspend fun generateSignal(
        symbol: String,
        interval: String,
        currentIndicator: IndicatorData,
        currentKline: KlineData,
        fundingRate: Double? = null,
        openInterest: Double? = null
    ): SignalGenerationResult = withContext(Dispatchers.Default) {
        
        try {
            val config = getSignalConfig()
            
            // 获取历史数据
            val previousIndicators = indicatorService.getIndicatorFlow(symbol, interval)
                .first()
                .filter { it.timestamp < currentIndicator.timestamp }
                .sortedByDescending { it.timestamp }
                .take(LOOKBACK_PERIODS)
            
            val previousKlines = marketDataRepository.getHistoricalKlines(symbol, interval, LOOKBACK_PERIODS + 1)
                .getOrNull()
                ?.filter { it.openTime < currentKline.openTime }
                ?.sortedByDescending { it.openTime }
                ?.take(LOOKBACK_PERIODS)
                ?: emptyList()
            
            val previousOI = previousIndicators.firstOrNull()?.let { openInterest }
            
            // 计算各因子得分
            val trendFactor = factorCalculator.calculateTrendFactor(currentIndicator, previousIndicators)
                .copy(weight = config.trendWeight, weightedScore = 0.0)
                .let { it.copy(weightedScore = it.score * it.weight) }
            
            val momentumFactor = factorCalculator.calculateMomentumFactor(currentIndicator, previousIndicators)
                .copy(weight = config.momentumWeight, weightedScore = 0.0)
                .let { it.copy(weightedScore = it.score * it.weight) }
            
            val volumeFactor = factorCalculator.calculateVolumeFactor(currentIndicator, currentKline, previousKlines)
                .copy(weight = config.volumeWeight, weightedScore = 0.0)
                .let { it.copy(weightedScore = it.score * it.weight) }
            
            val volatilityFactor = factorCalculator.calculateVolatilityFactor(currentIndicator, previousIndicators)
                .copy(weight = config.volatilityWeight, weightedScore = 0.0)
                .let { it.copy(weightedScore = it.score * it.weight) }
            
            val derivativesFactor = factorCalculator.calculateDerivativesFactor(fundingRate, openInterest, previousOI)
                .copy(weight = config.derivativesWeight, weightedScore = 0.0)
                .let { it.copy(weightedScore = it.score * it.weight) }
            
            val factorScores = listOf(trendFactor, momentumFactor, volumeFactor, volatilityFactor, derivativesFactor)
            
            // 计算总分和置信度
            val totalScore = factorScores.sumOf { it.weightedScore }
            val confidence = calculateConfidence(factorScores, config)
            
            // 生成信号
            val signal = createSignal(
                symbol = symbol,
                interval = interval,
                currentIndicator = currentIndicator,
                totalScore = totalScore,
                confidence = confidence,
                factorScores = factorScores,
                config = config,
                fundingRate = fundingRate,
                openInterest = openInterest
            )
            
            // 收集原因和警告
            val reasons = factorScores.filter { abs(it.score) > 0.5 }
                .map { "${it.factorName}: ${it.description}" }
            
            val warnings = mutableListOf<String>()
            if (confidence < config.minConfidence) {
                warnings.add("信号置信度较低: ${String.format("%.2f", confidence)}")
            }
            
            // 保存信号
            signal?.let {
                signalDao.insertSignal(it)
                _signalFlow.emit(it)
                Log.d(TAG, "Generated signal: ${it.signalType} for ${it.symbol} with score ${it.totalScore}")
            }
            
            return@withContext SignalGenerationResult(
                signal = signal,
                factorScores = factorScores,
                totalScore = totalScore,
                confidence = confidence,
                reasons = reasons,
                warnings = warnings
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating signal: ${e.message}")
            return@withContext SignalGenerationResult(
                signal = null,
                factorScores = emptyList(),
                totalScore = 0.0,
                confidence = 0.0,
                reasons = emptyList(),
                warnings = listOf("信号生成失败: ${e.message}")
            )
        }
    }
    
    // 创建交易信号
    private fun createSignal(
        symbol: String,
        interval: String,
        currentIndicator: IndicatorData,
        totalScore: Double,
        confidence: Double,
        factorScores: List<FactorScore>,
        config: SignalConfig,
        fundingRate: Double?,
        openInterest: Double?
    ): TradingSignal? {
        
        // 判断信号类型和强度
        val (signalType, strength) = when {
            totalScore >= config.strongSignalThreshold -> SignalType.LONG to SignalStrength.STRONG
            totalScore >= config.mediumSignalThreshold -> SignalType.LONG to SignalStrength.MEDIUM
            totalScore >= config.weakSignalThreshold -> SignalType.LONG to SignalStrength.WEAK
            totalScore <= -config.strongSignalThreshold -> SignalType.SHORT to SignalStrength.STRONG
            totalScore <= -config.mediumSignalThreshold -> SignalType.SHORT to SignalStrength.MEDIUM
            totalScore <= -config.weakSignalThreshold -> SignalType.SHORT to SignalStrength.WEAK
            else -> return null // 信号不够强
        }
        
        // 检查过滤条件
        if (confidence < config.minConfidence) {
            return null
        }
        
        if (config.requireTrendConfirmation) {
            val trendScore = factorScores.find { it.factorName.contains("趋势") }?.score ?: 0.0
            if (signalType == SignalType.LONG && trendScore < 1.0) return null
            if (signalType == SignalType.SHORT && trendScore > -1.0) return null
        }
        
        if (config.requireVolumeConfirmation) {
            val volumeScore = factorScores.find { it.factorName.contains("成交量") }?.score ?: 0.0
            if (abs(volumeScore) < 0.5) return null
        }
        
        // 计算建议的交易参数
        val atr = currentIndicator.atr ?: 0.0
        val price = currentIndicator.price
        
        val suggestedStopLoss = when (signalType) {
            SignalType.LONG -> price - (atr * config.defaultStopLossATRMultiple)
            SignalType.SHORT -> price + (atr * config.defaultStopLossATRMultiple)
            else -> null
        }
        
        val suggestedTakeProfit = suggestedStopLoss?.let { sl ->
            val riskDistance = abs(price - sl)
            when (signalType) {
                SignalType.LONG -> price + (riskDistance * config.defaultTakeProfitRatio)
                SignalType.SHORT -> price - (riskDistance * config.defaultTakeProfitRatio)
                else -> null
            }
        }
        
        val riskRewardRatio = if (suggestedStopLoss != null && suggestedTakeProfit != null) {
            val risk = abs(price - suggestedStopLoss)
            val reward = abs(suggestedTakeProfit - price)
            if (risk > 0) reward / risk else 0.0
        } else null
        
        // 根据信号强度调整杠杆
        val suggestedLeverage = when (strength) {
            SignalStrength.VERY_STRONG -> config.maxLeverageForSignal
            SignalStrength.STRONG -> config.maxLeverageForSignal * 0.8
            SignalStrength.MEDIUM -> config.maxLeverageForSignal * 0.6
            SignalStrength.WEAK -> config.maxLeverageForSignal * 0.4
        }
        
        return TradingSignal(
            id = "${symbol}_${interval}_${System.currentTimeMillis()}",
            symbol = symbol,
            interval = interval,
            timestamp = currentIndicator.timestamp,
            price = price,
            signalType = signalType,
            strength = strength,
            confidence = confidence,
            totalScore = totalScore,
            trendScore = factorScores.find { it.factorName.contains("趋势") }?.score ?: 0.0,
            momentumScore = factorScores.find { it.factorName.contains("动量") }?.score ?: 0.0,
            volumeScore = factorScores.find { it.factorName.contains("成交量") }?.score ?: 0.0,
            volatilityScore = factorScores.find { it.factorName.contains("波动性") }?.score ?: 0.0,
            derivativesScore = factorScores.find { it.factorName.contains("衍生品") }?.score ?: 0.0,
            ema12 = currentIndicator.ema12,
            ema26 = currentIndicator.ema26,
            ema50 = currentIndicator.ema50,
            ema200 = currentIndicator.ema200,
            macd = currentIndicator.macd,
            macdSignal = currentIndicator.macdSignal,
            rsi = currentIndicator.rsi,
            atr = currentIndicator.atr,
            cvd = currentIndicator.cvd,
            vwap = currentIndicator.vwap,
            fundingRate = fundingRate,
            openInterest = openInterest,
            suggestedStopLoss = suggestedStopLoss,
            suggestedTakeProfit = suggestedTakeProfit,
            suggestedLeverage = suggestedLeverage,
            riskRewardRatio = riskRewardRatio,
            expiryTime = System.currentTimeMillis() + (config.signalExpiryMinutes * 60 * 1000L)
        )
    }
    
    // 计算置信度
    private fun calculateConfidence(factorScores: List<FactorScore>, config: SignalConfig): Double {
        val totalWeight = factorScores.sumOf { it.weight }
        if (totalWeight == 0.0) return 0.0
        
        // 基于因子一致性计算置信度
        val positiveScores = factorScores.count { it.score > 0.5 }
        val negativeScores = factorScores.count { it.score < -0.5 }
        val neutralScores = factorScores.size - positiveScores - negativeScores
        
        val consistency = when {
            positiveScores > negativeScores -> positiveScores.toDouble() / factorScores.size
            negativeScores > positiveScores -> negativeScores.toDouble() / factorScores.size
            else -> 0.5 // 中性
        }
        
        // 基于分数强度调整置信度
        val avgAbsScore = factorScores.map { abs(it.score) }.average()
        val strengthFactor = (avgAbsScore / 3.0).coerceIn(0.0, 1.0)
        
        return (consistency * 0.7 + strengthFactor * 0.3).coerceIn(0.0, 1.0)
    }
    
    // 获取信号配置
    suspend fun getSignalConfig(): SignalConfig {
        return signalDao.getSignalConfig() ?: SignalConfig()
    }
    
    // 更新信号配置
    suspend fun updateSignalConfig(config: SignalConfig) {
        signalDao.insertSignalConfig(config)
    }
    
    // 获取活跃信号
    suspend fun getActiveSignals(symbol: String? = null): List<TradingSignal> {
        val currentTime = System.currentTimeMillis()
        return if (symbol != null) {
            signalDao.getActiveSignalsBySymbol(symbol, currentTime)
        } else {
            signalDao.getActiveSignals(currentTime)
        }
    }
    
    // 获取信号历史
    suspend fun getSignalHistory(symbol: String, limit: Int = 100): List<TradingSignal> {
        return signalDao.getSignalsBySymbol(symbol, limit)
    }
    
    // 使信号失效
    suspend fun deactivateSignal(signalId: String, reason: String) {
        signalDao.deactivateSignal(signalId, reason)
    }
    
    // 清理过期信号
    suspend fun cleanupExpiredSignals() {
        val currentTime = System.currentTimeMillis()
        signalDao.deactivateExpiredSignals(currentTime)
    }
}