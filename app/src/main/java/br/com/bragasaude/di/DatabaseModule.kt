package br.com.bragasaude.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BragaDatabase {
        // A2: Em release, nenhuma fallback destrutiva é aplicada para proteger dados locais (vitals, exames, medicações).
        // Em DEBUG, permite-se para agilizar o desenvolvimento.
        val builder = Room.databaseBuilder(
            context,
            BragaDatabase::class.java,
            BragaDatabase.DATABASE_NAME
        )

        builder.addMigrations(*Migrations.ALL)

        // Proteção LGPD: sem fallback destrutivo nem em DEBUG nem em RELEASE.
        // Qualquer alteração de schema requer migration explícita para evitar perda de dados de saúde.

        return builder.build()
    }

    @Provides
    fun provideBiometryDao(database: BragaDatabase): BiometryDao {
        return database.biometryDao()
    }

    @Provides
    fun provideVitalSignDao(database: BragaDatabase): VitalSignDao {
        return database.vitalSignDao()
    }

    @Provides
    fun provideProfileDao(database: BragaDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideFoodDao(database: BragaDatabase): FoodDao {
        return database.foodDao()
    }

    @Provides
    fun provideMealRuleDao(database: BragaDatabase): MealRuleDao {
        return database.mealRuleDao()
    }

    @Provides
    fun provideExamDao(database: BragaDatabase): ExamDao {
        return database.examDao()
    }

    @Provides
    fun provideMedicationDao(database: BragaDatabase): MedicationDao {
        return database.medicationDao()
    }

    @Provides
    fun provideMedicationLogDao(database: BragaDatabase): MedicationLogDao {
        return database.medicationLogDao()
    }

    @Provides
    fun provideMilestoneDao(database: BragaDatabase): MilestoneDao {
        return database.milestoneDao()
    }

    @Provides
    fun provideClinicalReferenceDao(database: BragaDatabase): ClinicalReferenceDao {
        return database.clinicalReferenceDao()
    }

    @Provides
    fun provideExamItemDao(database: BragaDatabase): ExamItemDao {
        return database.examItemDao()
    }

    @Provides
    fun provideDailyMetricsDao(database: BragaDatabase): DailyMetricsDao {
        return database.dailyMetricsDao()
    }

    @Provides
    fun provideFeedbackDao(database: BragaDatabase): FeedbackDao {
        return database.feedbackDao()
    }

    @Provides
    fun provideLeagueDao(database: BragaDatabase): LeagueDao {
        return database.leagueDao()
    }

    @Provides
    fun provideSocialFeedDao(database: BragaDatabase): SocialFeedDao {
        return database.socialFeedDao()
    }

    @Provides
    fun provideXpAwardDao(database: BragaDatabase): XpAwardDao {
        return database.xpAwardDao()
    }

    @Provides
    fun provideGroceryListDao(database: BragaDatabase): GroceryListDao {
        return database.groceryListDao()
    }

    @Provides
    fun provideFamilyDao(database: BragaDatabase): FamilyDao {
        return database.familyDao()
    }

    @Provides
    fun provideVitalAlertLogDao(database: BragaDatabase): VitalAlertLogDao {
        return database.vitalAlertLogDao()
    }

    @Provides
    fun provideAuditLogDao(database: BragaDatabase): AuditLogDao {
        return database.auditLogDao()
    }

    @Provides
    @Singleton
    fun providePdfReportGenerator(@ApplicationContext context: Context): br.com.bragasaude.domain.PdfReportGenerator {
        return br.com.bragasaude.domain.PdfReportGenerator(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
