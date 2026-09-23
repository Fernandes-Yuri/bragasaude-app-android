package br.com.bragasaude.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VitalSignEntity::class,
        ProfileEntity::class,
        ExamEntity::class,
        FoodEntity::class,
        MealRuleEntity::class,
        MilestoneEntity::class,
        BiometryEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        ClinicalReferenceEntity::class,
        ExamItemEntity::class,
        DailyMetricsEntity::class,
        FeedbackEntity::class,
        LeagueCycleEntity::class,
        LeagueMembershipEntity::class,
        SocialPostEntity::class,
        PostReactionEntity::class,
        XpAwardEntity::class,
        FamilyBindingEntity::class,
        FamilyMessageEntity::class,
        GroceryListItemEntity::class,
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
    version = 48, // D62: Care OS — colunas de estoque, idempotência e mural/telemetria
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BragaDatabase : RoomDatabase() {
    abstract fun orbChatDao(): OrbChatDao
    abstract fun vitalSignDao(): VitalSignDao
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun mealRuleDao(): MealRuleDao
    abstract fun biometryDao(): BiometryDao
    abstract fun examDao(): ExamDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun clinicalReferenceDao(): ClinicalReferenceDao
    abstract fun examItemDao(): ExamItemDao
    abstract fun dailyMetricsDao(): DailyMetricsDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun leagueDao(): LeagueDao
    abstract fun socialFeedDao(): SocialFeedDao
    abstract fun xpAwardDao(): XpAwardDao
    abstract fun groceryListDao(): GroceryListDao
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
