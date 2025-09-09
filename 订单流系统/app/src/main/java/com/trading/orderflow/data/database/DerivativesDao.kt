package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DerivativesDao {
    
    // 持仓量数据
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenInterest(data: OpenInterestData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenInterestBatch(dataList: List<OpenInterestData>)
    
    @Query("SELECT * FROM open_interest WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestOpenInterest(symbol: String): OpenInterestData?
    
    @Query("SELECT * FROM open_interest WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getOpenInterestHistory(symbol: String, limit: Int): List<OpenInterestData>
    
    @Query("SELECT * FROM open_interest WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    fun getOpenInterestFlow(symbol: String): Flow<OpenInterestData?>
    
    // 资金费率数据
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFundingRate(data: FundingRateData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFundingRateBatch(dataList: List<FundingRateData>)
    
    @Query("SELECT * FROM funding_rates WHERE symbol = :symbol ORDER BY fundingTime DESC LIMIT 1")
    suspend fun getLatestFundingRate(symbol: String): FundingRateData?
    
    @Query("SELECT * FROM funding_rates WHERE symbol = :symbol ORDER BY fundingTime DESC LIMIT :limit")
    suspend fun getFundingRateHistory(symbol: String, limit: Int): List<FundingRateData>
    
    @Query("SELECT * FROM funding_rates WHERE symbol = :symbol ORDER BY fundingTime DESC LIMIT 1")
    fun getFundingRateFlow(symbol: String): Flow<FundingRateData?>
    
    // 清算数据
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiquidation(data: LiquidationData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiquidationBatch(dataList: List<LiquidationData>)
    
    @Query("SELECT * FROM liquidations WHERE symbol = :symbol ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getLiquidationHistory(symbol: String, limit: Int): List<LiquidationData>
    
    @Query("SELECT * FROM liquidations WHERE symbol = :symbol ORDER BY eventTime DESC LIMIT 100")
    fun getLiquidationFlow(symbol: String): Flow<List<LiquidationData>>
    
    // 大户持仓数据
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopTraderPosition(data: TopTraderPositionData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopTraderPositionBatch(dataList: List<TopTraderPositionData>)
    
    @Query("SELECT * FROM top_trader_positions WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getTopTraderPositionHistory(symbol: String, limit: Int): List<TopTraderPositionData>
    
    // 清理旧数据
    @Query("DELETE FROM open_interest WHERE createdAt < :beforeTime")
    suspend fun deleteOldOpenInterest(beforeTime: Long)
    
    @Query("DELETE FROM funding_rates WHERE createdAt < :beforeTime")
    suspend fun deleteOldFundingRates(beforeTime: Long)
    
    @Query("DELETE FROM liquidations WHERE createdAt < :beforeTime")
    suspend fun deleteOldLiquidations(beforeTime: Long)
    
    @Query("DELETE FROM top_trader_positions WHERE createdAt < :beforeTime")
    suspend fun deleteOldTopTraderPositions(beforeTime: Long)
}