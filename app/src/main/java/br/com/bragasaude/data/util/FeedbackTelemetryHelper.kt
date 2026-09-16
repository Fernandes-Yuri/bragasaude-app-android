package br.com.bragasaude.data.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat

object FeedbackTelemetryHelper {

    fun collectDiagnosticTelemetry(context: Context): String {
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        val battery = getBatteryStatus(context)
        val network = getNetworkType(context)
        val activityPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if (granted) "GRANTED" else "DENIED"
        } else {
            "N/A (< Android 10)"
        }
        
        val locationPerm = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED"
        }

        return buildString {
            append("Aparelho: $model | ")
            append("SO: $androidVer | ")
            append("Bateria: $battery | ")
            append("Rede: $network | ")
            append("ACTIVITY_RECOGNITION: $activityPerm | ")
            append("LOCATION: $locationPerm")
        }
    }

    private fun getBatteryStatus(context: Context): String {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            
            if (pct >= 0) {
                "$pct% (${if (isCharging) "Carregando" else "Bateria"})"
            } else {
                "Desconhecido"
            }
        } catch (e: Exception) {
            "N/D"
        }
    }

    private fun getNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Desconhecida"
            val activeNetwork = cm.activeNetwork ?: return "Sem Conexão"
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Sem Conexão"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Rede Móvel (4G/5G)"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Conectado"
            }
        } catch (e: Exception) {
            "N/D"
        }
    }
}
