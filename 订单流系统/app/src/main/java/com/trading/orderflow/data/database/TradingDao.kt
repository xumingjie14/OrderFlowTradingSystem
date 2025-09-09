package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingDao {
    
    // 订单相关
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TradingOrder)
    
    @Update
    suspend fun updateOrder(order: TradingOrder)
    
    @Query("SELECT * FROM trading_orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): TradingOrder?
    
    @Query("SELECT * FROM trading_orders ORDER BY createdAt DESC")
    fun getAllOrdersFlow(): Flow<List<TradingOrder>>
    
    @Query("SELECT * FROM trading_orders WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getActiveOrdersFlow(): Flow<List<TradingOrder>>
    
    @Query("SELECT * FROM trading_orders WHERE symbol = :symbol ORDER BY createdAt DESC")
    fun getOrdersBySymbol(symbol: String): Flow<List<TradingOrder>>
    
    // 持仓相关
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: Position)
    
    @Update
    suspend fun updatePosition(position: Position)
    
    @Query("SELECT * FROM positions WHERE symbol = :symbol")
    suspend fun getPosition(symbol: String): Position?
    
    @Query("SELECT * FROM positions WHERE quantity > 0")
    fun getAllPositionsFlow(): Flow<List<Position>>
    
    @Query("SELECT * FROM positions WHERE quantity > 0")
    suspend fun getAllPositions(): List<Position>
    
    @Query("DELETE FROM positions WHERE symbol = :symbol")
    suspend fun deletePosition(symbol: String)
    
    // 交易记录相关
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: Trade)
    
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTradesFlow(): Flow<List<Trade>>
    
    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getTradesBySymbol(symbol: String): Flow<List<Trade>>
    
    // 账户余额相关
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(balance: AccountBalance)
    
    @Query("SELECT * FROM account_balance WHERE asset = :asset")
    suspend fun getBalance(asset: String): AccountBalance?
    
    @Query("SELECT * FROM account_balance")
    suspend fun getAllBalances(): List<AccountBalance>
    
    @Query("SELECT * FROM account_balance")
    fun getAllBalancesFlow(): Flow<List<AccountBalance>>
}