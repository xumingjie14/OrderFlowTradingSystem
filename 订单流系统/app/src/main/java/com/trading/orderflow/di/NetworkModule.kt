package com.trading.orderflow.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trading.orderflow.BuildConfig
import com.trading.orderflow.data.api.BinanceApiService
import com.trading.orderflow.data.api.BinanceFuturesApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    @Named("spot")
    fun provideSpotRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.binance.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    @Named("futures")
    fun provideFuturesRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://fapi.binance.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideBinanceApiService(
        @Named("spot") retrofit: Retrofit
    ): BinanceApiService {
        return retrofit.create(BinanceApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideBinanceFuturesApiService(
        @Named("futures") retrofit: Retrofit
    ): BinanceFuturesApiService {
        return retrofit.create(BinanceFuturesApiService::class.java)
    }
}