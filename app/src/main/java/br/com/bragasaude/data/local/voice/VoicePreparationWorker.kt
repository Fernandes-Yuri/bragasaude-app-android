package br.com.bragasaude.data.local.voice

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class VoicePreparationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val manager: VoiceProfileManager,
    private val notifications: VoicePreparationNotifications
) : CoroutineWorker(context, parameters) {
    companion object {
        const val WORK_NAME = "prepare_assistant_voice"
        const val VOICE_ID = "voice_id"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(VoicePreparationNotifications.ONGOING_ID, notifications.ongoing(), type)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(VOICE_ID) ?: return Result.failure()
        return try {
            setForeground(getForegroundInfo())
            if (manager.prepareSelection(id)) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            manager.preparationCouldNotStart(id)
            Result.failure()
        }
    }
}
