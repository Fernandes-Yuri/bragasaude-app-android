package br.com.bragasaude

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import br.com.bragasaude.data.remote.repository.CatalogRepository
import br.com.bragasaude.data.remote.sync.SyncManager
import br.com.bragasaude.data.remote.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        
        syncManager.startRealtimeSync()
        syncScheduler.schedulePeriodicRecoverySync()
        
        CoroutineScope(Dispatchers.IO).launch {
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
