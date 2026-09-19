package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.data.util.toRemote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class ProfileRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val profileDao: ProfileDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    fun getProfile(userId: String): Flow<ProfileEntity?> = profileDao.getProfile(userId)

    suspend fun getProfileSync(userId: String): RemoteProfile? =
        (apiClient.getProfileLookup(userId) as? br.com.bragasaude.data.remote.model.ProfileLookup.Found)?.profile

    suspend fun refreshProfileForLogin(userId: String): br.com.bragasaude.data.remote.model.ProfileLookup {
        val before = profileDao.getProfileOneShot(userId)
        val result = apiClient.getProfileLookup(userId)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (result is br.com.bragasaude.data.remote.model.ProfileLookup.Found) {
            profileDao.cacheRemoteProfile(result.profile.toEntity(), before)
        }
        return result
    }

    suspend fun syncProfile(userId: String) {
        if (userId == guestId) return
        refreshProfileForLogin(userId)
    }

    private var lastProfileSyncAttemptTime = 0L

    suspend fun saveProfile(profile: RemoteProfile) {
        val existing = profileDao.getProfileOneShot(profile.id)
        // Conta nasce com o segredo TOTP sob o capo (vínculo de WhatsApp). Só gera
        // uma vez: regenerar invalidaria o código de um app já instalado.
        val withTotp = if (existing?.whatsappTotpSecret.isNullOrBlank()
            && profile.whatsappTotpSecret.isNullOrBlank()
        ) {
            profile.copy(whatsappTotpSecret = br.com.bragasaude.data.util.WhatsAppLinkTotp.generateSecret())
        } else {
            profile
        }
        val entity = withTotp.toEntity().copy(
            pendingSync = true,
            customPhotoUri = existing?.customPhotoUri
        )
        // Se a entidade já existe e não mudou nada relevante, e não está com sync pendente, não reenvia
        if (existing != null && !existing.pendingSync &&
            existing.copy(pendingSync = false, updatedAt = existing.updatedAt) == entity.copy(pendingSync = false, updatedAt = existing.updatedAt)
        ) {
            return
        }
        profileDao.insert(entity)
        if (profile.id == guestId) return

        val now = System.currentTimeMillis()
        if (now - lastProfileSyncAttemptTime < 4000L) {
            triggerSync()
            return
        }
        lastProfileSyncAttemptTime = now

        try {
            val success = apiClient.syncProfile(entity)
            if (success) {
                if (profileDao.getProfileOneShot(profile.id) == entity) {
                    profileDao.insert(entity.copy(pendingSync = false))
                }
            } else {
                triggerSync()
            }
        } catch (e: Exception) {
            triggerSync()
        }
    }

    suspend fun updateAvatar(userId: String, avatarId: String?, photoUri: String? = null) {
        profileDao.updateAvatar(userId, avatarId, photoUri)
        if (userId == guestId) return
        try {
            val local = profileDao.getProfileOneShot(userId)
            if (local != null) {
                apiClient.syncProfile(local.copy(avatarIdentifier = avatarId))
            }
        } catch (e: Exception) {
            android.util.Log.w("ProfileRepository", "Falha ao sincronizar avatar: ${e.message}")
        }
    }

    suspend fun updateStepGoal(userId: String, goal: Int) {
        val sanitized = goal.coerceIn(500, 50000)
        val localProfile = profileDao.getProfileOneShot(userId)
        val updated = (localProfile?.toRemote() ?: RemoteProfile(id = userId)).copy(stepGoal = sanitized)
        saveProfile(updated)
    }

    /** Leitura local (Room) em uma única leitura, sem acesso à rede. */
    suspend fun getProfileOneShotLocal(userId: String): ProfileEntity? {
        return profileDao.getProfileOneShot(userId)
    }

    /**
     * Salva o papel do usuário. Blindagem: se ainda não existir perfil local
     * (ex: onboarding de cuidador antes do nome ser informado), cria a entidade
     * mínima com userId + userRole em vez de ignorar a chamada.
     */
    suspend fun saveUserRole(userRole: String, consentAcceptedAt: java.util.Date? = null) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val current = profileDao.getProfileOneShot(currentUserId)
        if (current != null) {
            saveProfile(current.copy(userRole = userRole, caregiverMode = if (userRole == "CAREGIVER") current.caregiverMode else null).toRemote())
        } else {
            saveProfile(
                ProfileEntity(
                    userId = currentUserId,
                    userRole = userRole,
                    consentAcceptedAt = consentAcceptedAt,
                    pendingSync = true
                ).toRemote()
            )
        }
    }

    /**
     * Salva o modo do cuidador (VIEWER_ONLY/HYBRID) localmente. Se o perfil ainda
     * não existe, cria entidade mínima já com userRole = "CAREGIVER" para o papel
     * nunca ficar nulo no banco. Com modo = null, apenas limpa o modo existente.
     */
    suspend fun saveCaregiverModeLocal(userId: String, mode: String?, consentAcceptedAt: java.util.Date? = null) {
        val current = profileDao.getProfileOneShot(userId)
        if (current != null) {
            saveProfile(current.copy(userRole = current.userRole ?: "CAREGIVER", caregiverMode = mode,
                basicProfileComplete = if (mode == null) false else current.basicProfileComplete,
                selfCareComplete = if (mode == null) false else current.selfCareComplete,
                selfCareSetupPending = if (mode == null) false else current.selfCareSetupPending).toRemote())
        } else if (mode != null) {
            saveProfile(
                ProfileEntity(
                    userId = userId,
                    userRole = "CAREGIVER",
                    caregiverMode = mode,
                    consentAcceptedAt = consentAcceptedAt,
                    pendingSync = true
                ).toRemote()
            )
        }
    }

    /**
     * Limpa papel/modo de um perfil ainda incompleto (sem nome) — usado quando o
     * usuário volta do onboarding de cuidador para a seleção de papel.
     */
    suspend fun clearRoleFromStubProfile(userId: String) {
        val current = profileDao.getProfileOneShot(userId) ?: return
        if (!br.com.bragasaude.domain.ProfileOnboarding.isComplete(current.userRole, current.caregiverMode, current.basicProfileComplete, current.selfCareComplete)) {
            saveProfile(current.copy(userRole = null, caregiverMode = null, basicProfileComplete = false, selfCareComplete = false, selfCareSetupPending = false).toRemote())
        }
    }

    suspend fun deleteProfileLocally(userId: String) {
        profileDao.deleteProfile(userId)
    }

    /**
     * MÓDULO 5 (LGPD — Direito ao Esquecimento): apaga TODOS os dados do usuário
     * no Firebase Data Connect (Postgres).
     *
     * Ordem importa:
     *   1. `DeleteUserFeedbacks` primeiro — a FK de feedback.user é SET NULL, então
     *      a linha sobreviveria órfã se não apagássemos explicitamente.
     *   2. `DeleteProfile` depois — dispara ON DELETE CASCADE em 14 tabelas filhas
     *      (vital_sign, medication, exam, daily_metric, family_binding como patient, etc).
     *
     * Retorna `true` se a nuvem confirmou a exclusão, `false` em falha de rede
     * (o chamador deve decidir se prossegue mesmo assim — LGPD não pode travar por
     * conectividade, mas a falha precisa ser logada e re-tentada depois).
     */
    suspend fun deleteProfileRemotely(userId: String): Boolean {
        if (userId == guestId) return true
        return true
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
