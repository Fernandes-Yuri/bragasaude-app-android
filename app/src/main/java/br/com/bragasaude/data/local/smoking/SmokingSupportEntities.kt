package br.com.bragasaude.data.local.smoking

import androidx.room.*
import br.com.bragasaude.domain.smoking.*

class SmokingSupportConverters {
    @TypeConverter fun decision(value: String): OfferDecision =
        OfferDecision.entries.firstOrNull { it.name == value } ?: OfferDecision.DECLINED
    @TypeConverter fun decision(value: OfferDecision): String = value.name
    @TypeConverter fun intensity(value: String): CravingIntensity? =
        CravingIntensity.entries.firstOrNull { it.name == value }
    @TypeConverter fun intensity(value: CravingIntensity?): String? = value?.name
    @TypeConverter fun stage(value: String): PreparationStage =
        PreparationStage.entries.firstOrNull { it.name == value } ?: PreparationStage.PREPARING
    @TypeConverter fun stage(value: PreparationStage): String = value.name
}

@Entity(tableName = "smoking_settings_local")
data class SmokingSupportSettings(
    @PrimaryKey val userId: String,
    val offerDecision: OfferDecision = OfferDecision.UNSEEN,
    val enabled: Boolean = false,
    val acceptedVersion: Int? = null,
    val acceptedAt: Long? = null,
    val updatedAt: Long
)

@Entity(tableName = "smoking_plan_local", foreignKeys = [ForeignKey(
    entity = SmokingSupportSettings::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE
)])
data class SmokingSupportPlan(
    @PrimaryKey val userId: String,
    val startedOn: String,
    val quitTargetOn: String? = null,
    val personalReason: String? = null,
    // Comma-separated enum identifiers, never arbitrary text.
    val triggers: String = "",
    val stage: PreparationStage,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "smoking_daily_local", primaryKeys = ["userId", "localDate"], foreignKeys = [ForeignKey(
    entity = SmokingSupportSettings::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE
)])
data class SmokingDailyEntry(
    val userId: String,
    val localDate: String,
    val smoked: Boolean,
    val cigaretteCount: Int?,
    val zoneId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "smoking_craving_local", indices = [Index("userId")], foreignKeys = [ForeignKey(
    entity = SmokingSupportSettings::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE
)])
data class SmokingCravingEntry(
    @PrimaryKey val id: String,
    val userId: String,
    val occurredAt: Long,
    val zoneId: String,
    val intensity: CravingIntensity?
)
