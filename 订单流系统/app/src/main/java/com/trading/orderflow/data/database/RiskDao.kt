package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskDao {
    
    // 风控配置
    @Query("SELECT * FROM risk_config WHERE id = 'default' LIMIT 1")
    suspend fun getRiskConfig(): RiskConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskConfig(config: RiskConfig)
    
    // 账户状态
    @Query("SELECT * FROM account_status WHERE id = 'main' LIMIT 1")
    suspend fun getAccountStatus(): AccountStatus?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountStatus(status: AccountStatus)
    
    @Query("SELECT * FROM account_status WHERE id = 'main'")
    fun getAccountStatusFlow(): Flow<AccountStatus?>
    
    // 交易记录
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeRecord)
    
    @Update
    suspend fun updateTrade(trade: TradeRecord)
    
    @Query("SELECT * FROM trades WHERE status = 'OPEN' ORDER BY entryTime DESC")
    suspend fun getOpenTrades(): List<TradeRecord>
    
    @Query("SELECT * FROM trades ORDER BY entryTime DESC LIMIT :limit")
    suspend fun getAllTrades(limit: Int = 1000): List<TradeRecord>
    
    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY entryTime DESC LIMIT :limit")
    suspend fun getTradesBySymbol(symbol: String, limit: Int = 100): List<TradeRecord>
    
    @Query("SELECT * FROM trades WHERE status = 'CLOSED' AND exitTime >= :startTime AND exitTime <= :endTime ORDER BY exitTime DESC")
    suspend fun getTradesInRange(startTime: Long, endTime: Long): List<TradeRecord>
    
    // 风控事件
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskEvent(event: RiskEvent)
    
    @Query("SELECT * FROM risk_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRiskEvents(limit: Int = 100): List<RiskEvent>
    
    @Query("SELECT * FROM risk_events WHERE severity = :severity ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRiskEventsBySeverity(severity: RiskSeverity, limit: Int = 50): List<RiskEvent>
    
    @Query("SELECT * FROM risk_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getRecentRiskEvents(startTime: Long): List<RiskEvent>
    
    // 清理旧数据
    @Query("DELETE FROM trades WHERE exitTime < :beforeTime AND status = 'CLOSED'")
    suspend fun deleteOldTrades(beforeTime: Long)
    
    @Query("DELETE FROM risk_events WHERE timestamp < :beforeTime")
    suspend fun deleteOldRiskEvents(beforeTime: Long)
}