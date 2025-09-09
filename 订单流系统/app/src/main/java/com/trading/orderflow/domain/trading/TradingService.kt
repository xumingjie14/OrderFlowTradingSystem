package com.trading.orderflow.domain.trading

import android.util.Log
import com.trading.orderflow.data.database.TradingDao
import com.trading.orderflow.data.model.*
import com.trading.orderflow.domain.risk.RiskManager
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradingService @Inject constructor(
    private val tradingDao: TradingDao,
    private val riskManager: RiskManager,
    private val orderExecutor: OrderExecutor
) {
    
    companion object {
        private const val TAG = "TradingService"
    }
    
    private val _orderUpdates = MutableSharedFlow<TradingOrder>()
    val orderUpdates: SharedFlow<TradingOrder> = _orderUpdates.asSharedFlow()
    
    private val _positionUpdates = MutableSharedFlow<Position>()
    val positionUpdates: SharedFlow<Position> = _positionUpdates.asSharedFlow()
    
    // 下单
    suspend fun placeOrder(request: PlaceOrderRequest): PlaceOrderResponse {
        try {
            // 1. 风险检查
            val riskCheck = riskManager.checkOrderRisk(request)
            if (!riskCheck.allowed) {
                return PlaceOrderResponse(
                    success = false,
                    message = "风险检查失败: ${riskCheck.reason}"
                )
            }
            
            // 2. 创建订单
            val order = TradingOrder(
                orderId = UUID.randomUUID().toString(),
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                quantity = request.quantity,
                price = request.price,
                stopPrice = request.stopPrice,
                clientOrderId = request.clientOrderId ?: UUID.randomUUID().toString(),
                timeInForce = request.timeInForce
            )
            
            // 3. 保存到数据库
            tradingDao.insertOrder(order)
            
            // 4. 执行订单
            val executionResult = orderExecutor.executeOrder(order)
            
            if (executionResult.success) {
                // 更新订单状态
                val updatedOrder = order.copy(
                    status = OrderStatus.FILLED,
                    filledQuantity = executionResult.filledQuantity ?: order.quantity,
                    averagePrice = executionResult.averagePrice,
                    updatedAt = System.currentTimeMillis()
                )
                
                tradingDao.updateOrder(updatedOrder)
                _orderUpdates.emit(updatedOrder)
                
                // 更新持仓
                updatePosition(updatedOrder, executionResult)
                
                Log.d(TAG, "Order executed successfully: ${order.orderId}")
                
                return PlaceOrderResponse(
                    success = true,
                    orderId = order.orderId,
                    clientOrderId = order.clientOrderId,
                    order = updatedOrder
                )
            } else {
                // 订单执行失败
                val failedOrder = order.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = System.currentTimeMillis()
                )
                
                tradingDao.updateOrder(failedOrder)
                _orderUpdates.emit(failedOrder)
                
                return PlaceOrderResponse(
                    success = false,
                    message = executionResult.message,
                    orderId = order.orderId
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Place order error: ${e.message}")
            return PlaceOrderResponse(
                success = false,
                message = "下单失败: ${e.message}"
            )
        }
    }
    
    // 取消订单
    suspend fun cancelOrder(orderId: String): Boolean {
        return try {
            val order = tradingDao.getOrderById(orderId)
            if (order != null && order.status == OrderStatus.PENDING) {
                val cancelResult = orderExecutor.cancelOrder(orderId)
                
                if (cancelResult) {
                    val cancelledOrder = order.copy(
                        status = OrderStatus.CANCELLED,
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    tradingDao.updateOrder(cancelledOrder)
                    _orderUpdates.emit(cancelledOrder)
                    
                    Log.d(TAG, "Order cancelled: $orderId")
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cancel order error: ${e.message}")
            false
        }
    }
    
    // 获取所有订单
    fun getAllOrders(): Flow<List<TradingOrder>> {
        return tradingDao.getAllOrdersFlow()
    }
    
    // 获取活跃订单
    fun getActiveOrders(): Flow<List<TradingOrder>> {
        return tradingDao.getActiveOrdersFlow()
    }
    
    // 获取所有持仓
    fun getAllPositions(): Flow<List<Position>> {
        return tradingDao.getAllPositionsFlow()
    }
    
    // 获取特定交易对持仓
    suspend fun getPosition(symbol: String): Position? {
        return tradingDao.getPosition(symbol)
    }
    
    // 平仓
    suspend fun closePosition(symbol: String): PlaceOrderResponse {
        val position = getPosition(symbol)
        if (position == null) {
            return PlaceOrderResponse(
                success = false,
                message = "未找到持仓"
            )
        }
        
        // 创建平仓订单
        val closeOrderSide = if (position.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY
        val closeRequest = PlaceOrderRequest(
            symbol = symbol,
            side = closeOrderSide,
            type = OrderType.MARKET,
            quantity = position.quantity
        )
        
        return placeOrder(closeRequest)
    }
    
    // 更新持仓
    private suspend fun updatePosition(order: TradingOrder, executionResult: OrderExecutionResult) {
        val existingPosition = getPosition(order.symbol)
        
        if (existingPosition == null) {
            // 创建新持仓
            val newPosition = Position(
                symbol = order.symbol,
                side = order.side,
                quantity = order.filledQuantity,
                averagePrice = executionResult.averagePrice ?: order.price ?: BigDecimal.ZERO
            )
            
            tradingDao.insertPosition(newPosition)
            _positionUpdates.emit(newPosition)
        } else {
            // 更新现有持仓
            val updatedPosition = calculateNewPosition(existingPosition, order, executionResult)
            tradingDao.updatePosition(updatedPosition)
            _positionUpdates.emit(updatedPosition)
        }
    }
    
    // 计算新持仓
    private fun calculateNewPosition(
        existingPosition: Position,
        order: TradingOrder,
        executionResult: OrderExecutionResult
    ): Position {
        val executionPrice = executionResult.averagePrice ?: order.price ?: BigDecimal.ZERO
        val executionQuantity = order.filledQuantity
        
        return if (existingPosition.side == order.side) {
            // 同方向，增加持仓
            val totalQuantity = existingPosition.quantity + executionQuantity
            val totalValue = (existingPosition.quantity * existingPosition.averagePrice) + 
                           (executionQuantity * executionPrice)
            val newAveragePrice = totalValue / totalQuantity
            
            existingPosition.copy(
                quantity = totalQuantity,
                averagePrice = newAveragePrice,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            // 反方向，减少持仓
            val newQuantity = existingPosition.quantity - executionQuantity
            
            if (newQuantity > BigDecimal.ZERO) {
                // 部分平仓
                existingPosition.copy(
                    quantity = newQuantity,
                    updatedAt = System.currentTimeMillis()
                )
            } else if (newQuantity == BigDecimal.ZERO) {
                // 完全平仓，删除持仓
                existingPosition.copy(
                    quantity = BigDecimal.ZERO,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                // 反向开仓
                val oppositeSide = if (order.side == OrderSide.BUY) OrderSide.BUY else OrderSide.SELL
                existingPosition.copy(
                    side = oppositeSide,
                    quantity = newQuantity.abs(),
                    averagePrice = executionPrice,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }
    
    // 根据信号自动交易
    suspend fun executeSignalOrder(signal: TradingSignal): PlaceOrderResponse {
        if (!signal.isValid || signal.strength < 0.6) {
            return PlaceOrderResponse(
                success = false,
                message = "信号强度不足或无效"
            )
        }
        
        val orderSide = if (signal.direction > 0) OrderSide.BUY else OrderSide.SELL
        val quantity = calculateOrderQuantity(signal)
        
        val request = PlaceOrderRequest(
            symbol = signal.symbol,
            side = orderSide,
            type = OrderType.MARKET,
            quantity = quantity
        )
        
        val response = placeOrder(request)
        
        // 记录信号交易关联
        if (response.success && response.order != null) {
            val updatedOrder = response.order.copy(
                isFromSignal = true,
                signalId = signal.id
            )
            tradingDao.updateOrder(updatedOrder)
        }
        
        return response
    }
    
    // 计算订单数量
    private suspend fun calculateOrderQuantity(signal: TradingSignal): BigDecimal {
        // 根据信号强度和风控规则计算订单数量
        val baseQuantity = BigDecimal("0.01") // 基础数量
        val strengthMultiplier = BigDecimal(signal.strength.toString())
        
        return baseQuantity * strengthMultiplier
    }
}

// 订单执行结果
data class OrderExecutionResult(
    val success: Boolean,
    val message: String? = null,
    val filledQuantity: BigDecimal? = null,
    val averagePrice: BigDecimal? = null,
    val commission: BigDecimal? = null
)