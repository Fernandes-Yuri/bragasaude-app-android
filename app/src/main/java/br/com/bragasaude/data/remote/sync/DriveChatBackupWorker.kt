package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.bragasaude.data.backup.ChatBackupRepository
import br.com.bragasaude.data.backup.GoogleDriveAppDataClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Backup periodico criptografado em Wi-Fi + carregamento para `drive.appdata` (CHAT-02).
 * Falha de autorizacao interativa (token expirado/revogado) encerra sem repetir em loop.
 */
@HiltWorker
class DriveChatBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: ChatBackupRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!backupRepository.isAutoBackupEnabled()) return Result.success()
        val passphrase = backupRepository.getSavedAutoBackupPassphrase() ?: return Result.success()

        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GoogleDriveAppDataClient.DRIVE_APPDATA_SCOPE)))
                .build()
            val authResult = Identity.getAuthorizationClient(applicationContext)
                .authorize(authRequest)
                .await()
            if (authResult.hasResolution()) {
                return Result.success()
            }
            val accessToken = authResult.accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.success()

            backupRepository.backupToGoogleDrive(accessToken, passphrase).fold(
                onSuccess = { Result.success() },
                onFailure = { err ->
                    if (err is SecurityException) Result.success() else Result.retry()
                }
            )
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
