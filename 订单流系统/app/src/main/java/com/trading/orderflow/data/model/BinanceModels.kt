package com.trading.orderflow.data.model

import com.google.gson.annotations.SerializedName
import androidx.room.Entity
import androidx.room.PrimaryKey

// 币安K线数据响应模型
data class BinanceKlineResponse(
    val data: List<List<String>>
)

// WebSocket K线数据模型
data class BinanceWebSocketKline(
    @SerializedName("e") val eventType: String,
    @SerializedName("E") val eventTime: Long,
    @SerializedName("s") val symbol: String,
    @SerializedName("k") val kline: BinanceKlineData
)

data class BinanceKlineData(
    @SerializedName("t") val openTime: Long,
    @SerializedName("T") val closeTime: Long,
    @SerializedName("s") val symbol: String,
    @SerializedName("i") val interval: String,
    @SerializedName("f") val firstTradeId: Long,
    @SerializedName("L") val lastTradeId: Long,
    @SerializedName("o") val open: String,
    @SerializedName("c") val close: String,
    @SerializedName("h") val high: String,
    @SerializedName("l") val low: String,
    @SerializedName("v") val volume: String,
    @SerializedName("n") val tradeCount: Int,
    @SerializedName("x") val isClosed: Boolean,
    @SerializedName("q") val quoteVolume: String,
    @SerializedName("V") val takerBuyBaseVolume: String,
    @SerializedName("Q") val takerBuyQuoteVolume: String
)

// 聚合交易数据模型（用于CVD计算）
data class BinanceAggTrade(
    @SerializedName("e") val eventType: String,
    @SerializedName("E") val eventTime: Long,
    @SerializedName("s") val symbol: String,
    @SerializedName("a") val aggTradeId: Long,
    @SerializedName("p") val price: String,
    @SerializedName("q") val quantity: String,
    @SerializedName("f") val firstTradeId: Long,
    @SerializedName("l") val lastTradeId: Long,
    @SerializedName("T") val tradeTime: Long,
    @SerializedName("m") val isBuyerMaker: Boolean
)

// 深度数据模型
data class BinanceDepthResponse(
    @SerializedName("lastUpdateId") val lastUpdateId: Long,
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>
)

// 24小时价格统计
data class BinanceTicker24hr(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("priceChange") val priceChange: String,
    @SerializedName("priceChangePercent") val priceChangePercent: String,
    @SerializedName("weightedAvgPrice") val weightedAvgPrice: String,
    @SerializedName("prevClosePrice") val prevClosePrice: String,
    @SerializedName("lastPrice") val lastPrice: String,
    @SerializedName("lastQty") val lastQty: String,
    @SerializedName("bidPrice") val bidPrice: String,
    @SerializedName("askPrice") val askPrice: String,
    @SerializedName("openPrice") val openPrice: String,
    @SerializedName("highPrice") val highPrice: String,
    @SerializedName("lowPrice") val lowPrice: String,
    @SerializedName("volume") val volume: String,
    @SerializedName("quoteVolume") val quoteVolume: String,
    @SerializedName("openTime") val openTime: Long,
    @SerializedName("closeTime") val closeTime: Long,
    @SerializedName("count") val count: Int
)

// 合约相关数据模型
data class BinanceFuturesOpenInterest(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("openInterest") val openInterest: String,
    @SerializedName("time") val time: Long
)

data class BinanceFundingRate(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("fundingRate") val fundingRate: String,
    @SerializedName("fundingTime") val fundingTime: Long
)

// 扩展函数：将币安数据转换为本地KlineData
fun List<String>.toKlineData(symbol: String, interval: String): KlineData {
    return KlineData(
        id = "${symbol}_${interval}_${this[0]}",
        symbol = symbol,
        interval = interval,
        openTime = this[0].toLong(),
        closeTime = this[6].toLong(),
        open = this[1].toDouble(),
        high = this[2].toDouble(),
        low = this[3].toDouble(),
        close = this[4].toDouble(),
        volume = this[5].toDouble(),
        quoteVolume = this[7].toDouble(),
        tradeCount = this[8].toInt(),
        takerBuyBaseVolume = this[9].toDouble(),
        takerBuyQuoteVolume = this[10].toDouble()
    )
}

fun BinanceKlineData.toKlineData(): KlineData {
    return KlineData(
        id = "${symbol}_${interval}_${openTime}",
        symbol = symbol,
        interval = interval,
        openTime = openTime,
        closeTime = closeTime,
        open = open.toDouble(),
        high = high.toDouble(),
        low = low.toDouble(),
        close = close.toDouble(),
        volume = volume.toDouble(),
        quoteVolume = quoteVolume.toDouble(),
        tradeCount = tradeCount,
        takerBuyBaseVolume = takerBuyBaseVolume.toDouble(),
        takerBuyQuoteVolume = takerBuyQuoteVolume.toDouble()
    )
}