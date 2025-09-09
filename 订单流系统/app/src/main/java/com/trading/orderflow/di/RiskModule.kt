package com.trading.orderflow.di

import com.trading.orderflow.data.database.OrderFlowDatabase
import com.trading.orderflow.data.database.RiskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RiskModule {
    
    @Provides
    fun provideRiskDao(database: OrderFlowDatabase): RiskDao {
        return database.riskDao()
    }
}