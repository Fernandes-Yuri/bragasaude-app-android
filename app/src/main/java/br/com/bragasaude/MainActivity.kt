package br.com.bragasaude

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import br.com.bragasaude.ui.AppContent
import br.com.bragasaude.ui.theme.BragasaudeTheme
import br.com.bragasaude.ui.util.StepTrackingService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val activityGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            results[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        } else true
        if (locationGranted && activityGranted) startTrackingService()
    }

    internal fun startTrackingService() {
        val intent = Intent(this, StepTrackingService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else startService(intent)
    }

    internal fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            getSharedPreferences("braga_prefs", MODE_PRIVATE)
                .edit().putLong("last_notification_prompt", System.currentTimeMillis()).apply()
        }
    }

    internal fun requestTrackingPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).also { list ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) requestPermissionLauncher.launch(toRequest.toTypedArray())
    }

    internal fun openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BragasaudeTheme {
                AppContent(activity = this)
            }
        }
    }
}