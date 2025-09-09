package com.trading.orderflow.data.database

import androidx.room.*
import com.trading.orderflow.data.model.LicenseInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseDao {
    
    @Query("SELECT * FROM license_info WHERE id = 'default' LIMIT 1")
    suspend fun getLicenseInfo(): LicenseInfo?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicenseInfo(licenseInfo: LicenseInfo)
    
    @Query("DELETE FROM license_info")
    suspend fun deleteLicenseInfo()
    
    @Query("SELECT * FROM license_info WHERE id = 'default'")
    fun getLicenseInfoFlow(): Flow<LicenseInfo?>
    
    @Query("UPDATE license_info SET activationAttempts = :attempts, lastAttemptTime = :time WHERE id = 'default'")
    suspend fun updateActivationAttempts(attempts: Int, time: Long)
}