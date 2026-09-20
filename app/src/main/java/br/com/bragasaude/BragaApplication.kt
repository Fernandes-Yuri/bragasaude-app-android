package br.com.bragasaude

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import br.com.bragasaude.data.remote.repository.CatalogRepository
import br.com.bragasaude.data.remote.sync.BragaFirebaseMessagingService
import br.com.bragasaude.data.remote.sync.SyncManager
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.ui.util.FamilyNotificationService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BragaApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var catalogRepository: CatalogRepository

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            android.util.Log.e("BragaApp", "Falha ao carregar libsqlcipher: ${e.message}", e)
        }

        // G1 (plano de notificações, doc 10 §3.1): os canais da família precisam ser
        // criados no startup, senão o Android 8+ descarta silenciosamente as
        // notificações de mensagem familiar e de cuidado.
        FamilyNotificationService.initChannels(this)

        // Canais de push (FCM): o canal padrao e o que o Play Services usa para
        // desenhar o balao com o app FECHADO. Se nao existir no startup, o
        // primeiro push chega e e descartado (Causa 6 do levantamento).
        BragaFirebaseMessagingService.initChannels(this)

        syncManager.startRealtimeSync()
        syncScheduler.schedulePeriodicRecoverySync()
        
        // AUD-AN07: CoroutineScope sem SupervisorJob — uma exceção no seeding
        // do catálogo propagava e derrubava o processo na inicialização. O
        // scope de aplicação deve sobreviver a falhas de um filho.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            catalogRepository.seedDatabaseIfNeeded()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
}
