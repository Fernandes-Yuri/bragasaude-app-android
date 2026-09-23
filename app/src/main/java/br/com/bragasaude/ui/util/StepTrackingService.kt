package br.com.bragasaude.ui.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import br.com.bragasaude.MainActivity
import br.com.bragasaude.R
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.util.MovementManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class StepTrackingService : Service() {

    @Inject
    lateinit var movementManager: MovementManager

    @Inject
    lateinit var profileDao: ProfileDao

    @Inject
    lateinit var auth: FirebaseAuth

    private val CHANNEL_ID = "step_tracking_channel"
    private val NOTIFICATION_ID = 1001
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var targetSteps = 8000

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BragaSaude::StepTrackingWakeLock").apply {
                setReferenceCounted(false)
                acquire() // Sem timeout fixo — libera no onDestroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                initialNotification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasActivity) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Carrega a meta de passos do perfil de forma reativa
        serviceScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                profileDao.getProfile(userId).collectLatest { profile ->
                    targetSteps = profile?.stepGoal?.takeIf { it > 0 } ?: when (profile?.activityLevel?.lowercase()) {
                        "sedentary", "sedentário" -> 6000
                        "active", "ativo" -> 10000
                        "very_active", "muito ativo" -> 12000
                        else -> 8000
                    }
                }
            }
        }

        movementManager.startStepCounter()
        movementManager.startLocationUpdates()
        
        // Rastreio silencioso em background: não posta atualizações repetitivas na barra de status.
        // Apenas verifica se atingiu 50% ou 100% da meta para celebrar conquistas pontuais (D62).
        serviceScope.launch {
            movementManager.currentSteps.collectLatest { steps ->
                NotificationHelper.checkAndNotifyStepGoal(this@StepTrackingService, steps, targetSteps)
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Notificação silenciosa exigida pelo Android para Foreground Services.
     * ongoing = false e PRIORITY_MIN garantem que não polua a gaveta ou a barra de status.
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Braga Saúde")
            .setContentText("Monitoramento de atividade em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Passos e Atividade",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitoramento silencioso de atividade em segundo plano"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
