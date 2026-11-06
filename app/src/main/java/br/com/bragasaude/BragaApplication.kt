package br.com.bragasaude

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.network.AvatarAuthInterceptor
import okhttp3.OkHttpClient
import br.com.bragasaude.data.remote.network.GatewayTlsPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import coil3.ImageLoader
import coil3.MemoryCache
import coil3.SingletonImageLoader
import coil3.cache.LruMemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
class BragaApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory, ComponentCallbacks2 {

    @Inject lateinit var authService: AuthService
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        br.com.bragasaude.data.local.RetiredNutritionCleanup.run(this)
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

        // Encerra a sessão temporária se houver saída ou troca de conta.
        val organizerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val organizerAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        var organizerUid = organizerAuth.currentUser?.uid
        organizerAuth.addAuthStateListener { changedAuth ->
            val newUid = changedAuth.currentUser?.uid
            if (newUid != organizerUid) {
                br.com.bragasaude.ai.BragaRoutingLogger.clear()
                organizerUid = newUid
                organizerScope.launch { br.com.bragasaude.data.local.organizer.OrganizerStore(this@BragaApplication).clearAll() }
            }
        }
        syncManager.startRealtimeSync()
        syncScheduler.schedulePeriodicRecoverySync()

        // AUD-AN07: CoroutineScope sem SupervisorJob — uma exceção no seeding
        // do catálogo propagava e derrubava o processo na inicialização. O
        // scope de aplicação deve sobreviver a falhas de um filho.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Expurgo da área de exames retirada; não toca nos originais externos.
            java.io.File(filesDir, "exams").deleteRecursively()
            listOf("exam_photos", "exam_originals", "dossiers").forEach { java.io.File(cacheDir, it).deleteRecursively() }
            java.io.File(cacheDir, "shared_pdfs").listFiles()?.filter { it.name.startsWith("exame_") }?.forEach { it.delete() }
            br.com.bragasaude.data.local.organizer.OrganizerStore(this@BragaApplication).cleanup()
        }

        // Ouve eventos de pressão de memória do SO para liberar caches voláteis
        // (Coil 3) antes do Low Memory Killer encerrar o processo.
        registerComponentCallbacks(this)
    }

    /**
     * Libera o cache de memória do Coil 3 nos níveis de corte do SO, evitando
     * que o LMK (Low Memory Killer) aborte o app em dispositivos com pouca RAM.
     */
    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                try {
                    SingletonImageLoader.get(this).memoryCache.evictAll()
                } catch (e: Exception) {
                    android.util.Log.w("BragaApp", "Falha ao evictar cache Coil no onTrimMemory: ${e.message}")
                }
            }
        }
    }

    override fun onLowMemory() {
        try {
            SingletonImageLoader.get(this).memoryCache.evictAll()
        } catch (e: Exception) {
            android.util.Log.w("BragaApp", "Falha ao evictar cache Coil no onLowMemory: ${e.message}")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onMemoryWarning() {}

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        // Limite o MemoryCache do Coil 3 a 20% da memória disponível do app,
        // evitando estouro de RAM em telas com muitas imagens (ex: feedback, organizador).
        val maxCacheBytes = availableMemoryBytes(this) * 0.20
        return ImageLoader.Builder(context)
            .memoryCacheFactory { ctx -> LruMemoryCache(ctx, (maxCacheBytes).coerceAtLeast(0L)) }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = {
                    GatewayTlsPolicy.configure(OkHttpClient.Builder(), !BuildConfig.DEBUG)
                        .addInterceptor(AvatarAuthInterceptor(BuildConfig.BASE_URL.toHttpUrl()) { forceRefresh ->
                            authService.getTokenBlocking(forceRefresh = forceRefresh)
                        })
                        .build()
                }))
            }
            .build()
    }

    private fun availableMemoryBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return (am.memoryClass * 1024 * 1024).toLong()
    }
}
