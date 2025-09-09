package com.trading.orderflow.di

import com.trading.orderflow.data.database.IndicatorDao
import com.trading.orderflow.data.database.OrderFlowDatabase
import com.trading.orderflow.domain.calculator.IndicatorCalculator
import com.trading.orderflow.domain.calculator.IndicatorCalculatorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IndicatorModule {
    
    @Provides
    @Singleton
    fun provideIndicatorCalculator(): IndicatorCalculator {
        return IndicatorCalculatorImpl()
    }
    
    @Provides
    fun provideIndicatorDao(database: OrderFlowDatabase): IndicatorDao {
        return database.indicatorDao()
    }
}