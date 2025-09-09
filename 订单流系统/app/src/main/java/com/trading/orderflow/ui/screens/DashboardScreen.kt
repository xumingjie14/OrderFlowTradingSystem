package com.trading.orderflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.trading.orderflow.ui.components.*
import com.trading.orderflow.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部欢迎区域
        item {
            WelcomeSection()
        }
        
        // 市场概览卡片
        item {
            MarketOverviewSection(uiState.marketData)
        }
        
        // 账户状态卡片
        item {
            AccountStatusSection(uiState.accountStatus)
        }
        
        // 活跃信号卡片
        item {
            ActiveSignalsSection(
                signals = uiState.activeSignals,
                onSignalClick = { signal ->
                    // 导航到信号详情
                    navController.navigate("signal_detail/${signal.id}")
                }
            )
        }
        
        // 最近交易
        item {
            RecentTradesSection(uiState.recentTrades)
        }
        
        // 风控状态
        item {
            RiskStatusSection(uiState.riskStatus)
        }
    }
}

@Composable
fun WelcomeSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "订单流交易系统",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "实时监控市场动态，智能信号提醒",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MarketOverviewSection(marketData: List<MarketDataItem>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "市场概览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(marketData) { item ->
                    MarketDataCard(item)
                }
            }
        }
    }
}

@Composable
fun MarketDataCard(item: MarketDataItem) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.symbol,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "$${String.format("%.2f", item.price)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.change >= 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (item.change >= 0) Color.Green else Color.Red
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.2f%%", item.changePercent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.change >= 0) Color.Green else Color.Red,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AccountStatusSection(accountStatus: AccountStatusItem?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账户状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            accountStatus?.let { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AccountMetricItem(
                        label = "总余额",
                        value = "$${String.format("%.2f", status.totalBalance)}",
                        icon = Icons.Filled.AccountBalanceWallet
                    )
                    
                    AccountMetricItem(
                        label = "可用余额",
                        value = "$${String.format("%.2f", status.availableBalance)}",
                        icon = Icons.Filled.Money
                    )
                    
                    AccountMetricItem(
                        label = "未实现盈亏",
                        value = "$${String.format("%.2f", status.unrealizedPnl)}",
                        icon = Icons.Filled.TrendingUp,
                        valueColor = if (status.unrealizedPnl >= 0) Color.Green else Color.Red
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 风险指标
                LinearProgressIndicator(
                    progress = (status.currentRisk / 0.1).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        status.currentRisk > 0.08 -> Color.Red
                        status.currentRisk > 0.05 -> Color.Yellow
                        else -> Color.Green
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "当前风险: ${String.format("%.2f%%", status.currentRisk * 100)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountMetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActiveSignalsSection(
    signals: List<SignalItem>,
    onSignalClick: (SignalItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "活跃信号",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge {
                    Text(text = signals.size.toString())
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (signals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无活跃信号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    signals.take(3).forEach { signal ->
                        SignalCard(
                            signal = signal,
                            onClick = { onSignalClick(signal) }
                        )
                    }
                    
                    if (signals.size > 3) {
                        TextButton(
                            onClick = { /* 导航到信号页面 */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("查看全部 ${signals.size} 个信号")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalCard(
    signal: SignalItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (signal.strength) {
                "STRONG" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                "MEDIUM" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = signal.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = when (signal.type) {
                            "LONG" -> Color.Green
                            "SHORT" -> Color.Red
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    ) {
                        Text(
                            text = signal.type,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "价格: $${String.format("%.2f", signal.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = signal.strength,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = when (signal.strength) {
                        "STRONG" -> Color.Red
                        "MEDIUM" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = "${String.format("%.1f", signal.score)}分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecentTradesSection(trades: List<TradeItem>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "最近交易",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (trades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无交易记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trades.take(3).forEach { trade ->
                        TradeCard(trade)
                    }
                }
            }
        }
    }
}

@Composable
fun TradeCard(trade: TradeItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${trade.symbol} ${trade.side}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = trade.time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "${if (trade.pnl >= 0) "+" else ""}$${String.format("%.2f", trade.pnl)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (trade.pnl >= 0) Color.Green else Color.Red
        )
    }
}

@Composable
fun RiskStatusSection(riskStatus: RiskStatusItem?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "风控状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            riskStatus?.let { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RiskMetricItem(
                        label = "最大回撤",
                        value = "${String.format("%.2f%%", status.maxDrawdown * 100)}",
                        isWarning = status.maxDrawdown > 0.1
                    )
                    
                    RiskMetricItem(
                        label = "胜率",
                        value = "${String.format("%.1f%%", status.winRate * 100)}",
                        isWarning = status.winRate < 0.5
                    )
                    
                    RiskMetricItem(
                        label = "连续亏损",
                        value = status.consecutiveLosses.toString(),
                        isWarning = status.consecutiveLosses >= 3
                    )
                }
                
                if (status.isInCooldown) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "账户处于冷却期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiskMetricItem(
    label: String,
    value: String,
    isWarning: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) Color.Red else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 数据类
data class MarketDataItem(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)

data class AccountStatusItem(
    val totalBalance: Double,
    val availableBalance: Double,
    val unrealizedPnl: Double,
    val currentRisk: Double
)

data class SignalItem(
    val id: String,
    val symbol: String,
    val type: String,
    val strength: String,
    val price: Double,
    val score: Double,
    val timestamp: Long
)

data class TradeItem(
    val symbol: String,
    val side: String,
    val pnl: Double,
    val time: String
)

data class RiskStatusItem(
    val maxDrawdown: Double,
    val winRate: Double,
    val consecutiveLosses: Int,
    val isInCooldown: Boolean
)