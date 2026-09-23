package br.com.bragasaude.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalSignDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vitalSign: VitalSignEntity): Long

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId ORDER BY measuredAt DESC")
    fun getAll(userId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId ORDER BY measuredAt DESC")
    suspend fun getAllOneShot(userId: String): List<VitalSignEntity>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND (systolicPressure IS NOT NULL OR diastolicPressure IS NOT NULL) ORDER BY measuredAt DESC")
    fun getBloodPressureRecords(userId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND (systolicPressure IS NOT NULL OR diastolicPressure IS NOT NULL) AND measuredAt >= :sinceMillis ORDER BY measuredAt DESC")
    fun getBloodPressureRecent30Days(userId: String, sinceMillis: Long): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND glucoseLevel IS NOT NULL ORDER BY measuredAt DESC")
    fun getGlucoseRecords(userId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND glucoseLevel IS NOT NULL AND measuredAt >= :sinceMillis ORDER BY measuredAt DESC")
    fun getGlucoseRecent30Days(userId: String, sinceMillis: Long): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND hydrationMl IS NOT NULL AND hydrationMl > 0 ORDER BY measuredAt DESC")
    fun getHydrationRecords(userId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND hydrationMl IS NOT NULL AND hydrationMl > 0 AND measuredAt >= :sinceMillis ORDER BY measuredAt DESC")
    fun getHydrationRecent30Days(userId: String, sinceMillis: Long): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND measuredAt >= :sinceMillis ORDER BY measuredAt DESC")
    fun getRecent30Days(userId: String, sinceMillis: Long): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<VitalSignEntity>

    @Query("SELECT * FROM vital_signs_local WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): VitalSignEntity?

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId AND remoteId IS NULL AND hydrationMl = :hydrationMl AND abs(measuredAt - :measuredAtMillis) < 10000 ORDER BY measuredAt DESC LIMIT 1")
    suspend fun findMatchingLocalHydration(userId: String, measuredAtMillis: Long, hydrationMl: Int): VitalSignEntity?

    // AUD-AN30: a chave era só measuredAt + hydrationMl. A voz que registra
    // "pressão 12x8 e glicemia 100" cria DUAS entidades no mesmo milissegundo
    // com hydrationMl null → mesma chave → uma era apagada (perda silenciosa
    // de um sinal válido). Agora a chave inclui todos os valores clínicos: só
    // duplicatas verdadeiras (mesmo sinal, mesmo valor, mesmo instante) colapsam.
    @Query("""DELETE FROM vital_signs_local WHERE localId NOT IN (
        SELECT MIN(localId) FROM vital_signs_local
        GROUP BY userId,
            COALESCE(remoteId, measuredAt || '_' ||
                COALESCE(systolicPressure, 0) || '_' ||
                COALESCE(diastolicPressure, 0) || '_' ||
                COALESCE(heartRate, 0) || '_' ||
                COALESCE(oxygenSaturation, 0) || '_' ||
                COALESCE(glucoseLevel, 0) || '_' ||
                COALESCE(glucoseType, '') || '_' ||
                COALESCE(hydrationMl, 0) || '_' ||
                COALESCE(steps, 0) || '_' ||
                COALESCE(distanceMeters, 0)
            ))""")
    suspend fun deduplicateVitals()

    @Query("DELETE FROM vital_signs_local WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Query("DELETE FROM vital_signs_local WHERE localId = (SELECT localId FROM vital_signs_local WHERE userId = :userId AND hydrationMl > 0 ORDER BY measuredAt DESC LIMIT 1)")
    suspend fun deleteLastHydration(userId: String)

    @Query("DELETE FROM vital_signs_local WHERE measuredAt < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM vital_signs_local WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)

    @Query("SELECT * FROM vital_signs_local WHERE userId = :userId ORDER BY measuredAt DESC LIMIT :count")
    suspend fun getLastN(userId: String, count: Int): List<VitalSignEntity>
}

@Dao
interface ProfileDao {
    @Transaction
    suspend fun cacheRemoteProfile(remote: ProfileEntity, expected: ProfileEntity?): ProfileEntity? {
        val current = getProfileOneShot(remote.userId)
        if (current != expected || current?.pendingSync == true) {
            // Ha sync pendente: nao sobrescreve o perfil local. Mas o segredo
            // TOTP do vinculo de WhatsApp tem que ser o do backend (a fonte da
            // verdade) — se os dois divergirem, o codigo nunca casa no webhook.
            // Mescla so este campo e devolve o estado local.
            return current?.let {
                if (!remote.whatsappTotpSecret.isNullOrBlank() &&
                    it.whatsappTotpSecret != remote.whatsappTotpSecret
                ) {
                    val merged = it.copy(whatsappTotpSecret = remote.whatsappTotpSecret)
                    insert(merged)
                    merged
                } else {
                    it
                }
            }
        }
        val merged = remote.copy(customPhotoUri = current?.customPhotoUri)
        insert(merged)
        return merged
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles_local WHERE userId = :userId")
    fun getProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles_local WHERE userId = :userId LIMIT 1")
    suspend fun getProfileOneShot(userId: String): ProfileEntity?

    @Query("SELECT * FROM profiles_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ProfileEntity>

    @Query("UPDATE profiles_local SET avatarIdentifier = :avatarId, customPhotoUri = :photoUri WHERE userId = :userId")
    suspend fun updateAvatar(userId: String, avatarId: String?, photoUri: String?)

    @Query("DELETE FROM profiles_local WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)
}

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity)

    @Query("SELECT * FROM food_catalog_local")
    fun getCatalog(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food_catalog_local WHERE name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<FoodEntity>>
}

@Dao
interface MealRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MealRuleEntity>)

    @Query("SELECT * FROM meal_rules_local")
    fun getRules(): Flow<List<MealRuleEntity>>
}

@Dao
interface BiometryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(biometry: BiometryEntity)

    @Query("SELECT * FROM biometry_local WHERE userId = :userId ORDER BY measuredAt DESC")
    fun getAll(userId: String): Flow<List<BiometryEntity>>

    @Query("SELECT * FROM biometry_local WHERE userId = :userId AND measuredAt >= :sinceMillis ORDER BY measuredAt DESC LIMIT 30")
    fun getRecent30Days(userId: String, sinceMillis: Long): Flow<List<BiometryEntity>>

    @Query("SELECT * FROM biometry_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<BiometryEntity>

    @Query("DELETE FROM biometry_local WHERE measuredAt < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long)
}

@Dao
interface ExamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity): Long

    @Query("SELECT * FROM exams_local WHERE userId = :userId ORDER BY examDate DESC")
    fun getAll(userId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams_local WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ExamEntity?

    @Query("SELECT * FROM exams_local WHERE userId = :userId AND remoteId IS NULL AND title = :title ORDER BY examDate DESC LIMIT 1")
    suspend fun findMatchingLocalExam(userId: String, title: String): ExamEntity?

    @Query("DELETE FROM exams_local WHERE localId NOT IN (SELECT MIN(localId) FROM exams_local GROUP BY userId, COALESCE(remoteId, title || '_' || examDate))")
    suspend fun deduplicateExams()

    @Query("SELECT * FROM exams_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ExamEntity>

    /** Busca o exame com maior localId para um remoteId */
    @Query("SELECT * FROM exams_local WHERE remoteId = :examId ORDER BY localId DESC LIMIT 1")
    suspend fun getLatestByExamId(examId: String): ExamEntity?

    @Query("DELETE FROM exams_local WHERE remoteId = :examId")
    suspend fun deleteByRemoteId(examId: String)
}

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Query("SELECT * FROM medications_local WHERE userId = :userId")
    fun getAll(userId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications_local WHERE userId = :userId")
    suspend fun getAllSync(userId: String): List<MedicationEntity>

    @Query("SELECT * FROM medications_local WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MedicationEntity?

    @Query("SELECT * FROM medications_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<MedicationEntity>

    @Query("DELETE FROM medications_local WHERE id = :id")
    suspend fun deleteById(id: String)

    // ---- Care OS (D62): gestão de estoque offline-first ----

    /**
     * Decrementa o estoque local ao tomar a dose. Só decrementa se houver
     * unidades suficientes (nunca negativa — constraint do servidor).
     * Retorna o número de linhas afetadas (0 = estoque insuficiente).
     */
    @Query(
        "UPDATE medications_local SET currentUnits = currentUnits - :units, pendingSync = 1 " +
            "WHERE id = :id AND currentUnits >= :units"
    )
    suspend fun decrementUnits(id: String, units: Int): Int

    /** Reabastece a caixa e carimba a data do último reposicionamento. */
    @Query(
        "UPDATE medications_local SET currentUnits = :units, totalUnits = :units, " +
            "lastRestockDate = :restockAt, pendingSync = 1 WHERE id = :id"
    )
    suspend fun restock(id: String, units: Int, restockAt: Long)

    /** Aplica o saldo autoritativo devolvido pelo Care OS sem criar novo push legado. */
    @Query("UPDATE medications_local SET currentUnits = :units, pendingSync = 0 WHERE id = :id")
    suspend fun applyAuthoritativeStock(id: String, units: Int)

    /** Medicamentos cadastrados sem a confirmação da receita (RDC 657/2022). */
    @Query("SELECT * FROM medications_local WHERE userId = :userId AND confirmedWithPrescription = 0")
    suspend fun getUnconfirmedWithPrescription(userId: String): List<MedicationEntity>
}

@Dao
interface MedicationLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(log: MedicationLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MedicationLogEntity)

    @Query("SELECT * FROM medication_logs_local WHERE userId = :userId ORDER BY takenAt DESC")
    fun getAllLogs(userId: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs_local WHERE userId = :userId AND medicationId = :medId ORDER BY takenAt DESC")
    fun getLogsForMedication(userId: String, medId: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<MedicationLogEntity>

    // AUD-AN27: takenAt é epoch-MILLIS. SQLite trata número como DIA JULIANO,
    // então DATE(1690000000000) → NULL (verificado empiricamente) e a
    // comparação DATE(x)=DATE(y) era NULL=NULL (sempre falso), fazendo
    // countTodayTaken devolver 0 e getDistinctTakeDays colapsar tudo em 1
    // grupo. A conversão correta é millis/1000 → 'unixepoch'.
    @Query("SELECT COUNT(*) FROM medication_logs_local WHERE userId = :userId AND date(takenAt/1000, 'unixepoch', 'localtime') = date(:currentTimestamp/1000, 'unixepoch', 'localtime')")
    suspend fun countTodayTaken(userId: String, currentTimestamp: Long): Int

    @Query("SELECT * FROM medication_logs_local WHERE userId = :userId ORDER BY takenAt DESC LIMIT :count")
    suspend fun getLastNMedications(userId: String, count: Int): List<MedicationLogEntity>

    @Query("SELECT date(takenAt/1000, 'unixepoch', 'localtime') AS d FROM medication_logs_local WHERE userId = :userId GROUP BY d ORDER BY d DESC")
    fun getDistinctTakeDays(userId: String): Flow<List<String>>

    // ---- Care OS (D62): idempotência de doses ----

    /** Busca um log pela chave idempotente (userId + chave). */
    @Query("SELECT * FROM medication_logs_local WHERE userId = :userId AND idempotencyKey = :key LIMIT 1")
    suspend fun findByIdempotencyKey(userId: String, key: String): MedicationLogEntity?

    /** Conta logs com a mesma chave idempotente (0 = ainda não registrada). */
    @Query("SELECT COUNT(*) FROM medication_logs_local WHERE userId = :userId AND idempotencyKey = :key")
    suspend fun countByIdempotencyKey(userId: String, key: String): Int

    @Query("UPDATE medication_logs_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)
}

@Dao
interface MilestoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: MilestoneEntity)

    @Query("SELECT * FROM milestones_local WHERE userId = :userId ORDER BY achievedAt DESC")
    fun getAll(userId: String): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<MilestoneEntity>
}

@Dao
interface ClinicalReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(references: List<ClinicalReferenceEntity>)

    @Query("SELECT COUNT(*) FROM clinical_references_local")
    suspend fun count(): Int

    @Query("SELECT * FROM clinical_references_local")
    fun getAll(): Flow<List<ClinicalReferenceEntity>>

    @Query("SELECT * FROM clinical_references_local WHERE itemKey = :key")
    suspend fun getByKey(key: String): ClinicalReferenceEntity?
}

@Dao
interface ExamItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExamItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExamItemEntity>)

    @Query("SELECT * FROM exam_items_local WHERE userId = :userId ORDER BY measuredAt DESC")
    fun getAll(userId: String): Flow<List<ExamItemEntity>>

    @Query("SELECT * FROM exam_items_local WHERE examId = :examId")
    fun getByExam(examId: String): Flow<List<ExamItemEntity>>

    @Query("SELECT * FROM exam_items_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ExamItemEntity>

    /** Busca item por remoteId (UUID no Firebase Data Connect) */
    @Query("SELECT * FROM exam_items_local WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ExamItemEntity?

    /** Busca item por localId quando ainda não tem remoteId */
    @Query("SELECT * FROM exam_items_local WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): ExamItemEntity?

    /** Busca todos os itens de um exame especificado (por examId local ou remoto) */
    @Query("SELECT * FROM exam_items_local WHERE examId = :examId")
    suspend fun getByExamLocal(examId: String): List<ExamItemEntity>

    @Query("DELETE FROM exam_items_local WHERE examId = :examId")
    suspend fun deleteByExamId(examId: String)
}

@Dao
interface DailyMetricsDao {
    @androidx.room.Transaction
    suspend fun mergeDeviceSteps(userId: String, date: String, steps: Int) {
        val current = getByDate(userId, date) ?: DailyMetricsEntity(userId = userId, date = date)
        insert(current.copy(steps = maxOf(current.steps, steps), pendingSync = true))
    }

    @androidx.room.Transaction
    suspend fun mergePhoneMetrics(metrics: DailyMetricsEntity) {
        val current = getByDate(metrics.userId, metrics.date)
        insert(metrics.copy(steps = maxOf(metrics.steps, current?.steps ?: 0)))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: DailyMetricsEntity)
    
    @Query("SELECT * FROM daily_metrics_local WHERE userId = :userId AND date = :date")
    suspend fun getByDate(userId: String, date: String): DailyMetricsEntity?
    
    @Query("SELECT * FROM daily_metrics_local WHERE userId = :userId ORDER BY date DESC LIMIT 30")
    fun getRecent30Days(userId: String): Flow<List<DailyMetricsEntity>>

    @Query("SELECT * FROM daily_metrics_local WHERE userId = :userId AND date >= :startDate ORDER BY date ASC")
    fun getMetricsForDateRange(userId: String, startDate: String): Flow<List<DailyMetricsEntity>>
    
    @Query("SELECT * FROM daily_metrics_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<DailyMetricsEntity>

    @Query("DELETE FROM daily_metrics_local WHERE date < :cutoffDate")
    suspend fun purgeOlderThan(cutoffDate: String)

    @Query("DELETE FROM daily_metrics_local WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)

    @Query("SELECT * FROM daily_metrics_local WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getDailyMetricsByDateRange(userId: String, startDate: String, endDate: String): List<DailyMetricsEntity>
}

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedbacks_local WHERE userId = :userId ORDER BY createdAt DESC")
    fun getFeedbacksByUser(userId: String): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM feedbacks_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<FeedbackEntity>

    @Query("UPDATE feedbacks_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("UPDATE feedbacks_local SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface LeagueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: LeagueCycleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: LeagueMembershipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberships(memberships: List<LeagueMembershipEntity>)

    @Query("SELECT * FROM league_cycles_local WHERE level = :level AND status = 'active' LIMIT 1")
    fun getActiveCycle(level: Int): Flow<LeagueCycleEntity?>

    @Query("SELECT * FROM league_cycles_local WHERE level = :level AND status = 'active' LIMIT 1")
    suspend fun getActiveCycleOneShot(level: Int): LeagueCycleEntity?

    @Query("SELECT * FROM league_cycles_local WHERE id = :cycleId LIMIT 1")
    suspend fun getCycleOneShot(cycleId: String): LeagueCycleEntity?

    @Query("SELECT * FROM league_memberships_local WHERE userId = :userId AND leagueCycleId = :cycleId LIMIT 1")
    fun getMyMembership(userId: String, cycleId: String): Flow<LeagueMembershipEntity?>

    @Query("SELECT * FROM league_memberships_local WHERE userId = :userId AND leagueCycleId = :cycleId LIMIT 1")
    suspend fun getMyMembershipOneShot(userId: String, cycleId: String): LeagueMembershipEntity?

    @Query("SELECT * FROM league_memberships_local WHERE leagueCycleId = :cycleId ORDER BY xpEarned DESC")
    fun getRanking(cycleId: String): Flow<List<LeagueMembershipEntity>>

    @Query("SELECT * FROM league_memberships_local WHERE leagueCycleId = :cycleId ORDER BY xpEarned DESC")
    suspend fun getRankingOneShot(cycleId: String): List<LeagueMembershipEntity>

    @Query("SELECT * FROM league_memberships_local WHERE pendingSync = 1")
    suspend fun getPendingSyncMemberships(): List<LeagueMembershipEntity>

    @Query("DELETE FROM league_memberships_local WHERE leagueCycleId = :cycleId")
    suspend fun deleteByCycle(cycleId: String)

    @Query("DELETE FROM league_memberships_local WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}

@Dao
interface XpAwardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(award: XpAwardEntity)

    /** Ações já concedidas hoje para um usuário (anti-farming). */
    @Query("SELECT actionType FROM xp_awards_local WHERE userId = :userId AND date = :date")
    suspend fun getAwardedActionTypes(userId: String, date: String): List<String>

    @Query("SELECT * FROM xp_awards_local WHERE userId = :userId AND date = :date ORDER BY awardedAt DESC")
    fun getAwardsForDay(userId: String, date: String): Flow<List<XpAwardEntity>>

    /** XP total concedido em um intervalo (usado no encerramento de ciclo da liga). */
    @Query("SELECT COALESCE(SUM(xp), 0) FROM xp_awards_local WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun sumXpBetween(userId: String, startDate: String, endDate: String): Int

    @Query("DELETE FROM xp_awards_local WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}

@Dao
interface SocialFeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: SocialPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<SocialPostEntity>)

    @Query("SELECT * FROM social_posts_local WHERE isVisible = 1 ORDER BY createdAt DESC")
    fun getGlobalFeed(): Flow<List<SocialPostEntity>>

    @Query("SELECT * FROM social_posts_local WHERE isVisible = 1 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getFeedPage(limit: Int, offset: Int): List<SocialPostEntity>

    @Query("SELECT * FROM social_posts_local WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): SocialPostEntity?

    @Query("SELECT * FROM social_posts_local WHERE pendingSync = 1 ORDER BY createdAt ASC")
    suspend fun getPendingPosts(): List<SocialPostEntity>

    @Query("DELETE FROM social_posts_local WHERE id = :postId")
    suspend fun deletePostById(postId: String)

    @Query("UPDATE social_posts_local SET pendingSync = 0 WHERE id = :postId")
    suspend fun markPostSynced(postId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: PostReactionEntity)

    @Query("DELETE FROM post_reactions_local WHERE postId = :postId AND userId = :userId")
    suspend fun deleteReaction(postId: String, userId: String)

    @Query("SELECT * FROM post_reactions_local WHERE postId = :postId ORDER BY createdAt DESC")
    fun getReactionsForPost(postId: String): Flow<List<PostReactionEntity>>

    @Query("SELECT * FROM post_reactions_local WHERE pendingSync = 1")
    suspend fun getPendingReactions(): List<PostReactionEntity>

    @Query("UPDATE post_reactions_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markReactionSynced(id: String)

    @Query("UPDATE social_posts_local SET reactionCount = :count, hasUserReacted = :hasReacted WHERE id = :postId")
    suspend fun updatePostReactionState(postId: String, count: Int, hasReacted: Boolean)

    @Query("DELETE FROM social_posts_local WHERE userId = :userId")
    suspend fun deletePostsByUser(userId: String)

    @Query("DELETE FROM post_reactions_local WHERE userId = :userId")
    suspend fun deleteReactionsByUser(userId: String)
}


/**
 * Ponte Familiar & Modo Cuidador.
 * Vinculos entre pacientes e cuidadores, codigos de conexao e mensagens familiares.
 */
@Dao
interface FamilyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinding(binding: FamilyBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: FamilyMessageEntity)

    /** Buscar vinculo pendente pelo codigo de conexao. */
    @Query("SELECT * FROM family_bindings_local WHERE connectionCode = :code AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingBindingByCode(code: String): FamilyBindingEntity?

    /** Buscar o vinculo pendente mais recente e nao expirado do paciente. */
    @Query("SELECT * FROM family_bindings_local WHERE patientUserId = :patientUserId AND status = 'PENDING' AND expiresAt > :now ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestPendingBindingForPatient(patientUserId: String, now: Long = System.currentTimeMillis()): FamilyBindingEntity?

    /** Todos os vinculos pendentes nao expirados do paciente. */
    @Query("SELECT * FROM family_bindings_local WHERE patientUserId = :patientUserId AND status = 'PENDING' AND expiresAt > :now")
    suspend fun getPendingBindingsForPatient(patientUserId: String, now: Long = System.currentTimeMillis()): List<FamilyBindingEntity>

    /** Revogar todos os vinculos pendentes do paciente. */
    @Query("UPDATE family_bindings_local SET status = 'REVOKED' WHERE patientUserId = :patientUserId AND status = 'PENDING'")
    suspend fun revokePendingBindingsForPatient(patientUserId: String)

    /** Buscar um vinculo pelo id local. */
    @Query("SELECT * FROM family_bindings_local WHERE id = :bindingId LIMIT 1")
    suspend fun getBindingById(bindingId: String): FamilyBindingEntity?

    /** Vinculos ativos em que o usuario e o paciente. */
    @Query("SELECT * FROM family_bindings_local WHERE patientUserId = :patientUserId AND status = 'ACTIVE'")
    fun getActiveBindingsForPatient(patientUserId: String): Flow<List<FamilyBindingEntity>>

    /** Vinculos ativos em que o usuario e o cuidador. */
    @Query("SELECT * FROM family_bindings_local WHERE caregiverUserId = :caregiverUserId AND status = 'ACTIVE'")
    fun getActiveBindingsForCaregiver(caregiverUserId: String): Flow<List<FamilyBindingEntity>>

    /** Verifica se ja existe vinculo ativo entre este paciente e este cuidador. */
    @Query("SELECT * FROM family_bindings_local WHERE patientUserId = :patientUserId AND caregiverUserId = :caregiverUserId AND status = 'ACTIVE' LIMIT 1")
    suspend fun existsActiveLink(patientUserId: String, caregiverUserId: String): FamilyBindingEntity?

    /** Mensagens nao lidas do paciente (D47: apenas vigentes e nao apagadas). */
    @Query("SELECT * FROM family_messages_local WHERE patientUserId = :patientUserId AND isRead = 0 AND deletedAt IS NULL AND expiresAt > :now ORDER BY sentAt DESC")
    fun getUnreadMessagesForPatient(patientUserId: String, now: Long = System.currentTimeMillis()): Flow<List<FamilyMessageEntity>>

    /** Mensagens recentes do paciente (D47: apenas vigentes e nao apagadas). */
    @Query("SELECT * FROM family_messages_local WHERE patientUserId = :patientUserId AND deletedAt IS NULL AND expiresAt > :now ORDER BY sentAt DESC LIMIT :limit")
    fun getRecentMessagesForPatient(patientUserId: String, limit: Int, now: Long = System.currentTimeMillis()): Flow<List<FamilyMessageEntity>>

    /** Ativar um vinculo pendente. */
    @Query("UPDATE family_bindings_local SET status = 'ACTIVE' WHERE id = :bindingId")
    suspend fun activateBinding(bindingId: String)

    /** Aceitar vinculo preenchendo os dados do cuidador. */
    @Query("UPDATE family_bindings_local SET caregiverUserId = :caregiverUserId, caregiverName = :caregiverName, caregiverRelation = :caregiverRelation, status = 'ACTIVE' WHERE id = :bindingId")
    suspend fun acceptBindingWithCaregiver(
        bindingId: String,
        caregiverUserId: String,
        caregiverName: String,
        caregiverRelation: String
    )

    /** Revogar um vinculo. */
    @Query("UPDATE family_bindings_local SET status = 'REVOKED' WHERE id = :bindingId")
    suspend fun revokeBinding(bindingId: String)

    /** Excluir um vinculo. */
    @Query("DELETE FROM family_bindings_local WHERE id = :bindingId")
    suspend fun deleteBinding(bindingId: String)

    /** Excluir vinculos pendentes pelo codigo de conexao para evitar linhas fantasmas duplicadas. */
    @Query("DELETE FROM family_bindings_local WHERE connectionCode = :code AND status = 'PENDING'")
    suspend fun deletePendingBindingsByCode(code: String)

    /** Buscar todos os codigos gerados pelo paciente (pendentes e ativos, ignorando revogados). */
    @Query("SELECT * FROM family_bindings_local WHERE patientUserId = :patientUserId AND status != 'REVOKED' ORDER BY createdAt DESC")
    fun getGeneratedCodesForPatient(patientUserId: String): Flow<List<FamilyBindingEntity>>

    /** Verificar se codigo de conexao esta ativo ou pendente (codigos revogados ficam livres para reuso). */
    @Query("SELECT * FROM family_bindings_local WHERE connectionCode = :code AND (status = 'PENDING' OR status = 'ACTIVE') LIMIT 1")
    suspend fun getActiveOrPendingBindingByCode(code: String): FamilyBindingEntity?

    /** Marcar codigo pendente como expirado. */
    @Query("UPDATE family_bindings_local SET status = 'EXPIRED' WHERE status = 'PENDING' AND expiresAt > 0 AND expiresAt < :now")
    suspend fun expireOldPendingBindings(now: Long = System.currentTimeMillis())

    /** Marcar mensagem como lida. */
    @Query("UPDATE family_messages_local SET isRead = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: String)

    /** Marcar todas as mensagens do paciente como lidas. */
    @Query("UPDATE family_messages_local SET isRead = 1 WHERE patientUserId = :patientUserId")
    suspend fun markAllMessagesAsRead(patientUserId: String)

    /** Excluir mensagem. */
    @Query("DELETE FROM family_messages_local WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    /** Excluir todas as mensagens de um paciente (purga pós-revogação LGPD). */
    @Query("DELETE FROM family_messages_local WHERE patientUserId = :patientUserId")
    suspend fun deleteMessagesForPatient(patientUserId: String)

    /** Inserir lista de mensagens vindas da sincronizacao com a nuvem. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<FamilyMessageEntity>)

    /** Buscar mensagem por remoteId para desduplicar sync. */
    @Query("SELECT * FROM family_messages_local WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getMessageByRemoteId(remoteId: String): FamilyMessageEntity?

    /** Mensagens pendentes de envio para a nuvem (exclui as ja apagadas localmente). */
    @Query("SELECT * FROM family_messages_local WHERE pendingSync = 1 AND deletedAt IS NULL AND expiresAt > :now")
    suspend fun getPendingSyncMessages(now: Long = System.currentTimeMillis()): List<FamilyMessageEntity>

    /** Marcar mensagem sincronizada com a nuvem. */
    @Query("UPDATE family_messages_local SET remoteId = :remoteId, pendingSync = 0 WHERE id = :id AND deletedAt IS NULL")
    suspend fun markMessageSynced(id: String, remoteId: String)

    /** Buscar mensagem pelo id local (D47: necessaria para decidir hard vs soft delete). */
    @Query("SELECT * FROM family_messages_local WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): FamilyMessageEntity?

    /** Exclusao pelo usuario (D47): tombstone imediato; propagacao ao servidor pelo SyncWorker. */
    @Query("UPDATE family_messages_local SET deletedAt = :deletedAt, pendingSync = 1, deletionAttempts = deletionAttempts + 1, messageText = '', senderName = '', iconType = 'LOVE' WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: String, deletedAt: Long = System.currentTimeMillis())

    /** Mensagens apagadas localmente que ja existem no servidor e precisam propagar a exclusao (D47). */
    @Query("SELECT * FROM family_messages_local WHERE deletedAt IS NOT NULL AND pendingSync = 1")
    suspend fun getPendingDeletionMessages(): List<FamilyMessageEntity>

    @Query("UPDATE family_messages_local SET pendingSync = 0 WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun markDeletionSynced(id: String)

    /**
     * AUD-AN23: desiste de um tombstone após MAX_DELETION_ATTEMPTS falhas.
     * Sem isto o SyncWorker re-tentava a mesma exclusão para SEMPRE (a cada
     * ciclo de WorkManager), pois a IOException final sempre re-agendava o
     * worker. A mensagem expira sozinha aos 24h (purgeExpiredMessages).
     */
    @Query("UPDATE family_messages_local SET pendingSync = 0 WHERE id = :id AND deletedAt IS NOT NULL AND deletionAttempts >= :maxAttempts")
    suspend fun abandonDeletion(id: String, maxAttempts: Int)

    /** Purga local D47: remove definitivamente tudo o que completou 24h (com ou sem tombstone). */
    @Query("DELETE FROM family_messages_local WHERE expiresAt > 0 AND expiresAt <= :now")
    suspend fun purgeExpiredMessages(now: Long = System.currentTimeMillis())

    /** Vínculos familiares pendentes de sincronização com a nuvem. */
    @Query("SELECT * FROM family_bindings_local WHERE pendingSync = 1")
    suspend fun getPendingSyncBindings(): List<FamilyBindingEntity>

    /** Marcar vínculo familiar sincronizado com a nuvem. */
    @Query("UPDATE family_bindings_local SET remoteId = :remoteId, pendingSync = 0 WHERE id = :id")
    suspend fun markBindingSynced(id: String, remoteId: String)

    /** --- CONSULTAS MÉDICAS --- */

    /** Inserir nova consulta médica. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity)

    /** Buscar consulta por ID. */
    @Query("SELECT * FROM consultations_local WHERE id = :consultationId LIMIT 1")
    suspend fun getConsultationById(consultationId: String): ConsultationEntity?

    /** Buscar todas as consultas do usuário. */
    @Query("SELECT * FROM consultations_local WHERE userId = :userId ORDER BY scheduledDate DESC")
    fun getConsultationsForUser(userId: String): Flow<List<ConsultationEntity>>

    /** Buscar consultas pendentes (sugestão do cuidador não aceita/rejeitada). */
    @Query("SELECT * FROM consultations_local WHERE userId = :userId AND status = 'SUGGESTED' ORDER BY scheduledDate DESC")
    fun getSuggestedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>>

    /** Buscar consultas aceitas. */
    @Query("SELECT * FROM consultations_local WHERE userId = :userId AND status = 'ACCEPTED' ORDER BY scheduledDate DESC")
    fun getAcceptedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>>

    /** Buscar consultas rejeitadas. */
    @Query("SELECT * FROM consultations_local WHERE userId = :userId AND status = 'REJECTED' ORDER BY scheduledDate DESC")
    fun getRejectedConsultationsForUser(userId: String): Flow<List<ConsultationEntity>>

    /** Atualizar status da consulta. */
    @Query("UPDATE consultations_local SET status = :status, updatedAt = :updatedAt WHERE id = :consultationId")
    suspend fun updateConsultationStatus(consultationId: String, status: String, updatedAt: Long)

    /** Atualizar ID do evento Google Calendar. */
    @Query("UPDATE consultations_local SET googleCalendarEventId = :googleCalendarEventId WHERE id = :consultationId")
    suspend fun updateGoogleCalendarEventId(consultationId: String, googleCalendarEventId: String)

    /** Excluir consulta. */
    @Query("DELETE FROM consultations_local WHERE id = :consultationId")
    suspend fun deleteConsultation(consultationId: String)

    /** Excluir todas as consultas de um usuário. */
    @Query("DELETE FROM consultations_local WHERE userId = :userId")
    suspend fun deleteAllConsultationsForUser(userId: String)

    /** Contar consultas por status. */
    @Query("SELECT status, COUNT(*) as count FROM consultations_local WHERE userId = :userId GROUP BY status")
    suspend fun countConsultationsByStatus(userId: String): List<ConsultationStatusCount>

    /** Buscar consulta marcada para hoje ou próxima. */
    @Query("SELECT * FROM consultations_local WHERE userId = :userId AND scheduledDate >= :nowMillis ORDER BY scheduledDate ASC LIMIT 1")
    suspend fun getNextConsultation(userId: String, nowMillis: Long): ConsultationEntity?
}

/**
 * Lista de compras compartilhada (Ponte Familiar).
 */
@Dao
interface GroceryListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GroceryListItemEntity>)

    /** Lista completa do usuario, ordenada por categoria e nome. */
    @Query("SELECT * FROM grocery_list_local WHERE userId = :userId ORDER BY category ASC, foodName ASC")
    fun getGroceryList(userId: String): Flow<List<GroceryListItemEntity>>

    /** Itens ja marcados como presentes na despensa. */
    @Query("SELECT * FROM grocery_list_local WHERE userId = :userId AND isCheckedInPantry = 1")
    fun getPantryItems(userId: String): Flow<List<GroceryListItemEntity>>

    /** Atualizar o status de compra de um item. */
    @Query("UPDATE grocery_list_local SET isCheckedInPantry = :isChecked WHERE remoteId = :id")
    suspend fun updateCheckedStatus(id: String, isChecked: Boolean)

    /** Limpar a lista do usuario. */
    @Query("DELETE FROM grocery_list_local WHERE userId = :userId")
    suspend fun clearGroceryList(userId: String)
}

/**
 * DAO para registro de alerts criticos de sinais vitais (anti-duplicacao).
 */
@Dao
interface VitalAlertLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: VitalAlertLogEntity)

    @Query("SELECT * FROM vital_alert_log_local WHERE caregiverUserId = :caregiverUserId AND alertType = :alertType LIMIT 1")
    suspend fun getLatestAlert(caregiverUserId: String, alertType: String): VitalAlertLogEntity?

    @Query("DELETE FROM vital_alert_log_local WHERE lastSentAtMs < :cutoffMillis")
    suspend fun purgeOldAlerts(cutoffMillis: Long)
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>)
    
    @Query("SELECT * FROM audit_logs_local WHERE userId = :userId ORDER BY timestamp DESC")
    fun getByUser(userId: String): Flow<List<AuditLogEntity>>
    
    @Query("SELECT * FROM audit_logs_local WHERE userId = :userId AND eventType = :eventType ORDER BY timestamp DESC")
    fun getByType(userId: String, eventType: String): Flow<List<AuditLogEntity>>
    
    @Query("SELECT * FROM audit_logs_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<AuditLogEntity>
    
    @Query("UPDATE audit_logs_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markAsSynced(id: String)
    
    @Query("DELETE FROM audit_logs_local WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOlderThan(cutoffTimestamp: Long)
}

// ============================================================================
// CARE OS — D62 (First Contract)
// ============================================================================

/**
 * Cena C37 — Check-in matinal por voz (sintomas, sono, disposição).
 */
@Dao
interface SymptomsDiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SymptomsDiaryEntity)

    @Query("SELECT * FROM symptoms_diary_local WHERE patientId = :patientId ORDER BY reportedAt DESC")
    fun getAll(patientId: String): Flow<List<SymptomsDiaryEntity>>

    @Query("SELECT * FROM symptoms_diary_local WHERE patientId = :patientId ORDER BY reportedAt DESC LIMIT :limit")
    suspend fun getRecentSync(patientId: String, limit: Int = 30): List<SymptomsDiaryEntity>

    /** Check-in da data informada (epoch-millis) — controla a primeira abertura matinal. */
    @Query(
        "SELECT * FROM symptoms_diary_local WHERE patientId = :patientId " +
            "AND date(reportedAt/1000, 'unixepoch', 'localtime') = date(:dayMillis/1000, 'unixepoch', 'localtime') LIMIT 1"
    )
    suspend fun getForDay(patientId: String, dayMillis: Long): SymptomsDiaryEntity?

    @Query("SELECT * FROM symptoms_diary_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<SymptomsDiaryEntity>

    @Query("UPDATE symptoms_diary_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM symptoms_diary_local WHERE patientId = :patientId")
    suspend fun deleteForPatient(patientId: String)
}

/**
 * Mural de Cuidado Compartilhado — auditoria de quem cuidou do paciente.
 */
@Dao
interface CareAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CareAuditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CareAuditEntity>)

    @Query("SELECT * FROM care_audit_local WHERE patientId = :patientId ORDER BY occurredAt DESC LIMIT :limit")
    fun getRecent(patientId: String, limit: Int = 50): Flow<List<CareAuditEntity>>

    @Query("SELECT * FROM care_audit_local WHERE patientId = :patientId ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun getRecentSync(patientId: String, limit: Int = 50): List<CareAuditEntity>

    @Query("DELETE FROM care_audit_local WHERE patientId = :patientId")
    suspend fun deleteForPatient(patientId: String)
}

/**
 * Telemetria BLE GATT ingerida (pressão arterial / glicemia).
 */
@Dao
interface BleTelemetryReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BleTelemetryReceiptEntity)

    @Query("SELECT * FROM ble_telemetry_receipts_local WHERE patientId = :patientId ORDER BY measuredAt DESC")
    fun getAll(patientId: String): Flow<List<BleTelemetryReceiptEntity>>

    @Query("SELECT * FROM ble_telemetry_receipts_local WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<BleTelemetryReceiptEntity>

    @Query("UPDATE ble_telemetry_receipts_local SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM ble_telemetry_receipts_local WHERE patientId = :patientId")
    suspend fun deleteForPatient(patientId: String)
}
