package com.trading.orderflow.domain.signal

import com.trading.orderflow.data.model.*
import kotlin.math.*

// 因子计算器接口
interface FactorCalculator {
    fun calculateTrendFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore
    fun calculateMomentumFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore
    fun calculateVolumeFactor(indicator: IndicatorData, kline: KlineData, previousKlines: List<KlineData>): FactorScore
    fun calculateVolatilityFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore
    fun calculateDerivativesFactor(fundingRate: Double?, openInterest: Double?, previousOI: Double?): FactorScore
}

class FactorCalculatorImpl : FactorCalculator {
    
    companion object {
        private const val TREND_FACTOR_NAME = "趋势因子"
        private const val MOMENTUM_FACTOR_NAME = "动量因子"
        private const val VOLUME_FACTOR_NAME = "成交量因子"
        private const val VOLATILITY_FACTOR_NAME = "波动性因子"
        private const val DERIVATIVES_FACTOR_NAME = "衍生品因子"
    }
    
    // 趋势因子计算
    override fun calculateTrendFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore {
        var score = 0.0
        val indicators = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        val ema12 = indicator.ema12
        val ema26 = indicator.ema26
        val ema50 = indicator.ema50
        val ema200 = indicator.ema200
        
        if (ema12 != null && ema26 != null && ema50 != null && ema200 != null) {
            indicators["EMA12"] = ema12
            indicators["EMA26"] = ema26
            indicators["EMA50"] = ema50
            indicators["EMA200"] = ema200
            
            // EMA排列 (最重要的趋势信号)
            if (ema12 > ema26 && ema26 > ema50 && ema50 > ema200) {
                score += 3.0 // 完美多头排列
                reasons.add("EMA完美多头排列")
            } else if (ema12 > ema26 && ema26 > ema50) {
                score += 2.0 // 短期多头排列
                reasons.add("EMA短期多头排列")
            } else if (ema12 > ema26) {
                score += 1.0 // 基本多头信号
                reasons.add("EMA12上穿EMA26")
            } else if (ema12 < ema26 && ema26 < ema50 && ema50 < ema200) {
                score -= 3.0 // 完美空头排列
                reasons.add("EMA完美空头排列")
            } else if (ema12 < ema26 && ema26 < ema50) {
                score -= 2.0 // 短期空头排列
                reasons.add("EMA短期空头排列")
            } else if (ema12 < ema26) {
                score -= 1.0 // 基本空头信号
                reasons.add("EMA12下穿EMA26")
            }
            
            // 价格与EMA关系
            val price = indicator.price
            if (price > ema12 && price > ema26) {
                score += 1.0
                reasons.add("价格位于短期EMA之上")
            } else if (price < ema12 && price < ema26) {
                score -= 1.0
                reasons.add("价格位于短期EMA之下")
            }
            
            // EMA斜率 (趋势强度)
            if (previousIndicators.isNotEmpty()) {
                val prevIndicator = previousIndicators.first()
                val ema12Slope = prevIndicator.ema12?.let { (ema12 - it) / it } ?: 0.0
                val ema26Slope = prevIndicator.ema26?.let { (ema26 - it) / it } ?: 0.0
                
                if (ema12Slope > 0.001 && ema26Slope > 0.001) {
                    score += 1.0
                    reasons.add("EMA向上倾斜强劲")
                } else if (ema12Slope < -0.001 && ema26Slope < -0.001) {
                    score -= 1.0
                    reasons.add("EMA向下倾斜强劲")
                }
            }
        }
        
        return FactorScore(
            factorName = TREND_FACTOR_NAME,
            score = score.coerceIn(-5.0, 5.0),
            weight = 0.0, // 权重在外部设置
            weightedScore = 0.0,
            description = reasons.joinToString("; "),
            indicators = indicators
        )
    }
    
