package com.trading.orderflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

// 回测配置
@Entity(tableName = "backtest_config")
data class BacktestConfig(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    
    // 回测时间范围
    val startTime: Long,
    val endTime: Long,
    val symbol: String,
    val interval: String,
    
    // 初始资金设置
    val initialBalance: Double = 10000.0,
    val commission: Double = 0.001, // 0.1% 手续费
    val slippage: Double = 0.0005, // 0.05% 滑点
    
    // 风控参数
    val maxRiskPerTrade: Double = 0.02,
    val maxPositions: Int = 3,
    val leverage: Double = 1.0,
    
    // 信号参数
    val signalConfig: String, // JSON格式的信号配置
    
    val createdAt: Long = System.currentTimeMillis()
)

// 回测结果
@Entity(tableName = "backtest_results")
data class BacktestResult(
    @PrimaryKey
    val id: String,
    val configId: String,
    
    // 基本统计
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    
    // 盈亏统计
    val totalPnl: Double,
    val totalPnlPercentage: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val largestWin: Double,
    val largestLoss: Double,
    val profitFactor: Double, // 总盈利/总亏损
    
    // 风险指标
    val maxDrawdown: Double,
    val maxDrawdownPercentage: Double,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val calmarRatio: Double,
    
    // 时间统计
    val avgHoldingTime: Long, // 平均持仓时间(毫秒)
    val maxHoldingTime: Long,
    val minHoldingTime: Long,
    
    // 连续统计
    val maxConsecutiveWins: Int,
    val maxConsecutiveLosses: Int,
    
    // 期末余额
    val finalBalance: Double,
    val peakBalance: Double,
    
    // 执行时间
    val executionTimeMs: Long,
    val completedAt: Long = System.currentTimeMillis()
)

// 回测交易记录
@Entity(tableName = "backtest_trades")
data class BacktestTrade(
    @PrimaryKey
    val id: String,
    val backtestId: String,
    val signalId: String?,
    
    // 交易基本信息
    val symbol: String,
    val side: TradeSide,
    val entryTime: Long,
    val exitTime: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val leverage: Double,
    
    // 止损止盈
    val stopLoss: Double,
    val takeProfit: Double?,
    val exitReason: ExitReason,
    
    // 盈亏计算
    val grossPnl: Double,
    val commission: Double,
    val netPnl: Double,
    val pnlPercentage: Double,
    val holdingTimeMs: Long,
    
    // 信号相关
    val signalStrength: SignalStrength?,
    val signalScore: Double?,
    val entryReason: String?,
    
    // 市场状态
    val entryBalance: Double,
    val exitBalance: Double,
    val drawdownAtEntry: Double,
    
    val createdAt: Long = System.currentTimeMillis()
)

enum class ExitReason {
    TAKE_PROFIT, STOP_LOSS, SIGNAL_REVERSE, TIME_LIMIT, MANUAL
}

// 回测进度
data class BacktestProgress(
    val backtestId: String,
    val currentTime: Long,
    val totalTime: Long,
    val processedBars: Int,
    val totalBars: Int,
    val currentBalance: Double,
    val currentDrawdown: Double,
    val tradesExecuted: Int,
    val isCompleted: Boolean = false,
    val error: String? = null
)

// 回测统计快照
data class BacktestSnapshot(
    val timestamp: Long,
    val balance: Double,
    val equity: Double,
    val drawdown: Double,
    val openPositions: Int,
    val totalTrades: Int,
    val winRate: Double,
    val profitFactor: Double
)

// 参数优化结果
@Entity(tableName = "optimization_results")
data class OptimizationResult(
    @PrimaryKey
    val id: String,
    val configId: String,
    val parameterSet: String, // JSON格式的参数组合
    
    // 优化目标指标
    val targetMetric: String, // "sharpe", "profit", "winRate", etc.
    val targetValue: Double,
    
    // 关键指标
    val totalReturn: Double,
    val maxDrawdown: Double,
    val sharpeRatio: Double,
    val winRate: Double,
    val profitFactor: Double,
    val totalTrades: Int,
    
    // 排名
    val rank: Int,
    val score: Double, // 综合评分
    
    val createdAt: Long = System.currentTimeMillis()
)

// 回测报告
data class BacktestReport(
    val config: BacktestConfig,
    val result: BacktestResult,
    val trades: List<BacktestTrade>,
    val snapshots: List<BacktestSnapshot>,
    val equityCurve: List<Pair<Long, Double>>,
    val drawdownCurve: List<Pair<Long, Double>>,
    val monthlyReturns: Map<String, Double>,
    val riskMetrics: RiskMetrics
)

data class RiskMetrics(
    val var95: Double, // 95% VaR
    val var99: Double, // 99% VaR
    val expectedShortfall: Double, // 条件VaR
    val beta: Double, // 市场Beta
    val alpha: Double, // 超额收益
    val informationRatio: Double,
    val treynorRatio: Double,
    val maxDrawdownDuration: Long // 最大回撤持续时间
)