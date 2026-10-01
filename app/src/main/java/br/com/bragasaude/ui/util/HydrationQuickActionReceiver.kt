package br.com.bragasaude.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.local.VitalSignEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class HydrationQuickActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var vitalSignDao: VitalSignDao

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onReceive(context: Context, intent: Intent) {
        val amountMl = intent.getIntExtra("amount_ml", 250)
        if (amountMl <= 0) return
        val userId = auth.currentUser?.uid ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                vitalSignDao.insert(
                    VitalSignEntity(
                        userId = userId,
                        hydrationMl = amountMl,
                        measuredAt = Date(),
                        status = "recorded",
                        pendingSync = true
                    )
                )
                // Primeiro registra, depois encerra o lembrete e mostra a confirmação sem ações.
                NotificationHelper.confirmHydrationRegistration(context, amountMl)
                // Dispara sincronização em segundo plano para persistir no Cloud SQL
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<br.com.bragasaude.data.remote.sync.SyncWorker>()
                    .setConstraints(constraints)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
