package com.trading.orderflow.domain.backtest

import android.util.Log
import com.trading.orderflow.data.database.BacktestDao
import com.trading.orderflow.data.model.*
import com.trading.orderflow.domain.signal.SignalEngine
import com.trading.orderflow.domain.service.IndicatorService
import com.trading.orderflow.data.repository.MarketDataRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class BacktestEngine @Inject constructor(
    private val backtestDao: BacktestDao,
    private val signalEngine: SignalEngine,
    private val indicatorService: IndicatorService,
    private val marketDataRepository: MarketDataRepository
) {
    
    companion object {
        private const val TAG = "BacktestEngine"
    }
    
    private val _progressFlow = MutableSharedFlow<BacktestProgress>()
    val progressFlow: SharedFlow<BacktestProgress> = _progressFlow.asSharedFlow()
    
    // 执行回测
    suspend fun runBacktest(config: BacktestConfig): BacktestResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val backtestId = "backtest_${startTime}"
        
        try {
            Log.d(TAG, "Starting backtest: ${config.name}")
            
            // 获取历史数据
            val klines = marketDataRepository.getHistoricalKlines(
                symbol = config.symbol,
                interval = config.interval,
                limit = 5000 // 获取足够的历史数据
            ).getOrThrow().filter { 
                it.openTime >= config.startTime && it.closeTime <= config.endTime 
            }.sortedBy { it.openTime }
            
            if (klines.isEmpty()) {
                throw IllegalArgumentException("No historical data available for the specified period")
            }
            
            // 计算指标
            val indicators = indicatorService.calculateAllIndicators(
                symbol = config.symbol,
                interval = config.interval,
                klines = klines
            )
            
            // 初始化回测状态
            val backtestState = BacktestState(
                balance = config.initialBalance,
                equity = config.initialBalance,
                peakBalance = config.initialBalance,
                positions = mutableListOf(),
                trades = mutableListOf(),
                snapshots = mutableListOf()
            )
            
            val totalBars = indicators.size
            var processedBars = 0
            
            // 逐个处理K线数据
            for (i in indicators.indices) {
                val indicator = indicators[i]
                val kline = klines.find { it.openTime == indicator.timestamp } ?: continue
                
                // 更新持仓盈亏
                updatePositions(backtestState, kline.close)
                
                // 检查止损止盈
                checkExits(backtestState, kline, config, backtestId)
                
                // 生成信号
                val signalResult = signalEngine.generateSignal(
                    symbol = config.symbol,
                    interval = config.interval,
                    currentIndicator = indicator,
                    currentKline = kline
                )
                
                // 执行信号
                signalResult.signal?.let { signal ->
                    executeSignal(backtestState, signal, kline, config, backtestId)
                }
                
                // 记录快照
                if (i % 24 == 0) { // 每24个周期记录一次快照
                    backtestState.snapshots.add(
                        BacktestSnapshot(
                            timestamp = indicator.timestamp,
                            balance = backtestState.balance,
                            equity = backtestState.equity,
                            drawdown = (backtestState.peakBalance - backtestState.equity) / backtestState.peakBalance,
                            openPositions = backtestState.positions.size,
                            totalTrades = backtestState.trades.size,
                            winRate = calculateWinRate(backtestState.trades),
                            profitFactor = calculateProfitFactor(backtestState.trades)
                        )
                    )
                }
                
                // 更新进度
                processedBars++
                if (processedBars % 100 == 0) {
                    _progressFlow.emit(
                        BacktestProgress(
                            backtestId = backtestId,
                            currentTime = indicator.timestamp,
                            totalTime = config.endTime - config.startTime,
                            processedBars = processedBars,
                            totalBars = totalBars,
                            currentBalance = backtestState.balance,
                            currentDrawdown = (backtestState.peakBalance - backtestState.equity) / backtestState.peakBalance,
                            tradesExecuted = backtestState.trades.size
                        )
                    )
                }
            }
            
            // 平仓所有持仓
            closeAllPositions(backtestState, klines.last(), config, backtestId)
            
            // 计算最终结果
            val result = calculateBacktestResult(
                backtestId = backtestId,
                configId = config.id,
                state = backtestState,
                executionTime = System.currentTimeMillis() - startTime
            )
            
            // 保存结果
            backtestDao.insertBacktestResult(result)
            backtestDao.insertBacktestTrades(backtestState.trades)
            
            Log.d(TAG, "Backtest completed: ${result.totalTrades} trades, ${String.format("%.2f%%", result.totalPnlPercentage)} return")
            
            _progressFlow.emit(
                BacktestProgress(
                    backtestId = backtestId,
                    currentTime = config.endTime,
                    totalTime = config.endTime - config.startTime,
                    processedBars = totalBars,
                    totalBars = totalBars,
                    currentBalance = result.finalBalance,
                    currentDrawdown = result.maxDrawdownPercentage,
                    tradesExecuted = result.totalTrades,
                    isCompleted = true
                )
            )
            
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Backtest failed: ${e.message}")
            _progressFlow.emit(
                BacktestProgress(
                    backtestId = backtestId,
                    currentTime = System.currentTimeMillis(),
                    totalTime = config.endTime - config.startTime,
                    processedBars = 0,
                    totalBars = 0,
                    currentBalance = config.initialBalance,
                    currentDrawdown = 0.0,
                    tradesExecuted = 0,
                    isCompleted = true,
                    error = e.message
                )
            )
            throw e
        }
    }
    
    // 执行信号
    private fun executeSignal(
        state: BacktestState,
        signal: TradingSignal,
        kline: KlineData,
        config: BacktestConfig,
        backtestId: String
    ) {
        // 检查是否可以开仓
        if (state.positions.size >= config.maxPositions) return
        
        val price = kline.close
        val stopLoss = signal.suggestedStopLoss ?: return
        val takeProfit = signal.suggestedTakeProfit
        
        // 计算仓位大小
        val riskAmount = state.balance * config.maxRiskPerTrade
        val riskDistance = abs(price - stopLoss)
        val quantity = if (riskDistance > 0) riskAmount / riskDistance else 0.0
        
        if (quantity <= 0) return
        
        // 检查余额是否足够
        val requiredMargin = quantity * price / config.leverage
        if (requiredMargin > state.balance * 0.9) return // 保留10%余额
        
        // 创建持仓
        val position = BacktestPosition(
            id = "pos_${System.currentTimeMillis()}",
            symbol = signal.symbol,
            side = when (signal.signalType) {
                SignalType.LONG -> TradeSide.LONG
                SignalType.SHORT -> TradeSide.SHORT
                else -> return
            },
            entryTime = kline.openTime,
            entryPrice = price,
            quantity = quantity,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            leverage = config.leverage,
            signalId = signal.id,
            signalStrength = signal.strength,
            signalScore = signal.totalScore
        )
        
        state.positions.add(position)
        state.balance -= requiredMargin
        
        Log.d(TAG, "Opened position: ${position.side} ${position.quantity}@${position.entryPrice}")
    }
    
    // 更新持仓
    private fun updatePositions(state: BacktestState, currentPrice: Double) {
        var totalUnrealizedPnl = 0.0
        
        for (position in state.positions) {
            val unrealizedPnl = when (position.side) {
                TradeSide.LONG -> (currentPrice - position.entryPrice) * position.quantity * position.leverage
                TradeSide.SHORT -> (position.entryPrice - currentPrice) * position.quantity * position.leverage
            }
            position.unrealizedPnl = unrealizedPnl
            totalUnrealizedPnl += unrealizedPnl
        }
        
        state.equity = state.balance + totalUnrealizedPnl
        if (state.equity > state.peakBalance) {
            state.peakBalance = state.equity
        }
    }
    
    // 检查出场条件
    private fun checkExits(
        state: BacktestState,
        kline: KlineData,
        config: BacktestConfig,
        backtestId: String
    ) {
        val positionsToClose = mutableListOf<BacktestPosition>()
        
        for (position in state.positions) {
            val currentPrice = kline.close
            var exitReason: ExitReason? = null
            
            // 检查止损
            when (position.side) {
                TradeSide.LONG -> {
                    if (currentPrice <= position.stopLoss) {
                        exitReason = ExitReason.STOP_LOSS
                    } else if (position.takeProfit != null && currentPrice >= position.takeProfit) {
                        exitReason = ExitReason.TAKE_PROFIT
                    }
                }
                TradeSide.SHORT -> {
                    if (currentPrice >= position.stopLoss) {
                        exitReason = ExitReason.STOP_LOSS
                    } else if (position.takeProfit != null && currentPrice <= position.takeProfit) {
                        exitReason = ExitReason.TAKE_PROFIT
                    }
                }
            }
            
            if (exitReason != null) {
                closePosition(state, position, currentPrice, kline.openTime, exitReason, config, backtestId)
                positionsToClose.add(position)
            }
        }
        
        // 移除已平仓的持仓
        state.positions.removeAll(positionsToClose)
    }
    
    // 平仓
    private fun closePosition(
        state: BacktestState,
        position: BacktestPosition,
        exitPrice: Double,
        exitTime: Long,
        exitReason: ExitReason,
        config: BacktestConfig,
        backtestId: String
    ) {
        val grossPnl = when (position.side) {
            TradeSide.LONG -> (exitPrice - position.entryPrice) * position.quantity * position.leverage
            TradeSide.SHORT -> (position.entryPrice - exitPrice) * position.quantity * position.leverage
        }
        
        val commission = position.quantity * (position.entryPrice + exitPrice) * config.commission
        val netPnl = grossPnl - commission
        val pnlPercentage = netPnl / (position.quantity * position.entryPrice / position.leverage)
        
        // 创建交易记录
        val trade = BacktestTrade(
            id = "trade_${System.currentTimeMillis()}_${state.trades.size}",
            backtestId = backtestId,
            signalId = position.signalId,
            symbol = position.symbol,
            side = position.side,
            entryTime = position.entryTime,
            exitTime = exitTime,
            entryPrice = position.entryPrice,
            exitPrice = exitPrice,
            quantity = position.quantity,
            leverage = position.leverage,
            stopLoss = position.stopLoss,
            takeProfit = position.takeProfit,
            exitReason = exitReason,
            grossPnl = grossPnl,
            commission = commission,
            netPnl = netPnl,
            pnlPercentage = pnlPercentage,
            holdingTimeMs = exitTime - position.entryTime,
            signalStrength = position.signalStrength,
            signalScore = position.signalScore,
            entryBalance = state.balance,
            exitBalance = state.balance + netPnl,
            drawdownAtEntry = (state.peakBalance - state.equity) / state.peakBalance
        )
        
        state.trades.add(trade)
        state.balance += netPnl + (position.quantity * position.entryPrice / position.leverage) // 返还保证金
        
        Log.d(TAG, "Closed position: ${position.side} PnL: ${String.format("%.2f", netPnl)}")
    }
    
    // 平仓所有持仓
    private fun closeAllPositions(
        state: BacktestState,
        lastKline: KlineData,
        config: BacktestConfig,
        backtestId: String
    ) {
        val positionsToClose = state.positions.toList()
        for (position in positionsToClose) {
            closePosition(state, position, lastKline.close, lastKline.closeTime, ExitReason.TIME_LIMIT, config, backtestId)
        }
        state.positions.clear()
    }
    
    // 计算回测结果
    private fun calculateBacktestResult(
        backtestId: String,
        configId: String,
        state: BacktestState,
        executionTime: Long
    ): BacktestResult {
        val trades = state.trades
        val winningTrades = trades.count { it.netPnl > 0 }
        val losingTrades = trades.count { it.netPnl < 0 }
        val winRate = if (trades.isNotEmpty()) winningTrades.toDouble() / trades.size else 0.0
        
        val totalPnl = trades.sumOf { it.netPnl }
        val totalPnlPercentage = totalPnl / state.balance * 100
        
        val wins = trades.filter { it.netPnl > 0 }.map { it.netPnl }
        val losses = trades.filter { it.netPnl < 0 }.map { it.netPnl }
        
        val avgWin = if (wins.isNotEmpty()) wins.average() else 0.0
        val avgLoss = if (losses.isNotEmpty()) losses.average() else 0.0
        val largestWin = wins.maxOrNull() ?: 0.0
        val largestLoss = losses.minOrNull() ?: 0.0
        val profitFactor = if (losses.isNotEmpty()) wins.sum() / abs(losses.sum()) else Double.MAX_VALUE
        
        // 计算最大回撤
        val maxDrawdown = state.peakBalance - state.balance
        val maxDrawdownPercentage = maxDrawdown / state.peakBalance
        
        // 计算夏普比率
        val returns = trades.map { it.pnlPercentage }
        val avgReturn = if (returns.isNotEmpty()) returns.average() else 0.0
        val returnStdDev = if (returns.size > 1) {
            sqrt(returns.map { (it - avgReturn).pow(2) }.average())
        } else 0.0
        val sharpeRatio = if (returnStdDev > 0) avgReturn / returnStdDev else 0.0
        
        // 计算索提诺比率
        val negativeReturns = returns.filter { it < 0 }
        val downwardStdDev = if (negativeReturns.size > 1) {
            sqrt(negativeReturns.map { it.pow(2) }.average())
        } else 0.0
        val sortinoRatio = if (downwardStdDev > 0) avgReturn / downwardStdDev else 0.0
        
        // 计算卡尔玛比率
        val calmarRatio = if (maxDrawdownPercentage > 0) totalPnlPercentage / (maxDrawdownPercentage * 100) else 0.0
        
        // 计算持仓时间统计
        val holdingTimes = trades.map { it.holdingTimeMs }
        val avgHoldingTime = if (holdingTimes.isNotEmpty()) holdingTimes.average().toLong() else 0L
        val maxHoldingTime = holdingTimes.maxOrNull() ?: 0L
        val minHoldingTime = holdingTimes.minOrNull() ?: 0L
        
        // 计算连续统计
        val (maxConsecutiveWins, maxConsecutiveLosses) = calculateConsecutiveStats(trades)
        
        return BacktestResult(
            id = backtestId,
            configId = configId,
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = winRate,
            totalPnl = totalPnl,
            totalPnlPercentage = totalPnlPercentage,
            avgWin = avgWin,
            avgLoss = avgLoss,
            largestWin = largestWin,
            largestLoss = largestLoss,
            profitFactor = profitFactor,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercentage = maxDrawdownPercentage,
            sharpeRatio = sharpeRatio,
            sortinoRatio = sortinoRatio,
            calmarRatio = calmarRatio,
            avgHoldingTime = avgHoldingTime,
            maxHoldingTime = maxHoldingTime,
            minHoldingTime = minHoldingTime,
            maxConsecutiveWins = maxConsecutiveWins,
            maxConsecutiveLosses = maxConsecutiveLosses,
            finalBalance = state.balance,
            peakBalance = state.peakBalance,
            executionTimeMs = executionTime
        )
    }
    
    // 辅助函数
    private fun calculateWinRate(trades: List<BacktestTrade>): Double {
        if (trades.isEmpty()) return 0.0
        return trades.count { it.netPnl > 0 }.toDouble() / trades.size
    }
    
    private fun calculateProfitFactor(trades: List<BacktestTrade>): Double {
        val wins = trades.filter { it.netPnl > 0 }.sumOf { it.netPnl }
        val losses = trades.filter { it.netPnl < 0 }.sumOf { abs(it.netPnl) }
        return if (losses > 0) wins / losses else Double.MAX_VALUE
    }
    
    private fun calculateConsecutiveStats(trades: List<BacktestTrade>): Pair<Int, Int> {
        var maxWins = 0
        var maxLosses = 0
        var currentWins = 0
        var currentLosses = 0
        
        for (trade in trades) {
            if (trade.netPnl > 0) {
                currentWins++
                currentLosses = 0
                maxWins = max(maxWins, currentWins)
            } else {
                currentLosses++
                currentWins = 0
                maxLosses = max(maxLosses, currentLosses)
            }
        }
        
        return Pair(maxWins, maxLosses)
    }
    
    // 获取回测结果
    suspend fun getBacktestResult(backtestId: String): BacktestResult? {
        return backtestDao.getBacktestResult(backtestId)
    }
    
    // 获取回测交易记录
    suspend fun getBacktestTrades(backtestId: String): List<BacktestTrade> {
        return backtestDao.getBacktestTrades(backtestId)
    }
    
    // 生成回测报告
    suspend fun generateBacktestReport(backtestId: String): BacktestReport? {
        val result = getBacktestResult(backtestId) ?: return null
        val config = backtestDao.getBacktestConfig(result.configId) ?: return null
        val trades = getBacktestTrades(backtestId)
        val snapshots = backtestDao.getBacktestSnapshots(backtestId)
        
        // 生成权益曲线
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        var runningBalance = config.initialBalance
        equityCurve.add(Pair(config.startTime, runningBalance))
        
        for (trade in trades.sortedBy { it.exitTime }) {
            runningBalance += trade.netPnl
            equityCurve.add(Pair(trade.exitTime, runningBalance))
        }
        
        // 生成回撤曲线
        val drawdownCurve = mutableListOf<Pair<Long, Double>>()
        var peakBalance = config.initialBalance
        
        for ((time, balance) in equityCurve) {
            if (balance > peakBalance) peakBalance = balance
            val drawdown = (peakBalance - balance) / peakBalance
            drawdownCurve.add(Pair(time, drawdown))
        }
        
        // 计算月度收益
        val monthlyReturns = calculateMonthlyReturns(trades, config.initialBalance)
        
        // 计算风险指标
        val riskMetrics = calculateRiskMetrics(trades, equityCurve)
        
        return BacktestReport(
            config = config,
            result = result,
            trades = trades,
            snapshots = snapshots,
            equityCurve = equityCurve,
            drawdownCurve = drawdownCurve,
            monthlyReturns = monthlyReturns,
            riskMetrics = riskMetrics
        )
    }
    
    private fun calculateMonthlyReturns(trades: List<BacktestTrade>, initialBalance: Double): Map<String, Double> {
        // 实现月度收益计算逻辑
        return emptyMap() // 简化实现
    }
    
    private fun calculateRiskMetrics(trades: List<BacktestTrade>, equityCurve: List<Pair<Long, Double>>): RiskMetrics {
        // 实现风险指标计算逻辑
        return RiskMetrics(
            var95 = 0.0,
            var99 = 0.0,
            expectedShortfall = 0.0,
            beta = 0.0,
            alpha = 0.0,
            informationRatio = 0.0,
            treynorRatio = 0.0,
            maxDrawdownDuration = 0L
        )
    }
}

// 回测状态
data class BacktestState(
    var balance: Double,
    var equity: Double,
    var peakBalance: Double,
    val positions: MutableList<BacktestPosition>,
    val trades: MutableList<BacktestTrade>,
    val snapshots: MutableList<BacktestSnapshot>
)

// 回测持仓
data class BacktestPosition(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val entryTime: Long,
    val entryPrice: Double,
    val quantity: Double,
    val stopLoss: Double,
    val takeProfit: Double?,
    val leverage: Double,
    val signalId: String?,
    val signalStrength: SignalStrength?,
    val signalScore: Double?,
    var unrealizedPnl: Double = 0.0
)