package com.trading.orderflow.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.trading.orderflow.data.model.*

@Database(
    entities = [
        KlineData::class, 
        IndicatorData::class,
        RiskConfig::class,
        TradeRecord::class,
        AccountStatus::class,
        RiskEvent::class,
        TradingSignal::class,
        SignalConfig::class,
        BacktestConfig::class,
        BacktestResult::class,
        BacktestTrade::class,
        OptimizationResult::class,
        OpenInterestData::class,
        FundingRateData::class,
        LiquidationData::class,
        TopTraderPositionData::class,
        NotificationConfig::class,
        NotificationRecord::class,
        PriceAlert::class,
        User::class,
        UserSession::class
    ],
    version = 4, // 版本升级
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OrderFlowDatabase : RoomDatabase() {
    abstract fun klineDao(): KlineDao
    abstract fun indicatorDao(): IndicatorDao
    abstract fun riskDao(): RiskDao
    abstract fun signalDao(): SignalDao
    abstract fun backtestDao(): BacktestDao
    abstract fun derivativesDao(): DerivativesDao
    abstract fun notificationDao(): NotificationDao
    abstract fun authDao(): AuthDao
    
    companion object {
        const val DATABASE_NAME = "orderflow_database"
    }
}