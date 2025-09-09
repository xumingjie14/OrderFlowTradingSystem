package com.trading.orderflow.data.api

import com.trading.orderflow.data.model.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DerivativesApiService {
    
    // 获取持仓量
    @GET("/fapi/v1/openInterest")
    suspend fun getOpenInterest(
        @Query("symbol") symbol: String
    ): Response<BinanceOpenInterestResponse>
    
    // 获取持仓量历史
    @GET("/futures/data/openInterestHist")
    suspend fun getOpenInterestHistory(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m", // 5m, 15m, 30m, 1h, 2h, 4h, 6h, 12h, 1d
        @Query("limit") limit: Int = 30,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<BinanceOpenInterestResponse>>
    
    // 获取资金费率
    @GET("/fapi/v1/fundingRate")
    suspend fun getFundingRate(
        @Query("symbol") symbol: String,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int = 100
    ): Response<List<BinanceFundingRateResponse>>
    
    // 获取当前资金费率
    @GET("/fapi/v1/premiumIndex")
    suspend fun getCurrentFundingRate(
        @Query("symbol") symbol: String
    ): Response<BinanceFundingRateResponse>
    
    // 获取强制平仓订单
    @GET("/fapi/v1/forceOrders")
    suspend fun getLiquidationOrders(
        @Query("symbol") symbol: String? = null,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int = 100
    ): Response<List<BinanceLiquidationResponse>>
    
    // 获取大户账户数多空比
    @GET("/futures/data/topLongShortAccountRatio")
    suspend fun getTopTraderAccountRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<BinanceTopTraderPositionResponse>>
    
    // 获取大户持仓量多空比
    @GET("/futures/data/topLongShortPositionRatio")
    suspend fun getTopTraderPositionRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<BinanceTopTraderPositionResponse>>
    
    // 获取多空持仓人数比
    @GET("/futures/data/globalLongShortAccountRatio")
    suspend fun getGlobalLongShortAccountRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<BinanceTopTraderPositionResponse>>
    
    // 获取合约资金流向
    @GET("/futures/data/takerlongshortRatio")
    suspend fun getTakerLongShortRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<List<BinanceTopTraderPositionResponse>>
}