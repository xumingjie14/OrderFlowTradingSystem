package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BacktestDao {
    
    // 回测配置
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktestConfig(config: BacktestConfig)
    
    @Query("SELECT * FROM backtest_config WHERE id = :configId")
    suspend fun getBacktestConfig(configId: String): BacktestConfig?
    
    @Query("SELECT * FROM backtest_config ORDER BY createdAt DESC")
    suspend fun getAllBacktestConfigs(): List<BacktestConfig>
    
    @Query("DELETE FROM backtest_config WHERE id = :configId")
    suspend fun deleteBacktestConfig(configId: String)
    
    // 回测结果
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktestResult(result: BacktestResult)
    
    @Query("SELECT * FROM backtest_results WHERE id = :backtestId")
    suspend fun getBacktestResult(backtestId: String): BacktestResult?
    
    @Query("SELECT * FROM backtest_results WHERE configId = :configId ORDER BY completedAt DESC")
    suspend fun getBacktestResultsByConfig(configId: String): List<BacktestResult>
    
    @Query("SELECT * FROM backtest_results ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getRecentBacktestResults(limit: Int = 50): List<BacktestResult>
    
    @Query("DELETE FROM backtest_results WHERE id = :backtestId")
    suspend fun deleteBacktestResult(backtestId: String)
    
    // 回测交易记录
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktestTrades(trades: List<BacktestTrade>)
    
    @Query("SELECT * FROM backtest_trades WHERE backtestId = :backtestId ORDER BY entryTime ASC")
    suspend fun getBacktestTrades(backtestId: String): List<BacktestTrade>
    
    @Query("SELECT * FROM backtest_trades WHERE backtestId = :backtestId AND netPnl > 0 ORDER BY netPnl DESC")
    suspend fun getWinningTrades(backtestId: String): List<BacktestTrade>
    
    @Query("SELECT * FROM backtest_trades WHERE backtestId = :backtestId AND netPnl < 0 ORDER BY netPnl ASC")
    suspend fun getLosingTrades(backtestId: String): List<BacktestTrade>
    
    @Query("DELETE FROM backtest_trades WHERE backtestId = :backtestId")
    suspend fun deleteBacktestTrades(backtestId: String)
    
    // 优化结果
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptimizationResults(results: List<OptimizationResult>)
    
    @Query("SELECT * FROM optimization_results WHERE configId = :configId ORDER BY rank ASC")
    suspend fun getOptimizationResults(configId: String): List<OptimizationResult>
    
    @Query("SELECT * FROM optimization_results WHERE configId = :configId ORDER BY :orderBy DESC LIMIT :limit")
    suspend fun getTopOptimizationResults(configId: String, orderBy: String, limit: Int = 10): List<OptimizationResult>
    
    @Query("DELETE FROM optimization_results WHERE configId = :configId")
    suspend fun deleteOptimizationResults(configId: String)
    
    // 快照数据（如果需要存储）
    suspend fun getBacktestSnapshots(backtestId: String): List<BacktestSnapshot> {
        // 这里可以实现快照数据的存储和查询
        // 为了简化，暂时返回空列表
        return emptyList()
    }
    
    // 清理旧数据
    @Query("DELETE FROM backtest_results WHERE completedAt < :beforeTime")
    suspend fun deleteOldBacktestResults(beforeTime: Long)
    
    @Query("DELETE FROM backtest_trades WHERE createdAt < :beforeTime")
    suspend fun deleteOldBacktestTrades(beforeTime: Long)
}