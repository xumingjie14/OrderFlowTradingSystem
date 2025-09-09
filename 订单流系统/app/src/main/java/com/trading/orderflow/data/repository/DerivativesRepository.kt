package com.trading.orderflow.data.repository

import android.util.Log
import com.trading.orderflow.data.api.DerivativesApiService
import com.trading.orderflow.data.database.DerivativesDao
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DerivativesRepository @Inject constructor(
    private val derivativesApi: DerivativesApiService,
    private val derivativesDao: DerivativesDao
) {
    
    companion object {
        private const val TAG = "DerivativesRepository"
        private const val CACHE_DURATION = 5 * 60 * 1000L // 5分钟缓存
    }
    
    // 获取持仓量数据
    suspend fun getOpenInterest(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result<OpenInterestData> = withContext(Dispatchers.IO) {
        try {
            // 检查缓存
            if (!forceRefresh) {
                val cached = derivativesDao.getLatestOpenInterest(symbol)
                if (cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_DURATION) {
                    return@withContext Result.success(cached)
                }
            }
            
            // 从API获取
            val response = derivativesApi.getOpenInterest(symbol)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.toOpenInterestData()
                derivativesDao.insertOpenInterest(data)
                Result.success(data)
            } else {
                Result.failure(Exception("Failed to get open interest: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting open interest: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取持仓量历史数据
    suspend fun getOpenInterestHistory(
        symbol: String,
        period: String = "1h",
        limit: Int = 24
    ): Result<List<OpenInterestData>> = withContext(Dispatchers.IO) {
        try {
            val response = derivativesApi.getOpenInterestHistory(symbol, period, limit)
            if (response.isSuccessful && response.body() != null) {
                val dataList = response.body()!!.map { it.toOpenInterestData() }
                derivativesDao.insertOpenInterestBatch(dataList)
                Result.success(dataList)
            } else {
                Result.failure(Exception("Failed to get OI history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting OI history: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取资金费率
    suspend fun getCurrentFundingRate(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result<FundingRateData> = withContext(Dispatchers.IO) {
        try {
            // 检查缓存
            if (!forceRefresh) {
                val cached = derivativesDao.getLatestFundingRate(symbol)
                if (cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_DURATION) {
                    return@withContext Result.success(cached)
                }
            }
            
            val response = derivativesApi.getCurrentFundingRate(symbol)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.toFundingRateData()
                derivativesDao.insertFundingRate(data)
                Result.success(data)
            } else {
                Result.failure(Exception("Failed to get funding rate: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting funding rate: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取资金费率历史
    suspend fun getFundingRateHistory(
        symbol: String,
        limit: Int = 100
    ): Result<List<FundingRateData>> = withContext(Dispatchers.IO) {
        try {
            val response = derivativesApi.getFundingRate(symbol, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                val dataList = response.body()!!.map { it.toFundingRateData() }
                derivativesDao.insertFundingRateBatch(dataList)
                Result.success(dataList)
            } else {
                Result.failure(Exception("Failed to get funding rate history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting funding rate history: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取清算数据
    suspend fun getLiquidationData(
        symbol: String? = null,
        limit: Int = 100
    ): Result<List<LiquidationData>> = withContext(Dispatchers.IO) {
        try {
            val response = derivativesApi.getLiquidationOrders(symbol, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                val dataList = response.body()!!.map { it.toLiquidationData() }
                derivativesDao.insertLiquidationBatch(dataList)
                Result.success(dataList)
            } else {
                Result.failure(Exception("Failed to get liquidation data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting liquidation data: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取大户持仓数据
    suspend fun getTopTraderPositions(
        symbol: String,
        period: String = "1h",
        limit: Int = 24
    ): Result<List<TopTraderPositionData>> = withContext(Dispatchers.IO) {
        try {
            val response = derivativesApi.getTopTraderPositionRatio(symbol, period, limit)
            if (response.isSuccessful && response.body() != null) {
                val dataList = response.body()!!.map { resp ->
                    TopTraderPositionData(
                        id = "${resp.symbol}_${resp.timestamp}",
                        symbol = resp.symbol,
                        longShortRatio = resp.longShortRatio.toDouble(),
                        longAccount = resp.longAccount.toDouble(),
                        shortAccount = resp.shortAccount.toDouble(),
                        timestamp = resp.timestamp
                    )
                }
                derivativesDao.insertTopTraderPositionBatch(dataList)
                Result.success(dataList)
            } else {
                Result.failure(Exception("Failed to get top trader positions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top trader positions: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 计算市场情绪指标
    suspend fun calculateMarketSentiment(symbol: String): Result<MarketSentimentIndicator> = withContext(Dispatchers.IO) {
        try {
            // 获取各项数据
            val openInterestResult = getOpenInterest(symbol)
            val fundingRateResult = getCurrentFundingRate(symbol)
            val topTraderResult = getTopTraderPositions(symbol, limit = 1)
            val liquidationResult = getLiquidationData(symbol, limit = 100)
            
            if (openInterestResult.isFailure || fundingRateResult.isFailure) {
                return@withContext Result.failure(Exception("Failed to get required data for sentiment calculation"))
            }
            
            val openInterest = openInterestResult.getOrThrow()
            val fundingRate = fundingRateResult.getOrThrow()
            val topTrader = topTraderResult.getOrNull()?.firstOrNull()
            val liquidations = liquidationResult.getOrNull() ?: emptyList()
            
            // 计算持仓量变化
            val previousOI = derivativesDao.getOpenInterestHistory(symbol, 2).getOrNull(1)
            val oiChange = if (previousOI != null) {
                openInterest.openInterest - previousOI.openInterest
            } else 0.0
            val oiChangePercent = if (previousOI != null && previousOI.openInterest > 0) {
                (oiChange / previousOI.openInterest) * 100
            } else 0.0
            
            // 计算资金费率趋势
            val fundingRateHistory = derivativesDao.getFundingRateHistory(symbol, 10)
            val fundingRateMA = if (fundingRateHistory.isNotEmpty()) {
                fundingRateHistory.map { it.fundingRate }.average()
            } else fundingRate.fundingRate
            
            val fundingRateTrend = when {
                fundingRate.fundingRate > fundingRateMA * 1.2 -> "BULLISH"
                fundingRate.fundingRate < fundingRateMA * 0.8 -> "BEARISH"
                else -> "NEUTRAL"
            }
            
            // 计算清算数据
            val longLiquidations = liquidations.filter { it.side == "SELL" } // 多头被清算
            val shortLiquidations = liquidations.filter { it.side == "BUY" } // 空头被清算
            val totalLiquidationVolume = liquidations.sumOf { it.originalQuantity }
            val longLiquidationRatio = if (totalLiquidationVolume > 0) {
                longLiquidations.sumOf { it.originalQuantity } / totalLiquidationVolume
            } else 0.0
            val shortLiquidationRatio = 1.0 - longLiquidationRatio
            
            // 大户情绪
            val topTraderSentiment = if (topTrader != null) {
                when {
                    topTrader.longShortRatio > 1.2 -> "BULLISH"
                    topTrader.longShortRatio < 0.8 -> "BEARISH"
                    else -> "NEUTRAL"
                }
            } else "NEUTRAL"
            
            // 综合情绪评分计算
            var sentimentScore = 0.0
            
            // 资金费率影响 (-30 到 +30)
            sentimentScore += when {
                fundingRate.fundingRate > 0.01 -> -30 // 极高资金费率，看空
                fundingRate.fundingRate > 0.005 -> -15
                fundingRate.fundingRate > 0.001 -> -5
                fundingRate.fundingRate < -0.01 -> 30 // 负资金费率，看多
                fundingRate.fundingRate < -0.005 -> 15
                fundingRate.fundingRate < -0.001 -> 5
                else -> 0
            }
            
            // 持仓量变化影响 (-20 到 +20)
            sentimentScore += when {
                oiChangePercent > 10 -> 20 // 持仓量大幅增加
                oiChangePercent > 5 -> 10
                oiChangePercent > 2 -> 5
                oiChangePercent < -10 -> -20 // 持仓量大幅减少
                oiChangePercent < -5 -> -10
                oiChangePercent < -2 -> -5
                else -> 0
            }
            
            // 清算数据影响 (-25 到 +25)
            sentimentScore += when {
                longLiquidationRatio > 0.7 -> 25 // 多头大量清算，可能见底
                longLiquidationRatio > 0.6 -> 15
                shortLiquidationRatio > 0.7 -> -25 // 空头大量清算，可能见顶
                shortLiquidationRatio > 0.6 -> -15
                else -> 0
            }
            
            // 大户持仓影响 (-25 到 +25)
            if (topTrader != null) {
                sentimentScore += when {
                    topTrader.longShortRatio > 2.0 -> 25
                    topTrader.longShortRatio > 1.5 -> 15
                    topTrader.longShortRatio > 1.2 -> 10
                    topTrader.longShortRatio < 0.5 -> -25
                    topTrader.longShortRatio < 0.7 -> -15
                    topTrader.longShortRatio < 0.8 -> -10
                    else -> 0
                }
            }
            
            // 限制评分范围
            sentimentScore = sentimentScore.coerceIn(-100.0, 100.0)
            
            // 确定情绪等级
            val sentimentLevel = when {
                sentimentScore >= 60 -> "EXTREMELY_BULLISH"
                sentimentScore >= 30 -> "BULLISH"
                sentimentScore >= -30 -> "NEUTRAL"
                sentimentScore >= -60 -> "BEARISH"
                else -> "EXTREMELY_BEARISH"
            }
            
            val sentiment = MarketSentimentIndicator(
                symbol = symbol,
                timestamp = System.currentTimeMillis(),
                openInterest = openInterest.openInterest,
                openInterestChange = oiChange,
                openInterestChangePercent = oiChangePercent,
                fundingRate = fundingRate.fundingRate,
                fundingRateMA = fundingRateMA,
                fundingRateTrend = fundingRateTrend,
                liquidationVolume24h = totalLiquidationVolume,
                longLiquidationRatio = longLiquidationRatio,
                shortLiquidationRatio = shortLiquidationRatio,
                topTraderLongRatio = topTrader?.longAccount ?: 0.5,
                topTraderShortRatio = topTrader?.shortAccount ?: 0.5,
                topTraderSentiment = topTraderSentiment,
                sentimentScore = sentimentScore,
                sentimentLevel = sentimentLevel
            )
            
            Result.success(sentiment)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating market sentiment: ${e.message}")
            Result.failure(e)
        }
    }
    
    // 获取数据流
    fun getOpenInterestFlow(symbol: String): Flow<OpenInterestData?> {
        return derivativesDao.getOpenInterestFlow(symbol)
    }
    
    fun getFundingRateFlow(symbol: String): Flow<FundingRateData?> {
        return derivativesDao.getFundingRateFlow(symbol)
    }
    
    fun getLiquidationFlow(symbol: String): Flow<List<LiquidationData>> {
        return derivativesDao.getLiquidationFlow(symbol)
    }
}