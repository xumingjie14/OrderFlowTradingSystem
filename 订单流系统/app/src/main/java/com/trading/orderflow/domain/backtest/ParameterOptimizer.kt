package com.trading.orderflow.domain.backtest

import android.util.Log
import com.trading.orderflow.data.database.BacktestDao
import com.trading.orderflow.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParameterOptimizer @Inject constructor(
    private val backtestEngine: BacktestEngine,
    private val backtestDao: BacktestDao,
    private val gson: Gson
) {
    
    companion object {
        private const val TAG = "ParameterOptimizer"
    }
    
    // 参数优化
    suspend fun optimizeParameters(
        baseConfig: BacktestConfig,
        parameterRanges: Map<String, ParameterRange>,
        targetMetric: String = "sharpe",
        maxIterations: Int = 100
    ): List<OptimizationResult> = withContext(Dispatchers.Default) {
        
        Log.d(TAG, "Starting parameter optimization with $maxIterations iterations")
        
        val results = mutableListOf<OptimizationResult>()
        val parameterCombinations = generateParameterCombinations(parameterRanges, maxIterations)
        
        for ((index, parameters) in parameterCombinations.withIndex()) {
            try {
                // 创建新的配置
                val optimizedConfig = baseConfig.copy(
                    id = "${baseConfig.id}_opt_$index",
                    name = "${baseConfig.name} - Optimization $index",
                    signalConfig = gson.toJson(parameters)
                )
                
                // 运行回测
                val backtestResult = backtestEngine.runBacktest(optimizedConfig)
                
                // 计算目标指标值
                val targetValue = when (targetMetric) {
                    "sharpe" -> backtestResult.sharpeRatio
                    "profit" -> backtestResult.totalPnlPercentage
                    "winRate" -> backtestResult.winRate
                    "profitFactor" -> backtestResult.profitFactor
                    "calmar" -> backtestResult.calmarRatio
                    else -> backtestResult.sharpeRatio
                }
                
                // 计算综合评分
                val score = calculateCompositeScore(backtestResult)
                
                val optimizationResult = OptimizationResult(
                    id = "opt_${System.currentTimeMillis()}_$index",
                    configId = baseConfig.id,
                    parameterSet = gson.toJson(parameters),
                    targetMetric = targetMetric,
                    targetValue = targetValue,
                    totalReturn = backtestResult.totalPnlPercentage,
                    maxDrawdown = backtestResult.maxDrawdownPercentage,
                    sharpeRatio = backtestResult.sharpeRatio,
                    winRate = backtestResult.winRate,
                    profitFactor = backtestResult.profitFactor,
                    totalTrades = backtestResult.totalTrades,
                    rank = 0, // 稍后排序
                    score = score
                )
                
                results.add(optimizationResult)
                
                Log.d(TAG, "Optimization $index completed: ${targetMetric}=${String.format("%.4f", targetValue)}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Optimization $index failed: ${e.message}")
            }
        }
        
        // 按目标指标排序并设置排名
        val sortedResults = results.sortedByDescending { it.targetValue }
        val rankedResults = sortedResults.mapIndexed { index, result ->
            result.copy(rank = index + 1)
        }
        
        // 保存优化结果
        backtestDao.insertOptimizationResults(rankedResults)
        
        Log.d(TAG, "Parameter optimization completed: ${rankedResults.size} results")
        
        return@withContext rankedResults
    }
    
    // 生成参数组合
    private fun generateParameterCombinations(
        parameterRanges: Map<String, ParameterRange>,
        maxCombinations: Int
    ): List<Map<String, Any>> {
        
        val combinations = mutableListOf<Map<String, Any>>()
        
        // 网格搜索
        if (parameterRanges.size <= 3) {
            combinations.addAll(generateGridSearch(parameterRanges))
        }
        
        // 随机搜索
        while (combinations.size < maxCombinations) {
            val randomCombination = mutableMapOf<String, Any>()
            
            for ((paramName, range) in parameterRanges) {
                randomCombination[paramName] = when (range.type) {
                    ParameterType.DOUBLE -> {
                        range.min + Math.random() * (range.max - range.min)
                    }
                    ParameterType.INT -> {
                        (range.min + Math.random() * (range.max - range.min + 1)).toInt()
                    }
                    ParameterType.BOOLEAN -> {
                        Math.random() < 0.5
                    }
                }
            }
            
            combinations.add(randomCombination)
        }
        
        return combinations.take(maxCombinations)
    }
    
    // 网格搜索
    private fun generateGridSearch(parameterRanges: Map<String, ParameterRange>): List<Map<String, Any>> {
        val combinations = mutableListOf<Map<String, Any>>()
        
        // 为简化实现，这里只处理最多3个参数的网格搜索
        val paramNames = parameterRanges.keys.toList()
        
        when (paramNames.size) {
            1 -> {
                val range = parameterRanges[paramNames[0]]!!
                val values = generateParameterValues(range)
                for (value in values) {
                    combinations.add(mapOf(paramNames[0] to value))
                }
            }
            2 -> {
                val range1 = parameterRanges[paramNames[0]]!!
                val range2 = parameterRanges[paramNames[1]]!!
                val values1 = generateParameterValues(range1)
                val values2 = generateParameterValues(range2)
                
                for (value1 in values1) {
                    for (value2 in values2) {
                        combinations.add(mapOf(
                            paramNames[0] to value1,
                            paramNames[1] to value2
                        ))
                    }
                }
            }
            3 -> {
                val range1 = parameterRanges[paramNames[0]]!!
                val range2 = parameterRanges[paramNames[1]]!!
                val range3 = parameterRanges[paramNames[2]]!!
                val values1 = generateParameterValues(range1)
                val values2 = generateParameterValues(range2)
                val values3 = generateParameterValues(range3)
                
                for (value1 in values1) {
                    for (value2 in values2) {
                        for (value3 in values3) {
                            combinations.add(mapOf(
                                paramNames[0] to value1,
                                paramNames[1] to value2,
                                paramNames[2] to value3
                            ))
                        }
                    }
                }
            }
        }
        
        return combinations
    }
    
    // 生成参数值
    private fun generateParameterValues(range: ParameterRange): List<Any> {
        return when (range.type) {
            ParameterType.DOUBLE -> {
                val step = (range.max - range.min) / (range.steps - 1)
                (0 until range.steps).map { range.min + it * step }
            }
            ParameterType.INT -> {
                val step = (range.max - range.min) / (range.steps - 1)
                (0 until range.steps).map { (range.min + it * step).toInt() }
            }
            ParameterType.BOOLEAN -> {
                listOf(true, false)
            }
        }
    }
    
    // 计算综合评分
    private fun calculateCompositeScore(result: BacktestResult): Double {
        // 综合评分公式：考虑收益、风险、胜率等多个因素
        val returnScore = result.totalPnlPercentage / 100.0 // 归一化收益
        val riskScore = 1.0 / (1.0 + result.maxDrawdownPercentage) // 风险惩罚
        val sharpeScore = result.sharpeRatio / 10.0 // 夏普比率
        val winRateScore = result.winRate
        val tradeCountScore = kotlin.math.min(result.totalTrades / 100.0, 1.0) // 交易次数奖励
        
        return (returnScore * 0.3 + riskScore * 0.2 + sharpeScore * 0.3 + winRateScore * 0.1 + tradeCountScore * 0.1)
    }
    
    // 获取优化结果
    suspend fun getOptimizationResults(configId: String): List<OptimizationResult> {
        return backtestDao.getOptimizationResults(configId)
    }
    
    // 获取最佳参数组合
    suspend fun getBestParameters(configId: String, metric: String = "score"): OptimizationResult? {
        return backtestDao.getTopOptimizationResults(configId, metric, 1).firstOrNull()
    }
}

// 参数范围定义
data class ParameterRange(
    val type: ParameterType,
    val min: Double,
    val max: Double,
    val steps: Int = 10
)

enum class ParameterType {
    DOUBLE, INT, BOOLEAN
}