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
        val initialNotification = createNotification(0, targetSteps)
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

        // Carrega a meta estimada com base no perfil
        serviceScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                val profile = profileDao.getProfileOneShot(userId)
                targetSteps = when (profile?.activityLevel?.lowercase()) {
                    "sedentary", "sedentário" -> 6000
                    "active", "ativo" -> 10000
                    "very_active", "muito ativo" -> 12000
                    else -> 8000
                }
            }
        }

        movementManager.startStepCounter()
        movementManager.startLocationUpdates()
        
        serviceScope.launch {
            movementManager.currentSteps.collectLatest { steps ->
                updateNotification(steps, targetSteps)
            }
        }
        
        return START_STICKY
    }

    private fun updateNotification(steps: Int, target: Int) {
        val notification = createNotification(steps, target)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(steps: Int, target: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val km = (steps * 0.00075f)
        val kcal = (steps * 0.04f).toInt()
        val percent = if (target > 0) ((steps.toFloat() / target) * 100).toInt() else 0

        val detailText = String.format(Locale.getDefault(), "%d passos (%.1f km - %d kcal) - %d%% da meta diaria", steps, km, kcal, percent)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Braga Saúde • Monitor de Atividade")
            .setContentText(detailText)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingIntent)
            .setProgress(target, steps.coerceAtMost(target), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Passos e Atividade",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Exibe o progresso de passos e caminhada em tempo real"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
