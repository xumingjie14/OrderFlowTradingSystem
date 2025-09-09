package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// 持仓量数据
@Entity(tableName = "open_interest")
data class OpenInterestData(
    @PrimaryKey
    val id: String, // symbol_timestamp
    val symbol: String,
    val openInterest: Double,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// 资金费率数据
@Entity(tableName = "funding_rates")
data class FundingRateData(
    @PrimaryKey
    val id: String, // symbol_fundingTime
    val symbol: String,
    val fundingRate: Double,
    val fundingTime: Long,
    val markPrice: Double? = null,
    val indexPrice: Double? = null,
    val estimatedSettlePrice: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// 清算数据
@Entity(tableName = "liquidations")
data class LiquidationData(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val orderType: String, // "LIMIT" or "MARKET"
    val timeInForce: String,
    val originalQuantity: Double,
    val price: Double,
    val averagePrice: Double,
    val orderStatus: String,
    val lastFilledQuantity: Double,
    val accumulatedQuantity: Double,
    val eventTime: Long,
    val transactionTime: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// 大户持仓比例
@Entity(tableName = "top_trader_positions")
data class TopTraderPositionData(
    @PrimaryKey
    val id: String, // symbol_timestamp
    val symbol: String,
    val longShortRatio: Double, // 多空比例
    val longAccount: Double, // 多头账户比例
    val shortAccount: Double, // 空头账户比例
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// 合约统计数据
@Entity(tableName = "futures_stats")
data class FuturesStatsData(
    @PrimaryKey
    val id: String, // symbol_timestamp
    val symbol: String,
    val priceChange: Double,
    val priceChangePercent: Double,
    val weightedAvgPrice: Double,
    val lastPrice: Double,
    val lastQty: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Double,
    val quoteVolume: Double,
    val openTime: Long,
    val closeTime: Long,
    val firstId: Long,
    val lastId: Long,
    val count: Long,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// 期权数据（如果需要）
@Entity(tableName = "options_data")
data class OptionsData(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val strikePrice: Double,
    val expiryDate: Long,
    val optionType: String, // "CALL" or "PUT"
    val impliedVolatility: Double,
    val delta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double,
    val openInterest: Double,
    val volume: Double,
    val lastPrice: Double,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// 币安API响应模型
data class BinanceOpenInterestResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("openInterest") val openInterest: String,
    @SerializedName("time") val time: Long
)

data class BinanceFundingRateResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("fundingRate") val fundingRate: String,
    @SerializedName("fundingTime") val fundingTime: Long,
    @SerializedName("markPrice") val markPrice: String? = null
)

data class BinanceLiquidationResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("price") val price: String,
    @SerializedName("origQty") val origQty: String,
    @SerializedName("executedQty") val executedQty: String,
    @SerializedName("avrPrice") val avrPrice: String,
    @SerializedName("status") val status: String,
    @SerializedName("timeInForce") val timeInForce: String,
    @SerializedName("type") val type: String,
    @SerializedName("side") val side: String,
    @SerializedName("time") val time: Long
)

data class BinanceTopTraderPositionResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("longShortRatio") val longShortRatio: String,
    @SerializedName("longAccount") val longAccount: String,
    @SerializedName("shortAccount") val shortAccount: String,
    @SerializedName("timestamp") val timestamp: Long
)

// 衍生品市场情绪指标
data class MarketSentimentIndicator(
    val symbol: String,
    val timestamp: Long,
    
    // 持仓量相关
    val openInterest: Double,
    val openInterestChange: Double, // 24h变化
    val openInterestChangePercent: Double,
    
    // 资金费率相关
    val fundingRate: Double,
    val fundingRateMA: Double, // 移动平均
    val fundingRateTrend: String, // "BULLISH", "BEARISH", "NEUTRAL"
    
    // 清算相关
    val liquidationVolume24h: Double,
    val longLiquidationRatio: Double, // 多头清算比例
    val shortLiquidationRatio: Double, // 空头清算比例
    
    // 大户持仓
    val topTraderLongRatio: Double,
    val topTraderShortRatio: Double,
    val topTraderSentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
    
    // 综合情绪评分
    val sentimentScore: Double, // -100 到 100
    val sentimentLevel: String // "EXTREMELY_BEARISH", "BEARISH", "NEUTRAL", "BULLISH", "EXTREMELY_BULLISH"
)

// 扩展函数
fun BinanceOpenInterestResponse.toOpenInterestData(): OpenInterestData {
    return OpenInterestData(
        id = "${symbol}_${time}",
        symbol = symbol,
        openInterest = openInterest.toDouble(),
        timestamp = time
    )
}

fun BinanceFundingRateResponse.toFundingRateData(): FundingRateData {
    return FundingRateData(
        id = "${symbol}_${fundingTime}",
        symbol = symbol,
        fundingRate = fundingRate.toDouble(),
        fundingTime = fundingTime,
        markPrice = markPrice?.toDoubleOrNull()
    )
}

fun BinanceLiquidationResponse.toLiquidationData(): LiquidationData {
    return LiquidationData(
        id = "${symbol}_${time}_${System.nanoTime()}",
        symbol = symbol,
        side = side,
        orderType = type,
        timeInForce = timeInForce,
        originalQuantity = origQty.toDouble(),
        price = price.toDouble(),
        averagePrice = avrPrice.toDouble(),
        orderStatus = status,
        lastFilledQuantity = 0.0,
        accumulatedQuantity = executedQty.toDouble(),
        eventTime = time,
        transactionTime = time
    )
}