    // 动量因子计算
    override fun calculateMomentumFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore {
        var score = 0.0
        val indicators = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        val macd = indicator.macd
        val macdSignal = indicator.macdSignal
        val macdHistogram = indicator.macdHistogram
        val rsi = indicator.rsi
        
        // MACD分析
        if (macd != null && macdSignal != null && macdHistogram != null) {
            indicators["MACD"] = macd
            indicators["MACD_Signal"] = macdSignal
            indicators["MACD_Histogram"] = macdHistogram
            
            // MACD金叉死叉
            if (macd > macdSignal && macd > 0) {
                score += 2.0
                reasons.add("MACD金叉且位于零轴上方")
            } else if (macd > macdSignal) {
                score += 1.0
                reasons.add("MACD金叉")
            } else if (macd < macdSignal && macd < 0) {
                score -= 2.0
                reasons.add("MACD死叉且位于零轴下方")
            } else if (macd < macdSignal) {
                score -= 1.0
                reasons.add("MACD死叉")
            }
            
            // MACD柱状图变化
            if (previousIndicators.isNotEmpty()) {
                val prevHistogram = previousIndicators.first().macdHistogram
                if (prevHistogram != null) {
                    if (macdHistogram > prevHistogram && macdHistogram > 0) {
                        score += 1.0
                        reasons.add("MACD柱状图增强")
                    } else if (macdHistogram < prevHistogram && macdHistogram < 0) {
                        score -= 1.0
                        reasons.add("MACD柱状图减弱")
                    }
                }
            }
        }
        
        // RSI分析
        if (rsi != null) {
            indicators["RSI"] = rsi
            
            when {
                rsi > 70 -> {
                    score -= 1.0
                    reasons.add("RSI超买区域")
                }
                rsi > 50 && rsi <= 70 -> {
                    score += 1.0
                    reasons.add("RSI健康上行")
                }
                rsi >= 30 && rsi <= 50 -> {
                    // 中性区域，不加分
                }
                rsi < 30 -> {
                    score += 1.0 // 超卖反弹机会
                    reasons.add("RSI超卖区域")
                }
            }
            
            // RSI背离检测
            if (previousIndicators.size >= 2) {
                val prevRsi = previousIndicators[0].rsi
                val prevPrice = previousIndicators[0].price
                val currentPrice = indicator.price
                
                if (prevRsi != null) {
                    // 顶背离：价格新高，RSI未创新高
                    if (currentPrice > prevPrice && rsi < prevRsi && rsi > 70) {
                        score -= 2.0
                        reasons.add("RSI顶背离")
                    }
                    // 底背离：价格新低，RSI未创新低
                    else if (currentPrice < prevPrice && rsi > prevRsi && rsi < 30) {
                        score += 2.0
                        reasons.add("RSI底背离")
                    }
                }
            }
        }
        
        return FactorScore(
            factorName = MOMENTUM_FACTOR_NAME,
            score = score.coerceIn(-5.0, 5.0),
            weight = 0.0,
            weightedScore = 0.0,
            description = reasons.joinToString("; "),
            indicators = indicators
        )
    }
    
    // 成交量因子计算
    override fun calculateVolumeFactor(indicator: IndicatorData, kline: KlineData, previousKlines: List<KlineData>): FactorScore {
        var score = 0.0
        val indicators = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        val volume = kline.volume
        val cvd = indicator.cvd ?: kline.cvd
        val vwap = indicator.vwap
        val price = indicator.price
        
        indicators["Volume"] = volume
        indicators["CVD"] = cvd
        vwap?.let { indicators["VWAP"] = it }
        
        // 成交量分析
        if (previousKlines.isNotEmpty()) {
            val avgVolume = previousKlines.take(20).map { it.volume }.average()
            val volumeRatio = volume / avgVolume
            
            when {
                volumeRatio > 2.0 -> {
                    score += 2.0
                    reasons.add("成交量异常放大")
                }
                volumeRatio > 1.5 -> {
                    score += 1.0
                    reasons.add("成交量明显放大")
                }
                volumeRatio < 0.5 -> {
                    score -= 1.0
                    reasons.add("成交量萎缩")
                }
            }
        }
        
        // CVD分析
        if (previousKlines.isNotEmpty()) {
            val prevCvd = previousKlines.first().cvd
            val cvdChange = cvd - prevCvd
            
            if (cvdChange > 0 && price > previousKlines.first().close) {
                score += 1.0
                reasons.add("CVD与价格同向上涨")
            } else if (cvdChange < 0 && price < previousKlines.first().close) {
                score += 1.0
                reasons.add("CVD与价格同向下跌")
            } else if (cvdChange > 0 && price < previousKlines.first().close) {
                score -= 1.0
                reasons.add("CVD与价格背离(买盘强但价格跌)")
            } else if (cvdChange < 0 && price > previousKlines.first().close) {
                score -= 1.0
                reasons.add("CVD与价格背离(卖盘强但价格涨)")
            }
        }
        
        // VWAP分析
        if (vwap != null) {
            if (price > vwap * 1.002) {
                score += 1.0
                reasons.add("价格显著高于VWAP")
            } else if (price < vwap * 0.998) {
                score -= 1.0
                reasons.add("价格显著低于VWAP")
            }
        }
        
        return FactorScore(
            factorName = VOLUME_FACTOR_NAME,
            score = score.coerceIn(-5.0, 5.0),
            weight = 0.0,
            weightedScore = 0.0,
            description = reasons.joinToString("; "),
            indicators = indicators
        )
    }
    
