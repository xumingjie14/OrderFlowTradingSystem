package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

// 风控配置
@Entity(tableName = "risk_config")
data class RiskConfig(
    @PrimaryKey
    val id: String = "default",
    
    // 基础风控参数
    val maxRiskPerTrade: Double = 0.02, // 单笔最大风险2%
    val maxTotalRisk: Double = 0.06, // 总风险6%
    val maxPositions: Int = 3, // 最大同时持仓数
    val maxDrawdown: Double = 0.15, // 最大回撤15%
    
    // 冷却期设置
    val cooldownAfterLosses: Int = 3, // 连续亏损3次后冷却
    val cooldownDurationHours: Int = 24, // 冷却24小时
    
    // 账户保护
    val minAccountBalance: Double = 1000.0, // 最小账户余额
    val emergencyStopLoss: Double = 0.20, // 紧急止损20%
    
    // 杠杆控制
    val maxLeverage: Double = 3.0, // 最大杠杆3倍
    val leverageReductionThreshold: Double = 0.10, // 回撤10%时降杠杆
    
    val updatedAt: Long = System.currentTimeMillis()
)

// 交易记录
@Entity(tableName = "trades")
data class TradeRecord(
    @PrimaryKey
    val id: String,
    val symbol: String,
    val side: TradeSide, // LONG/SHORT
    val entryPrice: Double,
    val exitPrice: Double? = null,
    val quantity: Double,
    val leverage: Double = 1.0,
    
    // 风控参数
    val stopLoss: Double,
    val takeProfit: Double? = null,
    val riskAmount: Double, // 风险金额
    val riskPercentage: Double, // 风险百分比
    
    // 交易状态
    val status: TradeStatus,
    val entryTime: Long,
    val exitTime: Long? = null,
    
    // 盈亏计算
    val pnl: Double? = null,
    val pnlPercentage: Double? = null,
    val commission: Double = 0.0,
    
    // 信号相关
    val signalId: String? = null,
    val signalStrength: Double? = null,
    val entryReason: String? = null,
    val exitReason: String? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)

enum class TradeSide {
    LONG, SHORT
}

enum class TradeStatus {
    PENDING, // 待执行
    OPEN, // 持仓中
    CLOSED, // 已平仓
    CANCELLED, // 已取消
    STOPPED // 止损
}

// 账户状态
@Entity(tableName = "account_status")
data class AccountStatus(
    @PrimaryKey
    val id: String = "main",
    val totalBalance: Double,
    val availableBalance: Double,
    val unrealizedPnl: Double = 0.0,
    val totalPnl: Double = 0.0,
    
    // 风控状态
    val currentRisk: Double = 0.0, // 当前总风险
    val maxDrawdownFromPeak: Double = 0.0, // 从峰值的最大回撤
    val peakBalance: Double = totalBalance, // 历史最高余额
    
    // 交易统计
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val consecutiveLosses: Int = 0, // 连续亏损次数
    
    // 冷却状态
    val isInCooldown: Boolean = false,
    val cooldownEndTime: Long? = null,
    
    val lastUpdated: Long = System.currentTimeMillis()
)

// 风控事件记录
@Entity(tableName = "risk_events")
data class RiskEvent(
    @PrimaryKey
    val id: String,
    val eventType: RiskEventType,
    val severity: RiskSeverity,
    val message: String,
    val details: String? = null,
    
    // 相关数据
    val tradeId: String? = null,
    val symbol: String? = null,
    val riskValue: Double? = null,
    val threshold: Double? = null,
    
    val timestamp: Long = System.currentTimeMillis()
)

enum class RiskEventType {
    RISK_LIMIT_EXCEEDED, // 风险限制超出
    MAX_POSITIONS_REACHED, // 达到最大持仓数
    DRAWDOWN_WARNING, // 回撤警告
    EMERGENCY_STOP, // 紧急停止
    COOLDOWN_ACTIVATED, // 冷却期激活
    LEVERAGE_REDUCED, // 杠杆降低
    BALANCE_LOW // 余额不足
}

enum class RiskSeverity {
    INFO, WARNING, CRITICAL
}

// 仓位信息
data class Position(
    val symbol: String,
    val side: TradeSide,
    val size: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPercentage: Double,
    val stopLoss: Double,
    val takeProfit: Double? = null,
    val leverage: Double = 1.0,
    val marginUsed: Double,
    val riskAmount: Double
) {
    val marketValue: Double
        get() = size * currentPrice
    
    val isProfit: Boolean
        get() = unrealizedPnl > 0
    
    val riskRewardRatio: Double
        get() = takeProfit?.let { tp ->
            val potentialProfit = when (side) {
                TradeSide.LONG -> (tp - entryPrice) * size
                TradeSide.SHORT -> (entryPrice - tp) * size
            }
            if (riskAmount > 0) potentialProfit / riskAmount else 0.0
        } ?: 0.0
}