package com.trading.orderflow.data.database

import androidx.room.TypeConverter
import com.trading.orderflow.data.model.*

class Converters {
    
    @TypeConverter
    fun fromTradeSide(side: TradeSide): String {
        return side.name
    }
    
    @TypeConverter
    fun toTradeSide(side: String): TradeSide {
        return TradeSide.valueOf(side)
    }
    
    @TypeConverter
    fun fromTradeStatus(status: TradeStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toTradeStatus(status: String): TradeStatus {
        return TradeStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromRiskEventType(type: RiskEventType): String {
        return type.name
    }
    
    @TypeConverter
    fun toRiskEventType(type: String): RiskEventType {
        return RiskEventType.valueOf(type)
    }
    
    @TypeConverter
    fun fromRiskSeverity(severity: RiskSeverity): String {
        return severity.name
    }
    
    @TypeConverter
    fun toRiskSeverity(severity: String): RiskSeverity {
        return RiskSeverity.valueOf(severity)
    }
}