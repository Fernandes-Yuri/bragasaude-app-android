package br.com.bragasaude.data.local.smoking

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** All operations explicitly scope the owner; no selected-patient dependency. */
@Dao
interface SmokingSupportDao {
    @Query("SELECT * FROM smoking_settings_local WHERE userId = :userId")
    fun observeSettings(userId: String): Flow<SmokingSupportSettings?>
    @Query("SELECT * FROM smoking_plan_local WHERE userId = :userId")
    fun observePlan(userId: String): Flow<SmokingSupportPlan?>
    @Query("SELECT * FROM smoking_daily_local WHERE userId = :userId ORDER BY localDate DESC")
    fun observeDays(userId: String): Flow<List<SmokingDailyEntry>>
    @Query("SELECT * FROM smoking_craving_local WHERE userId = :userId ORDER BY occurredAt DESC")
    fun observeCravings(userId: String): Flow<List<SmokingCravingEntry>>
    @Query("SELECT * FROM smoking_settings_local WHERE userId = :userId")
    suspend fun settings(userId: String): SmokingSupportSettings?
    @Query("SELECT * FROM smoking_plan_local WHERE userId = :userId")
    suspend fun plan(userId: String): SmokingSupportPlan?
    @Query("SELECT * FROM smoking_daily_local WHERE userId = :userId AND localDate = :date")
    suspend fun day(userId: String, date: String): SmokingDailyEntry?
    @Upsert suspend fun saveSettings(value: SmokingSupportSettings)
    @Upsert suspend fun savePlan(value: SmokingSupportPlan)
    @Upsert suspend fun saveDay(value: SmokingDailyEntry)
    @Insert suspend fun saveCraving(value: SmokingCravingEntry)
    @Query("DELETE FROM smoking_daily_local WHERE userId = :userId AND localDate = :date")
    suspend fun deleteDay(userId: String, date: String)
    @Query("DELETE FROM smoking_settings_local WHERE userId = :userId")
    suspend fun deleteOwner(userId: String)

    @Transaction
    suspend fun activate(settings: SmokingSupportSettings, plan: SmokingSupportPlan) {
        require(settings.userId == plan.userId && settings.enabled)
        saveSettings(settings)
        savePlan(plan)
    }

    /** Keep only a refusal marker so deletion never reopens the automatic offer. */
    @Transaction
    suspend fun erase(settings: SmokingSupportSettings) {
        deleteOwner(settings.userId)
        saveSettings(settings)
    }
}
