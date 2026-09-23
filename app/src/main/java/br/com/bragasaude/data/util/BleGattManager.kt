package br.com.bragasaude.data.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import br.com.bragasaude.data.remote.repository.CareOsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class BleGattState(val running: Boolean = false, val message: String = "Pronto para conectar")

/** Cliente dos perfis Bluetooth SIG Blood Pressure (0x1810) e Glucose (0x1808). */
@Singleton
class BleGattManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val careOsRepository: CareOsRepository
) {
    companion object {
        private val BLOOD_PRESSURE_SERVICE = uuid16(0x1810)
        private val BLOOD_PRESSURE_MEASUREMENT = uuid16(0x2A35)
        private val GLUCOSE_SERVICE = uuid16(0x1808)
        private val GLUCOSE_MEASUREMENT = uuid16(0x2A18)
        private val CCCD = uuid16(0x2902)
        private fun uuid16(value: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(value))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(BleGattState())
    val state = _state.asStateFlow()
    private var patientId: String? = null
    private var gatt: BluetoothGatt? = null

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun scanAndConnect(patientId: String) {
        if (!hasPermissions()) {
            _state.value = BleGattState(message = "Autorize o Bluetooth para conectar o aparelho.")
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BleGattState(message = "Ative o Bluetooth e tente novamente.")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.value = BleGattState(message = "Este aparelho não oferece leitura Bluetooth LE.")
            return
        }
        this.patientId = patientId
        _state.value = BleGattState(true, "Procurando medidor de pressão ou glicemia…")
        val filters = listOf(BLOOD_PRESSURE_SERVICE, GLUCOSE_SERVICE).map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = BleGattState()
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter?.bluetoothLeScanner
            scanner?.stopScan(this)
            _state.value = BleGattState(true, "Conectando ao medidor…")
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothGatt.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = BleGattState(message = "Falha ao procurar aparelho Bluetooth ($errorCode).")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = BleGattState(true, "Aparelho conectado. Lendo medição…")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = BleGattState(message = "Aparelho desconectado.")
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            listOfNotNull(
                gatt.getService(BLOOD_PRESSURE_SERVICE)?.getCharacteristic(BLOOD_PRESSURE_MEASUREMENT),
                gatt.getService(GLUCOSE_SERVICE)?.getCharacteristic(GLUCOSE_MEASUREMENT)
            ).forEach { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(CCCD)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        @Deprecated("API 33 compatibility")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleMeasurement(gatt, characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleMeasurement(gatt, characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleMeasurement(gatt: BluetoothGatt, characteristic: UUID, value: ByteArray) {
        val patient = patientId ?: return
        val deviceId = runCatching { gatt.device.address }.getOrDefault("ble-device")
        val now = System.currentTimeMillis()
        scope.launch {
            val sent = when (characteristic) {
                BLOOD_PRESSURE_MEASUREMENT -> parseBloodPressure(value)?.let { (systolic, diastolic) ->
                    careOsRepository.ingestBle(patient, deviceId, "BLOOD_PRESSURE", now, systolic, diastolic)
                } ?: false
                GLUCOSE_MEASUREMENT -> parseGlucose(value)?.let { glucose ->
                    careOsRepository.ingestBle(patient, deviceId, "GLUCOSE", now, glucose = glucose)
                } ?: false
                else -> false
            }
            _state.value = BleGattState(message = if (sent) "Medição recebida e sincronizada." else "Medição salva; sincronizaremos quando houver internet.")
            close()
        }
    }

    private fun parseBloodPressure(value: ByteArray): Pair<Int, Int>? {
        if (value.size < 5) return null
        val systolic = decodeSfloat(value[1], value[2]).toInt()
        val diastolic = decodeSfloat(value[3], value[4]).toInt()
        return if (systolic in 40..300 && diastolic in 20..200) systolic to diastolic else null
    }

    private fun parseGlucose(value: ByteArray): Int? {
        if (value.size < 13) return null
        val flags = value[0].toInt() and 0xff
        var offset = 10 // flags + sequence + base time
        if (flags and 0x01 != 0) offset += 2 // time offset
        if (value.size < offset + 3 || flags and 0x02 == 0) return null
        val concentration = decodeSfloat(value[offset], value[offset + 1])
        val unitMolPerL = flags and 0x04 != 0
        val mgDl = if (unitMolPerL) concentration * 18_000.0 else concentration * 100_000.0
        return mgDl.toInt().takeIf { it in 20..1000 }
    }

    private fun decodeSfloat(low: Byte, high: Byte): Double {
        val raw = ByteBuffer.wrap(byteArrayOf(low, high)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        var mantissa = raw and 0x0fff
        if (mantissa and 0x0800 != 0) mantissa -= 0x1000
        var exponent = (raw shr 12) and 0x0f
        if (exponent and 0x08 != 0) exponent -= 0x10
        return mantissa * 10.0.pow(exponent)
    }
}
