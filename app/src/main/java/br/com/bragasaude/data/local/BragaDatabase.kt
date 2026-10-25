package br.com.bragasaude.data.local

import br.com.bragasaude.data.local.smoking.*
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SmokingSupportSettings::class, SmokingSupportPlan::class, SmokingDailyEntry::class, SmokingCravingEntry::class,
        DoseGroupEntity::class, DoseIntentEntity::class, DoseReservationEntity::class, DoseScheduleEntity::class,
        VitalSignEntity::class,
        ProfileEntity::class,
        FoodEntity::class,
        MealRuleEntity::class,
        MilestoneEntity::class,
        BiometryEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        DailyMetricsEntity::class,
        FeedbackEntity::class,
        // Tabelas legadas mantidas para compatibilidade do schema 59, sem DAOs ativos.
        LeagueCycleEntity::class,
        LeagueMembershipEntity::class,
        SocialPostEntity::class,
        PostReactionEntity::class,
        XpAwardEntity::class,
        FamilyBindingEntity::class,
        FamilyMessageEntity::class,
        GroceryListItemEntity::class,
        GroceryRetailCardEntity::class,
        GroceryPublicationCache::class,
        GroceryAdoptedPack::class,
        WeeklyGrocerySummaryEntity::class,
        GroceryPantryStockEntity::class,
        GroceryMealEntity::class,
        GroceryConsumptionEntity::class,
        VitalAlertLogEntity::class,
        AuditLogEntity::class,
        WearableReading::class,
        OrbConversation::class,
        ConsultationEntity::class,
        // Care OS — D62 (First Contract)
        SymptomsDiaryEntity::class,
        CareAuditEntity::class,
        BleTelemetryReceiptEntity::class
    ],
    version = 62, // Metadados semânticos do catálogo de alimentos
    exportSchema = true
)
@TypeConverters(Converters::class, SmokingSupportConverters::class)
abstract class BragaDatabase : RoomDatabase() {
    abstract fun smokingSupportDao(): SmokingSupportDao
    abstract fun medicationDoseDao(): MedicationDoseDao
    abstract fun orbChatDao(): OrbChatDao
    abstract fun vitalSignDao(): VitalSignDao
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun mealRuleDao(): MealRuleDao
    abstract fun biometryDao(): BiometryDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun dailyMetricsDao(): DailyMetricsDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun socialFeedDao(): SocialFeedDao
    abstract fun groceryPublicationDao(): GroceryPublicationDao
    abstract fun groceryRetailCardDao(): GroceryRetailCardDao
    abstract fun groceryListDao(): GroceryListDao
    abstract fun weeklyGrocerySummaryDao(): WeeklyGrocerySummaryDao
    abstract fun groceryPantryDao(): GroceryPantryDao
    abstract fun familyDao(): FamilyDao
    abstract fun vitalAlertLogDao(): VitalAlertLogDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun wearableReadingDao(): WearableReadingDao

    // Care OS — D62 (First Contract)
    abstract fun symptomsDiaryDao(): SymptomsDiaryDao
    abstract fun careAuditDao(): CareAuditDao
    abstract fun bleTelemetryReceiptDao(): BleTelemetryReceiptDao

    companion object {
        const val DATABASE_NAME = "braga_saude_db"
    }
}
