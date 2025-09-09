package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "klines")
data class KlineData(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val interval: String,
    @SerializedName("open_time")
    val openTime: Long,
    @SerializedName("close_time")
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    @SerializedName("quote_volume")
    val quoteVolume: Double,
    @SerializedName("trade_count")
    val tradeCount: Int,
    @SerializedName("taker_buy_base_volume")
    val takerBuyBaseVolume: Double,
    @SerializedName("taker_buy_quote_volume")
    val takerBuyQuoteVolume: Double
) {
    // 计算CVD (Cumulative Volume Delta)
    val cvd: Double
        get() = takerBuyBaseVolume - (volume - takerBuyBaseVolume)
}

data class TradingSignal(
    val symbol: String,
    val timestamp: Long,
    val signalType: SignalType,
    val strength: SignalStrength,
    val price: Double,
    val factors: Map<String, Double>,
    val totalScore: Double
)

enum class SignalType {
    LONG, SHORT, NEUTRAL
}

enum class SignalStrength {
    WEAK, MEDIUM, STRONG
}