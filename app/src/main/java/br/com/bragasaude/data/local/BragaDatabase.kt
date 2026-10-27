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
    version = 64, // Remoção durável de reações e seleção canônica do mural
    exportSchema = true
)
@TypeConverters(Converters::class, SmokingSupportConverters::class)
abstract class BragaDatabase : RoomDatabase() {
    abstract fun smokingSupportDao(): SmokingSupportDao
    abstract fun medicationDoseDao(): MedicationDoseDao
    abstract fun orbChatDao(): OrbChatDao
    abstract fun vitalSignDao(): VitalSignDao
    abstract fun profileDao(): ProfileDao
    abstract fun biometryDao(): BiometryDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun dailyMetricsDao(): DailyMetricsDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun socialFeedDao(): SocialFeedDao
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
