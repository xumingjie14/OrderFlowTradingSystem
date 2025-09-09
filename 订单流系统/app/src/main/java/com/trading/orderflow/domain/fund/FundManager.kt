package com.trading.orderflow.domain.fund

import android.util.Log
import com.trading.orderflow.data.database.TradingDao
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FundManager @Inject constructor(
    private val tradingDao: TradingDao
) {
    
    companion object {
        private const val TAG = "FundManager"
        private val DEFAULT_RISK_PER_TRADE = BigDecimal("0.02") // 2%
        private val MAX_TOTAL_RISK = BigDecimal("0.06") // 6%
    }
    
    private val _balanceUpdates = MutableSharedFlow<List<AccountBalance>>()
    val balanceUpdates: SharedFlow<List<AccountBalance>> = _balanceUpdates.asSharedFlow()
    
    // 获取账户总价值
    suspend fun getTotalAccountValue(): BigDecimal {
        val balances = tradingDao.getAllBalances()
        return balances.sumOf { it.total * getAssetPrice(it.asset) }
    }
    
    // 获取可用资金
    suspend fun getAvailableFunds(asset: String = "USDT"): BigDecimal {
        val balance = tradingDao.getBalance(asset)
        return balance?.free ?: BigDecimal.ZERO
    }
    
    // 计算建议仓位大小
    suspend fun calculatePositionSize(
        symbol: String,
        entryPrice: BigDecimal,
        stopLoss: BigDecimal,
        riskPercentage: BigDecimal = DEFAULT_RISK_PER_TRADE
    ): BigDecimal {
        val totalValue = getTotalAccountValue()
        val riskAmount = totalValue * riskPercentage
        
        // 计算每股风险
        val priceRisk = (entryPrice - stopLoss).abs()
        
        if (priceRisk == BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        
        // 计算仓位大小
        val positionSize = riskAmount.divide(priceRisk, 8, RoundingMode.DOWN)
        
        // 检查资金是否足够
        val requiredFunds = positionSize * entryPrice
        val availableFunds = getAvailableFunds()
        
        return if (requiredFunds <= availableFunds) {
            positionSize
        } else {
            availableFunds.divide(entryPrice, 8, RoundingMode.DOWN)
        }
    }
    
    // 检查资金充足性
    suspend fun checkFundsSufficiency(request: PlaceOrderRequest): FundCheckResult {
        val requiredFunds = calculateRequiredFunds(request)
        val availableFunds = getAvailableFunds()
        
        return if (availableFunds >= requiredFunds) {
            FundCheckResult(
                sufficient = true,
                availableFunds = availableFunds,
                requiredFunds = requiredFunds
            )
        } else {
            FundCheckResult(
                sufficient = false,
                availableFunds = availableFunds,
                requiredFunds = requiredFunds,
                message = "资金不足，需要 $requiredFunds，可用 $availableFunds"
            )
        }
    }
    
    // 计算所需资金
    private suspend fun calculateRequiredFunds(request: PlaceOrderRequest): BigDecimal {
        val price = request.price ?: getCurrentPrice(request.symbol)
        val notionalValue = request.quantity * price
        
        // 添加手续费（0.1%）
        val commission = notionalValue * BigDecimal("0.001")
        
        return notionalValue + commission
    }
    
    // 更新账户余额
    suspend fun updateBalance(
        asset: String,
        freeChange: BigDecimal,
        lockedChange: BigDecimal = BigDecimal.ZERO
    ) {
        val currentBalance = tradingDao.getBalance(asset) ?: AccountBalance(
            asset = asset,
            free = BigDecimal.ZERO,
            locked = BigDecimal.ZERO,
            total = BigDecimal.ZERO
        )
        
        val newFree = currentBalance.free + freeChange
        val newLocked = currentBalance.locked + lockedChange
        val newTotal = newFree + newLocked
        
        val updatedBalance = currentBalance.copy(
            free = newFree,
            locked = newLocked,
            total = newTotal,
            updatedAt = System.currentTimeMillis()
        )
        
        tradingDao.insertBalance(updatedBalance)
        
        // 发送余额更新通知
        val allBalances = tradingDao.getAllBalances()
        _balanceUpdates.emit(allBalances)
        
        Log.d(TAG, "Balance updated for $asset: free=$newFree, locked=$newLocked")
    }
    
    // 冻结资金
    suspend fun freezeFunds(asset: String, amount: BigDecimal): Boolean {
        val balance = tradingDao.getBalance(asset)
        
        return if (balance != null && balance.free >= amount) {
            updateBalance(asset, -amount, amount)
            true
        } else {
            false
        }
    }
    
    // 解冻资金
    suspend fun unfreezeFunds(asset: String, amount: BigDecimal) {
        updateBalance(asset, amount, -amount)
    }
    
    // 计算当前风险敞口
    suspend fun getCurrentRiskExposure(): BigDecimal {
        val positions = tradingDao.getAllPositions()
        val totalValue = getTotalAccountValue()
        
        if (totalValue == BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        
        val totalRisk = positions.sumOf { position ->
            val positionValue = position.quantity * position.averagePrice
            positionValue.abs()
        }
        
        return totalRisk.divide(totalValue, 4, RoundingMode.HALF_UP)
    }
    
    // 检查风险限制
    suspend fun checkRiskLimits(newOrderValue: BigDecimal): RiskCheckResult {
        val currentRisk = getCurrentRiskExposure()
        val totalValue = getTotalAccountValue()
        val newRisk = newOrderValue.divide(totalValue, 4, RoundingMode.HALF_UP)
        val totalRisk = currentRisk + newRisk
        
        return if (totalRisk <= MAX_TOTAL_RISK) {
            RiskCheckResult(
                allowed = true,
                currentRisk = currentRisk,
                newRisk = newRisk,
                totalRisk = totalRisk
            )
        } else {
            RiskCheckResult(
                allowed = false,
                currentRisk = currentRisk,
                newRisk = newRisk,
                totalRisk = totalRisk,
                message = "超出最大风险限制 ${MAX_TOTAL_RISK * BigDecimal("100")}%"
            )
        }
    }
    
    // 获取资产价格（模拟）
    private suspend fun getAssetPrice(asset: String): BigDecimal {
        return when (asset) {
            "USDT" -> BigDecimal.ONE
            "BTC" -> BigDecimal("45000")
            "ETH" -> BigDecimal("3000")
            "BNB" -> BigDecimal("300")
            else -> BigDecimal.ONE
        }
    }
    
    // 获取当前价格（模拟）
    private suspend fun getCurrentPrice(symbol: String): BigDecimal {
        return when (symbol) {
            "BTCUSDT" -> BigDecimal("45000.00")
            "ETHUSDT" -> BigDecimal("3000.00")
            "BNBUSDT" -> BigDecimal("300.00")
            else -> BigDecimal("100.00")
        }
    }
    
    // 初始化默认余额
    suspend fun initializeDefaultBalance() {
        val existingBalance = tradingDao.getBalance("USDT")
        if (existingBalance == null) {
            val defaultBalance = AccountBalance(
                asset = "USDT",
                free = BigDecimal("10000.00"), // 默认10000 USDT
                locked = BigDecimal.ZERO,
                total = BigDecimal("10000.00")
            )
            tradingDao.insertBalance(defaultBalance)
            Log.d(TAG, "Default balance initialized: 10000 USDT")
        }
    }
}

// 资金检查结果
data class FundCheckResult(
    val sufficient: Boolean,
    val availableFunds: BigDecimal,
    val requiredFunds: BigDecimal,
    val message: String? = null
)

// 风险检查结果
data class RiskCheckResult(
    val allowed: Boolean,
    val currentRisk: BigDecimal,
    val newRisk: BigDecimal,
    val totalRisk: BigDecimal,
    val message: String? = null
)