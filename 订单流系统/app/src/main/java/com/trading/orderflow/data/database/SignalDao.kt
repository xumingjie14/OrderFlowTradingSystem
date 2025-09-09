package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    
    // 信号配置
    @Query("SELECT * FROM signal_config WHERE id = 'default' LIMIT 1")
    suspend fun getSignalConfig(): SignalConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalConfig(config: SignalConfig)
    
    // 交易信号
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: TradingSignal)
    
    @Query("SELECT * FROM trading_signals WHERE isActive = 1 AND (expiryTime IS NULL OR expiryTime > :currentTime) ORDER BY timestamp DESC")
    suspend fun getActiveSignals(currentTime: Long): List<TradingSignal>
    
    @Query("SELECT * FROM trading_signals WHERE symbol = :symbol AND isActive = 1 AND (expiryTime IS NULL OR expiryTime > :currentTime) ORDER BY timestamp DESC")
    suspend fun getActiveSignalsBySymbol(symbol: String, currentTime: Long): List<TradingSignal>
    
    @Query("SELECT * FROM trading_signals WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSignalsBySymbol(symbol: String, limit: Int): List<TradingSignal>
    
    @Query("SELECT * FROM trading_signals ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllSignals(limit: Int): List<TradingSignal>
    
    @Query("SELECT * FROM trading_signals WHERE signalType = :signalType AND strength = :strength ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSignalsByTypeAndStrength(signalType: SignalType, strength: SignalStrength, limit: Int): List<TradingSignal>
    
    @Query("UPDATE trading_signals SET isActive = 0, notes = :reason WHERE id = :signalId")
    suspend fun deactivateSignal(signalId: String, reason: String)
    
    @Query("UPDATE trading_signals SET isActive = 0, notes = 'Expired' WHERE expiryTime <= :currentTime AND isActive = 1")
    suspend fun deactivateExpiredSignals(currentTime: Long)
    
    @Query("SELECT * FROM trading_signals WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getSignalsInRange(startTime: Long, endTime: Long): List<TradingSignal>
    
    @Query("DELETE FROM trading_signals WHERE timestamp < :beforeTime")
    suspend fun deleteOldSignals(beforeTime: Long)
    
    @Query("SELECT * FROM trading_signals WHERE isActive = 1")
    fun getActiveSignalsFlow(): Flow<List<TradingSignal>>
}