    // 波动性因子计算
    override fun calculateVolatilityFactor(indicator: IndicatorData, previousIndicators: List<IndicatorData>): FactorScore {
        var score = 0.0
        val indicators = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        val atr = indicator.atr
        val bollingerUpper = indicator.bollingerUpper
        val bollingerLower = indicator.bollingerLower
        val bollingerMiddle = indicator.bollingerMiddle
        val price = indicator.price
        
        // ATR分析
        if (atr != null) {
            indicators["ATR"] = atr
            
            if (previousIndicators.isNotEmpty()) {
                val prevAtr = previousIndicators.first().atr
                if (prevAtr != null) {
                    val atrChange = (atr - prevAtr) / prevAtr
                    
                    if (atrChange > 0.2) {
                        score += 1.0
                        reasons.add("波动性显著增加")
                    } else if (atrChange < -0.2) {
                        score -= 1.0
                        reasons.add("波动性显著减少")
                    }
                }
            }
        }
        
        // 布林带分析
        if (bollingerUpper != null && bollingerLower != null && bollingerMiddle != null) {
            indicators["BB_Upper"] = bollingerUpper
            indicators["BB_Lower"] = bollingerLower
            indicators["BB_Middle"] = bollingerMiddle
            
            val bbWidth = (bollingerUpper - bollingerLower) / bollingerMiddle
            val pricePosition = (price - bollingerLower) / (bollingerUpper - bollingerLower)
            
            // 布林带宽度分析
            if (previousIndicators.isNotEmpty()) {
                val prevUpper = previousIndicators.first().bollingerUpper
                val prevLower = previousIndicators.first().bollingerLower
                val prevMiddle = previousIndicators.first().bollingerMiddle
                
                if (prevUpper != null && prevLower != null && prevMiddle != null) {
                    val prevBbWidth = (prevUpper - prevLower) / prevMiddle
                    
                    if (bbWidth > prevBbWidth * 1.1) {
                        score += 1.0
                        reasons.add("布林带扩张")
                    } else if (bbWidth < prevBbWidth * 0.9) {
                        score -= 1.0
                        reasons.add("布林带收缩")
                    }
                }
            }
            
            // 价格在布林带中的位置
            when {
                pricePosition > 0.8 -> {
                    score -= 1.0
                    reasons.add("价格接近布林带上轨")
                }
                pricePosition < 0.2 -> {
                    score += 1.0
                    reasons.add("价格接近布林带下轨")
                }
                pricePosition > 0.6 -> {
                    score += 0.5
                    reasons.add("价格位于布林带上半部")
                }
                pricePosition < 0.4 -> {
                    score -= 0.5
                    reasons.add("价格位于布林带下半部")
                }
            }
        }
        
        return FactorScore(
            factorName = VOLATILITY_FACTOR_NAME,
            score = score.coerceIn(-5.0, 5.0),
            weight = 0.0,
            weightedScore = 0.0,
            description = reasons.joinToString("; "),
            indicators = indicators
        )
    }
    
    // 衍生品因子计算
    override fun calculateDerivativesFactor(fundingRate: Double?, openInterest: Double?, previousOI: Double?): FactorScore {
        var score = 0.0
        val indicators = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        // 资金费率分析
        if (fundingRate != null) {
            indicators["FundingRate"] = fundingRate
            
            when {
                fundingRate > 0.01 -> {
                    score -= 2.0
                    reasons.add("资金费率过高，多头付费压力大")
                }
                fundingRate > 0.005 -> {
                    score -= 1.0
                    reasons.add("资金费率偏高")
                }
                fundingRate < -0.01 -> {
                    score += 2.0
                    reasons.add("资金费率为负，空头付费")
                }
                fundingRate < -0.005 -> {
                    score += 1.0
                    reasons.add("资金费率偏低")
                }
            }
        }
        
        // 持仓量分析
        if (openInterest != null && previousOI != null) {
            indicators["OpenInterest"] = openInterest
            
            val oiChange = (openInterest - previousOI) / previousOI
            
            when {
                oiChange > 0.1 -> {
                    score += 1.0
                    reasons.add("持仓量大幅增加")
                }
                oiChange > 0.05 -> {
                    score += 0.5
                    reasons.add("持仓量增加")
                }
                oiChange < -0.1 -> {
                    score -= 1.0
                    reasons.add("持仓量大幅减少")
                }
                oiChange < -0.05 -> {
                    score -= 0.5
                    reasons.add("持仓量减少")
                }
            }
        }
        
        return FactorScore(
            factorName = DERIVATIVES_FACTOR_NAME,
            score = score.coerceIn(-5.0, 5.0),
            weight = 0.0,
            weightedScore = 0.0,
            description = reasons.joinToString("; "),
            indicators = indicators
        )
    }
}