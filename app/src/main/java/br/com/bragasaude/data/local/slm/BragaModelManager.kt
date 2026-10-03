package br.com.bragasaude.data.local.slm

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BragaModelState(val ready: Boolean = false, val busy: Boolean = false,
    val progress: Int = 0, val message: String? = null)

@Singleton
class BragaModelManager @Inject constructor(private val work: WorkManager, private val store: BragaModelStore) {
    companion object { const val WORK_NAME = "install_braga_slm_v2_2" }
    private val mutable = MutableStateFlow(BragaModelState(ready = store.installed()))
    val state = mutable.asStateFlow()
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            work.getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { infos ->
                val info = infos.lastOrNull()
                val busy = info?.state?.isFinished == false
                mutable.value = BragaModelState(ready = store.installed(), busy = busy,
                    progress = info?.progress?.getInt("percent", 0) ?: 0,
                    message = when {
                        info?.state == WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Não foi possível instalar o Braga. Tente novamente."
                        info?.state == WorkInfo.State.ENQUEUED -> "Aguardando conexão para baixar"
                        info?.state == WorkInfo.State.RUNNING -> info.progress.getString("phase") ?: "Preparando Braga"
                        else -> null
                    })
            }
        }
    }
    fun install() {
        if (store.installed()) return
        val request = OneTimeWorkRequestBuilder<BragaModelWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
    fun cancel() { work.cancelUniqueWork(WORK_NAME) }
}
