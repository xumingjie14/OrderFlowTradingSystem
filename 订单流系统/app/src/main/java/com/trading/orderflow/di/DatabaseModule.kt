package com.trading.orderflow.di

import android.content.Context
import androidx.room.Room
import com.trading.orderflow.data.database.OrderFlowDatabase
import com.trading.orderflow.data.database.KlineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideOrderFlowDatabase(
        @ApplicationContext context: Context
    ): OrderFlowDatabase {
        return Room.databaseBuilder(
            context,
            OrderFlowDatabase::class.java,
            OrderFlowDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideKlineDao(database: OrderFlowDatabase): KlineDao {
        return database.klineDao()
    }
}