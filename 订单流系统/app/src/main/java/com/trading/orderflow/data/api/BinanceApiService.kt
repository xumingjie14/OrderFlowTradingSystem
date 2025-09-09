package com.trading.orderflow.data.api

import com.trading.orderflow.data.model.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApiService {
    
    // 获取K线数据
    @GET("/api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 1000,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<List<String>>>
    
    // 获取24小时价格统计
    @GET("/api/v3/ticker/24hr")
    suspend fun get24hrTicker(
        @Query("symbol") symbol: String
    ): Response<BinanceTicker24hr>
    
    // 获取深度信息
    @GET("/api/v3/depth")
    suspend fun getDepth(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 1000
    ): Response<BinanceDepthResponse>
    
    // 获取最新价格
    @GET("/api/v3/ticker/price")
    suspend fun getPrice(
        @Query("symbol") symbol: String
    ): Response<Map<String, String>>
    
    // 获取服务器时间
    @GET("/api/v3/time")
    suspend fun getServerTime(): Response<Map<String, Long>>
}

// 币安合约API服务
interface BinanceFuturesApiService {
    
    // 获取持仓量
    @GET("/fapi/v1/openInterest")
    suspend fun getOpenInterest(
        @Query("symbol") symbol: String
    ): Response<BinanceFuturesOpenInterest>
    
    // 获取资金费率
    @GET("/fapi/v1/fundingRate")
    suspend fun getFundingRate(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 100
    ): Response<List<BinanceFundingRate>>
    
    // 获取合约K线数据
    @GET("/fapi/v1/klines")
    suspend fun getFuturesKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 1000,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<List<String>>>
}