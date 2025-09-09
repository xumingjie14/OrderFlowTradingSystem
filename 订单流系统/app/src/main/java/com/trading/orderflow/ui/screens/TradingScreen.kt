package com.trading.orderflow.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trading.orderflow.data.model.*
import com.trading.orderflow.ui.viewmodel.TradingViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingScreen(
    viewModel: TradingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 账户信息
        AccountInfoCard(
            totalValue = uiState.totalAccountValue,
            availableFunds = uiState.availableFunds,
            currentRisk = uiState.currentRisk
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 交易面板
        TradingPanel(
            onPlaceOrder = { request ->
                viewModel.placeOrder(request)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 订单和持仓列表
        TabRow(
            selectedTabIndex = uiState.selectedTab
        ) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("活跃订单") }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text("持仓") }
            )
            Tab(
                selected = uiState.selectedTab == 2,
                onClick = { viewModel.selectTab(2) },
                text = { Text("历史订单") }
            )
        }
        
        when (uiState.selectedTab) {
            0 -> ActiveOrdersList(
                orders = uiState.activeOrders,
                onCancelOrder = { orderId ->
                    viewModel.cancelOrder(orderId)
                }
            )
            1 -> PositionsList(
                positions = uiState.positions,
                onClosePosition = { symbol ->
                    viewModel.closePosition(symbol)
                }
            )
            2 -> OrderHistoryList(
                orders = uiState.allOrders
            )
        }
    }
}

@Composable
fun AccountInfoCard(
    totalValue: BigDecimal,
    availableFunds: BigDecimal,
    currentRisk: BigDecimal
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "账户信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "总价值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${totalValue}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column {
                    Text(
                        text = "可用资金",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${availableFunds}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column {
                    Text(
                        text = "当前风险",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(currentRisk * BigDecimal("100")).setScale(2)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentRisk > BigDecimal("0.05")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TradingPanel(
    onPlaceOrder: (PlaceOrderRequest) -> Unit
) {
    var symbol by remember { mutableStateOf("BTCUSDT") }
    var side by remember { mutableStateOf(OrderSide.BUY) }
    var type by remember { mutableStateOf(OrderType.MARKET) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "下单交易",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 交易对选择
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                label = { Text("交易对") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 买卖方向
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = side == OrderSide.BUY,
                    onClick = { side = OrderSide.BUY },
                    label = { Text("买入") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = side == OrderSide.SELL,
                    onClick = { side = OrderSide.SELL },
                    label = { Text("卖出") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 订单类型
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = type == OrderType.MARKET,
                    onClick = { type = OrderType.MARKET },
                    label = { Text("市价") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = type == OrderType.LIMIT,
                    onClick = { type = OrderType.LIMIT },
                    label = { Text("限价") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 数量输入
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("数量") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 价格输入（限价单）
            if (type == OrderType.LIMIT) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("价格") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 下单按钮
            Button(
                onClick = {
                    val request = PlaceOrderRequest(
                        symbol = symbol,
                        side = side,
                        type = type,
                        quantity = BigDecimal(quantity),
                        price = if (type == OrderType.LIMIT && price.isNotEmpty()) {
                            BigDecimal(price)
                        } else null
                    )
                    onPlaceOrder(request)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = quantity.isNotEmpty() && (type == OrderType.MARKET || price.isNotEmpty())
            ) {
                Text("${if (side == OrderSide.BUY) "买入" else "卖出"} $symbol")
            }
        }
    }
}

@Composable
fun ActiveOrdersList(
    orders: List<TradingOrder>,
    onCancelOrder: (String) -> Unit
) {
    LazyColumn {
        items(orders) { order ->
            OrderItem(
                order = order,
                showCancelButton = true,
                onCancelOrder = onCancelOrder
            )
        }
    }
}

@Composable
fun PositionsList(
    positions: List<Position>,
    onClosePosition: (String) -> Unit
) {
    LazyColumn {
        items(positions) { position ->
            PositionItem(
                position = position,
                onClosePosition = onClosePosition
            )
        }
    }
}

@Composable
fun OrderHistoryList(
    orders: List<TradingOrder>
) {
    LazyColumn {
        items(orders) { order ->
            OrderItem(
                order = order,
                showCancelButton = false,
                onCancelOrder = {}
            )
        }
    }
}

@Composable
fun OrderItem(
    order: TradingOrder,
    showCancelButton: Boolean,
    onCancelOrder: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${order.side.name} ${order.type.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (order.side == OrderSide.BUY) {
                            Color.Green
                        } else {
                            Color.Red
                        }
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = order.quantity.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = order.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (order.status) {
                            OrderStatus.FILLED -> Color.Green
                            OrderStatus.CANCELLED -> Color.Gray
                            OrderStatus.REJECTED -> Color.Red
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                
                if (showCancelButton && order.status == OrderStatus.PENDING) {
                    IconButton(
                        onClick = { onCancelOrder(order.orderId) }
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "取消订单",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PositionItem(
    position: Position,
    onClosePosition: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = position.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = position.side.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (position.side == OrderSide.BUY) {
                            Color.Green
                        } else {
                            Color.Red
                        }
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "数量: ${position.quantity}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "均价: ${position.averagePrice}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "盈亏: ${position.unrealizedPnl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (position.unrealizedPnl >= BigDecimal.ZERO) {
                            Color.Green
                        } else {
                            Color.Red
                        }
                    )
                }
                
                IconButton(
                    onClick = { onClosePosition(position.symbol) }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "平仓",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}