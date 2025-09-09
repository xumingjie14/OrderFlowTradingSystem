package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

// 订单类型
enum class OrderType {
    MARKET,     // 市价单
    LIMIT,      // 限价单
    STOP_LOSS,  // 止损单
    TAKE_PROFIT // 止盈单
}

// 订单方向
enum class OrderSide {
    BUY,  // 买入
    SELL  // 卖出
}

// 订单状态
enum class OrderStatus {
    PENDING,    // 待成交
    FILLED,     // 已成交
    CANCELLED,  // 已取消
    REJECTED,   // 已拒绝
    EXPIRED     // 已过期
}

// 交易订单
@Entity(tableName = "trading_orders")
data class TradingOrder(
    @PrimaryKey
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: BigDecimal,
    val price: BigDecimal? = null, // 限价单价格
    val stopPrice: BigDecimal? = null, // 止损价格
    val status: OrderStatus = OrderStatus.PENDING,
    val filledQuantity: BigDecimal = BigDecimal.ZERO,
    val averagePrice: BigDecimal? = null,
    val commission: BigDecimal = BigDecimal.ZERO,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clientOrderId: String? = null,
    val timeInForce: String = "GTC", // Good Till Cancel
    val isFromSignal: Boolean = false, // 是否来自信号
    val signalId: String? = null
)

// 持仓信息
@Entity(tableName = "positions")
data class Position(
    @PrimaryKey
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
    val unrealizedPnl: BigDecimal = BigDecimal.ZERO,
    val realizedPnl: BigDecimal = BigDecimal.ZERO,
    val margin: BigDecimal = BigDecimal.ZERO,
    val leverage: Int = 1,
    val markPrice: BigDecimal? = null,
    val liquidationPrice: BigDecimal? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 交易记录
@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey
    val tradeId: String,
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val commission: BigDecimal,
    val commissionAsset: String,
    val realizedPnl: BigDecimal = BigDecimal.ZERO,
    val timestamp: Long = System.currentTimeMillis()
)

// 账户余额
@Entity(tableName = "account_balance")
data class AccountBalance(
    @PrimaryKey
    val asset: String,
    val free: BigDecimal,      // 可用余额
    val locked: BigDecimal,    // 冻结余额
    val total: BigDecimal,     // 总余额
    val updatedAt: Long = System.currentTimeMillis()
)

// 交易请求
data class PlaceOrderRequest(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: BigDecimal,
    val price: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    val timeInForce: String = "GTC",
    val clientOrderId: String? = null
)

// 交易响应
data class PlaceOrderResponse(
    val success: Boolean,
    val orderId: String? = null,
    val clientOrderId: String? = null,
    val message: String? = null,
    val order: TradingOrder? = null
)