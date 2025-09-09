package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.IndicatorData
import kotlinx.coroutines.flow.Flow

@Dao
interface IndicatorDao {
    
    @Query("SELECT * FROM indicators WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getIndicators(symbol: String, interval: String, limit: Int = 1000): List<IndicatorData>
    
    @Query("SELECT * FROM indicators WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp DESC")
    fun getIndicatorFlow(symbol: String, interval: String): Flow<List<IndicatorData>>
    
    @Query("SELECT * FROM indicators WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestIndicator(symbol: String, interval: String): IndicatorData?
    
    @Query("SELECT * FROM indicators WHERE symbol = :symbol AND interval = :interval AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getIndicatorsInRange(symbol: String, interval: String, startTime: Long, endTime: Long): List<IndicatorData>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndicators(indicators: List<IndicatorData>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndicator(indicator: IndicatorData)
    
    @Query("DELETE FROM indicators WHERE symbol = :symbol AND interval = :interval AND timestamp < :beforeTime")
    suspend fun deleteOldIndicators(symbol: String, interval: String, beforeTime: Long)
    
    @Query("DELETE FROM indicators WHERE symbol = :symbol AND interval = :interval")
    suspend fun deleteAllIndicators(symbol: String, interval: String)
}