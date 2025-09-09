package com.trading.orderflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trading.orderflow.data.repository.MarketDataRepository
import com.trading.orderflow.domain.risk.RiskManager
import com.trading.orderflow.domain.signal.SignalEngine
import com.trading.orderflow.ui.screens.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val marketDataRepository: MarketDataRepository,
    private val riskManager: RiskManager,
    private val signalEngine: SignalEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // 加载市场数据
                val marketData = loadMarketData()
                
                // 加载账户状态
                val accountStatus = loadAccountStatus()
                
                // 加载活跃信号
                val activeSignals = loadActiveSignals()
                
                // 加载最近交易
                val recentTrades = loadRecentTrades()
                
                // 加载风控状态
                val riskStatus = loadRiskStatus()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    marketData = marketData,
                    accountStatus = accountStatus,
                    activeSignals = activeSignals,
                    recentTrades = recentTrades,
                    riskStatus = riskStatus
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    private suspend fun loadMarketData(): List<MarketDataItem> {
        val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        return symbols.mapNotNull { symbol ->
            marketDataRepository.get24hrTicker(symbol).getOrNull()?.let { ticker ->
                MarketDataItem(
                    symbol = symbol,
                    price = ticker.lastPrice.toDouble(),
                    change = ticker.priceChange.toDouble(),
                    changePercent = ticker.priceChangePercent.toDouble()
                )
            }
        }
    }
    
    private suspend fun loadAccountStatus(): AccountStatusItem? {
        return riskManager.getAccountStatus().let { status ->
            AccountStatusItem(
                totalBalance = status.totalBalance,
                availableBalance = status.availableBalance,
                unrealizedPnl = status.unrealizedPnl,
                currentRisk = status.currentRisk
            )
        }
    }
    
    private suspend fun loadActiveSignals(): List<SignalItem> {
        return signalEngine.getActiveSignals().map { signal ->
            SignalItem(
                id = signal.id,
                symbol = signal.symbol,
                type = signal.signalType.name,
                strength = signal.strength.name,
                price = signal.price,
                score = signal.totalScore,
                timestamp = signal.timestamp
            )
        }
    }
    
    private suspend fun loadRecentTrades(): List<TradeItem> {
        // 从风控管理器获取最近交易
        return emptyList() // 简化实现
    }
    
    private suspend fun loadRiskStatus(): RiskStatusItem? {
        val stats = riskManager.getTradingStats()
        val accountStatus = riskManager.getAccountStatus()
        
        return RiskStatusItem(
            maxDrawdown = stats.maxDrawdown,
            winRate = stats.winRate,
            consecutiveLosses = accountStatus.consecutiveLosses,
            isInCooldown = accountStatus.isInCooldown
        )
    }
    
    fun refreshData() {
        loadDashboardData()
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val marketData: List<MarketDataItem> = emptyList(),
    val accountStatus: AccountStatusItem? = null,
    val activeSignals: List<SignalItem> = emptyList(),
    val recentTrades: List<TradeItem> = emptyList(),
    val riskStatus: RiskStatusItem? = null,
    val error: String? = null
)