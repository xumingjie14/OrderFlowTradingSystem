package com.trading.orderflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.trading.orderflow.ui.viewmodel.ChartViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    navController: NavController,
    viewModel: ChartViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSymbol by remember { mutableStateOf("BTCUSDT") }
    var selectedInterval by remember { mutableStateOf("4h") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部控制栏
        TopAppBar(
            title = { Text("K线图表") },
            actions = {
                IconButton(onClick = { /* 设置 */ }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 交易对选择
        SymbolSelector(
            selectedSymbol = selectedSymbol,
            onSymbolSelected = { 
                selectedSymbol = it
                viewModel.loadChartData(it, selectedInterval)
            }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 时间周期选择
        IntervalSelector(
            selectedInterval = selectedInterval,
            onIntervalSelected = {
                selectedInterval = it
                viewModel.loadChartData(selectedSymbol, it)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // K线图表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                CandlestickChart(
                    data = uiState.chartData,
                    indicators = uiState.indicators
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 技术指标面板
        IndicatorPanel(
            indicators = uiState.currentIndicators,
            onIndicatorToggle = { indicator, enabled ->
                viewModel.toggleIndicator(indicator, enabled)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 信号信息
        SignalInfoPanel(
            currentSignal = uiState.currentSignal,
            signalHistory = uiState.signalHistory
        )
    }
}

@Composable
fun SymbolSelector(
    selectedSymbol: String,
    onSymbolSelected: (String) -> Unit
) {
    val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "DOTUSDT")
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(symbols) { symbol ->
            FilterChip(
                onClick = { onSymbolSelected(symbol) },
                label = { Text(symbol) },
                selected = symbol == selectedSymbol
            )
        }
    }
}

@Composable
fun IntervalSelector(
    selectedInterval: String,
    onIntervalSelected: (String) -> Unit
) {
    val intervals = listOf("1h", "4h", "1d", "1w")
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(intervals) { interval ->
            FilterChip(
                onClick = { onIntervalSelected(interval) },
                label = { Text(interval) },
                selected = interval == selectedInterval
            )
        }
    }
}

@Composable
fun CandlestickChart(
    data: List<CandleEntry>,
    indicators: Map<String, List<Entry>>
) {
    AndroidView(
        factory = { context ->
            CandleStickChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                
                // 设置图表数据
                val candleDataSet = CandleDataSet(data, "Price").apply {
                    shadowColor = Color.Gray.hashCode()
                    shadowWidth = 0.7f
                    decreasingColor = Color.Red.hashCode()
                    decreasingPaintStyle = android.graphics.Paint.Style.FILL
                    increasingColor = Color.Green.hashCode()
                    increasingPaintStyle = android.graphics.Paint.Style.STROKE
                    neutralColor = Color.Blue.hashCode()
                }
                
                val candleData = CandleData(candleDataSet)
                this.data = candleData
                
                // 添加技术指标线
                indicators.forEach { (name, entries) ->
                    val lineDataSet = LineDataSet(entries, name).apply {
                        color = when (name) {
                            "EMA12" -> Color.Blue.hashCode()
                            "EMA26" -> Color.Red.hashCode()
                            "EMA50" -> Color.Green.hashCode()
                            else -> Color.Yellow.hashCode()
                        }
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                }
                
                invalidate()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun IndicatorPanel(
    indicators: Map<String, Boolean>,
    onIndicatorToggle: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "技术指标",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(indicators.toList()) { (indicator, enabled) ->
                    FilterChip(
                        onClick = { onIndicatorToggle(indicator, !enabled) },
                        label = { Text(indicator) },
                        selected = enabled
                    )
                }
            }
        }
    }
}

@Composable
fun SignalInfoPanel(
    currentSignal: SignalInfo?,
    signalHistory: List<SignalInfo>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "信号信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            currentSignal?.let { signal ->
                CurrentSignalCard(signal)
            } ?: run {
                Text(
                    text = "暂无活跃信号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (signalHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "历史信号",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    signalHistory.take(3).forEach { signal ->
                        HistorySignalItem(signal)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentSignalCard(signal: SignalInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (signal.strength) {
                "STRONG" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                "MEDIUM" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(
                        containerColor = when (signal.type) {
                            "LONG" -> Color.Green
                            "SHORT" -> Color.Red
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    ) {
                        Text(
                            text = signal.type,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = signal.strength,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${String.format("%.1f", signal.score)}分",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "入场价: $${String.format("%.2f", signal.entryPrice)}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                signal.stopLoss?.let {
                    Text(
                        text = "止损: $${String.format("%.2f", it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
                
                signal.takeProfit?.let {
                    Text(
                        text = "止盈: $${String.format("%.2f", it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
            }
        }
    }
}

@Composable
fun HistorySignalItem(signal: SignalInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(
                containerColor = when (signal.type) {
                    "LONG" -> Color.Green
                    "SHORT" -> Color.Red
                    else -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(16.dp)
            ) {
                Text(
                    text = signal.type.take(1),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = signal.time,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Text(
            text = "$${String.format("%.2f", signal.entryPrice)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// 数据类
data class SignalInfo(
    val type: String,
    val strength: String,
    val score: Double,
    val entryPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val time: String
)