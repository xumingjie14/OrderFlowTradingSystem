package com.trading.orderflow.domain.risk

import android.util.Log
import com.trading.orderflow.data.database.RiskDao
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class RiskManager @Inject constructor(
    private val riskDao: RiskDao
) {
    
    companion object {
        private const val TAG = "RiskManager"
    }
    
    private val _riskEvents = MutableSharedFlow<RiskEvent>()
    val riskEvents: SharedFlow<RiskEvent> = _riskEvents.asSharedFlow()
    
    // 获取风控配置
    suspend fun getRiskConfig(): RiskConfig {
        return riskDao.getRiskConfig() ?: RiskConfig()
    }
    
    // 更新风控配置
    suspend fun updateRiskConfig(config: RiskConfig) {
        riskDao.insertRiskConfig(config)
    }
    
    // 检查是否可以开仓
    suspend fun canOpenPosition(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        price: Double,
        stopLoss: Double,
        leverage: Double = 1.0
    ): RiskCheckResult = withContext(Dispatchers.Default) {
        
        val config = getRiskConfig()
        val accountStatus = getAccountStatus()
        val currentPositions = getCurrentPositions()
        
        // 检查冷却期
        if (accountStatus.isInCooldown) {
            val cooldownEnd = accountStatus.cooldownEndTime ?: 0L
            if (System.currentTimeMillis() < cooldownEnd) {
                return@withContext RiskCheckResult(
                    canTrade = false,
                    reason = "账户处于冷却期，冷却结束时间：${java.util.Date(cooldownEnd)}"
                )
            } else {
                // 冷却期结束，更新状态
                updateAccountStatus(accountStatus.copy(
                    isInCooldown = false,
                    cooldownEndTime = null
                ))
            }
        }
        
        // 检查最大持仓数
        if (currentPositions.size >= config.maxPositions) {
            emitRiskEvent(
                RiskEvent(
                    id = "max_positions_${System.currentTimeMillis()}",
                    eventType = RiskEventType.MAX_POSITIONS_REACHED,
                    severity = RiskSeverity.WARNING,
                    message = "已达到最大持仓数限制：${config.maxPositions}",
                    symbol = symbol
                )
            )
            return@withContext RiskCheckResult(
                canTrade = false,
                reason = "已达到最大持仓数限制：${config.maxPositions}"
            )
        }
        
        // 计算交易风险
        val riskAmount = calculateTradeRisk(price, stopLoss, quantity, side, leverage)
        val riskPercentage = riskAmount / accountStatus.totalBalance
        
        // 检查单笔风险
        if (riskPercentage > config.maxRiskPerTrade) {
            emitRiskEvent(
                RiskEvent(
                    id = "risk_exceeded_${System.currentTimeMillis()}",
                    eventType = RiskEventType.RISK_LIMIT_EXCEEDED,
                    severity = RiskSeverity.CRITICAL,
                    message = "单笔风险超限：${String.format("%.2f%%", riskPercentage * 100)}",
                    symbol = symbol,
                    riskValue = riskPercentage,
                    threshold = config.maxRiskPerTrade
                )
            )
            return@withContext RiskCheckResult(
                canTrade = false,
                reason = "单笔风险超限：${String.format("%.2f%%", riskPercentage * 100)}，最大允许：${String.format("%.2f%%", config.maxRiskPerTrade * 100)}"
            )
        }
        
        // 检查总风险
        val totalRisk = accountStatus.currentRisk + riskPercentage
        if (totalRisk > config.maxTotalRisk) {
            emitRiskEvent(
                RiskEvent(
                    id = "total_risk_exceeded_${System.currentTimeMillis()}",
                    eventType = RiskEventType.RISK_LIMIT_EXCEEDED,
                    severity = RiskSeverity.CRITICAL,
                    message = "总风险超限：${String.format("%.2f%%", totalRisk * 100)}",
                    symbol = symbol,
                    riskValue = totalRisk,
                    threshold = config.maxTotalRisk
                )
            )
            return@withContext RiskCheckResult(
                canTrade = false,
                reason = "总风险超限：${String.format("%.2f%%", totalRisk * 100)}，最大允许：${String.format("%.2f%%", config.maxTotalRisk * 100)}"
            )
        }
        
        // 检查账户余额
        val requiredMargin = (quantity * price * leverage) / leverage
        if (accountStatus.availableBalance < requiredMargin) {
            return@withContext RiskCheckResult(
                canTrade = false,
                reason = "可用余额不足，需要：${String.format("%.2f", requiredMargin)}，可用：${String.format("%.2f", accountStatus.availableBalance)}"
            )
        }
        
        // 检查杠杆限制
        if (leverage > config.maxLeverage) {
            return@withContext RiskCheckResult(
                canTrade = false,
                reason = "杠杆超限：${leverage}x，最大允许：${config.maxLeverage}x"
            )
        }
        
        // 检查回撤状态
        if (accountStatus.maxDrawdownFromPeak > config.leverageReductionThreshold) {
            val recommendedLeverage = max(1.0, leverage * 0.5) // 降低杠杆
            return@withContext RiskCheckResult(
                canTrade = true,
                reason = "当前回撤较大，建议降低杠杆至：${recommendedLeverage}x",
                recommendedLeverage = recommendedLeverage
            )
        }
        
        return@withContext RiskCheckResult(
            canTrade = true,
            reason = "风控检查通过",
            riskAmount = riskAmount,
            riskPercentage = riskPercentage
        )
    }
    
    // 计算交易风险
    private fun calculateTradeRisk(
        entryPrice: Double,
        stopLoss: Double,
        quantity: Double,
        side: TradeSide,
        leverage: Double
    ): Double {
        val priceDistance = abs(entryPrice - stopLoss)
        return priceDistance * quantity * leverage
    }
    
    // 开仓后更新风控状态
    suspend fun onPositionOpened(trade: TradeRecord) {
        val accountStatus = getAccountStatus()
        val newRisk = accountStatus.currentRisk + trade.riskPercentage
        
        updateAccountStatus(accountStatus.copy(
            currentRisk = newRisk,
            totalTrades = accountStatus.totalTrades + 1,
            availableBalance = accountStatus.availableBalance - (trade.quantity * trade.entryPrice / (trade.leverage))
        ))
        
        // 保存交易记录
        riskDao.insertTrade(trade)
        
        Log.d(TAG, "Position opened: ${trade.symbol} ${trade.side} ${trade.quantity}@${trade.entryPrice}")
    }
    
    // 平仓后更新风控状态
    suspend fun onPositionClosed(trade: TradeRecord) {
        val accountStatus = getAccountStatus()
        val pnl = trade.pnl ?: 0.0
        val isWin = pnl > 0
        
        val newConsecutiveLosses = if (isWin) 0 else accountStatus.consecutiveLosses + 1
        val newBalance = accountStatus.totalBalance + pnl
        val newPeakBalance = max(accountStatus.peakBalance, newBalance)
        val newDrawdown = (newPeakBalance - newBalance) / newPeakBalance
        
        val updatedStatus = accountStatus.copy(
            totalBalance = newBalance,
            availableBalance = accountStatus.availableBalance + (trade.quantity * trade.entryPrice / trade.leverage) + pnl,
            currentRisk = max(0.0, accountStatus.currentRisk - trade.riskPercentage),
            totalPnl = accountStatus.totalPnl + pnl,
            winningTrades = if (isWin) accountStatus.winningTrades + 1 else accountStatus.winningTrades,
            losingTrades = if (!isWin) accountStatus.losingTrades + 1 else accountStatus.losingTrades,
            consecutiveLosses = newConsecutiveLosses,
            peakBalance = newPeakBalance,
            maxDrawdownFromPeak = newDrawdown
        )
        
        updateAccountStatus(updatedStatus)
        
        // 更新交易记录
        riskDao.updateTrade(trade)
        
        // 检查是否需要激活冷却期
        val config = getRiskConfig()
        if (newConsecutiveLosses >= config.cooldownAfterLosses) {
            activateCooldown(config.cooldownDurationHours)
        }
        
        // 检查回撤警告
        if (newDrawdown > config.maxDrawdown * 0.8) { // 80%阈值警告
            emitRiskEvent(
                RiskEvent(
                    id = "drawdown_warning_${System.currentTimeMillis()}",
                    eventType = RiskEventType.DRAWDOWN_WARNING,
                    severity = RiskSeverity.WARNING,
                    message = "回撤接近限制：${String.format("%.2f%%", newDrawdown * 100)}",
                    riskValue = newDrawdown,
                    threshold = config.maxDrawdown
                )
            )
        }
        
        // 检查紧急止损
        if (newDrawdown > config.maxDrawdown) {
            emitRiskEvent(
                RiskEvent(
                    id = "emergency_stop_${System.currentTimeMillis()}",
                    eventType = RiskEventType.EMERGENCY_STOP,
                    severity = RiskSeverity.CRITICAL,
                    message = "触发紧急止损：回撤${String.format("%.2f%%", newDrawdown * 100)}",
                    riskValue = newDrawdown,
                    threshold = config.maxDrawdown
                )
            )
            
            // 强制平仓所有持仓
            closeAllPositions("紧急止损")
        }
        
        Log.d(TAG, "Position closed: ${trade.symbol} PnL: ${pnl}")
    }
    
    // 激活冷却期
    private suspend fun activateCooldown(durationHours: Int) {
        val cooldownEnd = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)
        val accountStatus = getAccountStatus()
        
        updateAccountStatus(accountStatus.copy(
            isInCooldown = true,
            cooldownEndTime = cooldownEnd
        ))
        
        emitRiskEvent(
            RiskEvent(
                id = "cooldown_activated_${System.currentTimeMillis()}",
                eventType = RiskEventType.COOLDOWN_ACTIVATED,
                severity = RiskSeverity.WARNING,
                message = "连续亏损达到限制，激活${durationHours}小时冷却期",
                details = "冷却结束时间：${java.util.Date(cooldownEnd)}"
            )
        )
        
        Log.w(TAG, "Cooldown activated for $durationHours hours")
    }
    
    // 强制平仓所有持仓
    private suspend fun closeAllPositions(reason: String) {
        val positions = getCurrentPositions()
        for (position in positions) {
            // 这里应该调用交易执行模块来平仓
            Log.w(TAG, "Force closing position: ${position.symbol} - $reason")
        }
    }
    
    // 获取账户状态
    suspend fun getAccountStatus(): AccountStatus {
        return riskDao.getAccountStatus() ?: AccountStatus(
            totalBalance = 10000.0, // 默认余额
            availableBalance = 10000.0
        )
    }
    
    // 更新账户状态
    private suspend fun updateAccountStatus(status: AccountStatus) {
        riskDao.insertAccountStatus(status)
    }
    
    // 获取当前持仓
    private suspend fun getCurrentPositions(): List<Position> {
        // 这里应该从交易模块获取当前持仓
        return riskDao.getOpenTrades().map { trade ->
            Position(
                symbol = trade.symbol,
                side = trade.side,
                size = trade.quantity,
                entryPrice = trade.entryPrice,
                currentPrice = trade.entryPrice, // 应该从市场数据获取当前价格
                unrealizedPnl = 0.0, // 应该计算未实现盈亏
                unrealizedPnlPercentage = 0.0,
                stopLoss = trade.stopLoss,
                takeProfit = trade.takeProfit,
                leverage = trade.leverage,
                marginUsed = trade.quantity * trade.entryPrice / trade.leverage,
                riskAmount = trade.riskAmount
            )
        }
    }
    
    // 发送风控事件
    private suspend fun emitRiskEvent(event: RiskEvent) {
        riskDao.insertRiskEvent(event)
        _riskEvents.emit(event)
    }
    
    // 获取风控事件历史
    suspend fun getRiskEvents(limit: Int = 100): List<RiskEvent> {
        return riskDao.getRiskEvents(limit)
    }
    
    // 获取交易统计
    suspend fun getTradingStats(): TradingStats {
        val accountStatus = getAccountStatus()
        val trades = riskDao.getAllTrades()
        
        val closedTrades = trades.filter { it.status == TradeStatus.CLOSED }
        val totalPnl = closedTrades.sumOf { it.pnl ?: 0.0 }
        val winRate = if (closedTrades.isNotEmpty()) {
            closedTrades.count { (it.pnl ?: 0.0) > 0 }.toDouble() / closedTrades.size
        } else 0.0
        
        val avgWin = closedTrades.filter { (it.pnl ?: 0.0) > 0 }.map { it.pnl ?: 0.0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val avgLoss = closedTrades.filter { (it.pnl ?: 0.0) < 0 }.map { it.pnl ?: 0.0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val profitFactor = if (avgLoss != 0.0) avgWin / abs(avgLoss) else 0.0
        
        return TradingStats(
            totalTrades = closedTrades.size,
            winningTrades = closedTrades.count { (it.pnl ?: 0.0) > 0 },
            losingTrades = closedTrades.count { (it.pnl ?: 0.0) < 0 },
            winRate = winRate,
            totalPnl = totalPnl,
            avgWin = avgWin,
            avgLoss = avgLoss,
            profitFactor = profitFactor,
            maxDrawdown = accountStatus.maxDrawdownFromPeak,
            currentBalance = accountStatus.totalBalance,
            peakBalance = accountStatus.peakBalance
        )
    }
}

// 风控检查结果
data class RiskCheckResult(
    val canTrade: Boolean,
    val reason: String,
    val riskAmount: Double = 0.0,
    val riskPercentage: Double = 0.0,
    val recommendedLeverage: Double? = null
)

// 交易统计
data class TradingStats(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalPnl: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val profitFactor: Double,
    val maxDrawdown: Double,
    val currentBalance: Double,
    val peakBalance: Double
)