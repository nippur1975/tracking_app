package com.example.segnmea

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class ThingSpeakForegroundService : Service() {

    private val channelId = "thingspeak_service"
    private val notifId = 1001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    // Bluetooth
    private lateinit var bluetoothManager: BluetoothManager
    private val nmeaParser = NmeaParser()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize Bluetooth here so it runs in background
        bluetoothManager = BluetoothManager(this,
            onDataReceived = { line -> onBluetoothDataReceived(line) },
            onStatusChange = { status ->
                // Optional: Update notification or log status
                Log.d("ServiceBT", status)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifId, buildNotification("Enviando datos a ThingSpeak cada 15 s"))

        // Try to connect if not connected
        autoConnectToFuruno()

        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    try {
                        val data = GlobalData.currentData
                        // Only upload if we have meaningful data (e.g. at least speed or heading or pos)
                        // Or just upload whatever we have as "heartbeat"

                        // Check if we have an API Key
                        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
                        val apiKey = sharedPreferences.getString("write_api_key", "A9UJBBGRO6NP852V")

                        if (!apiKey.isNullOrEmpty()) {
                            sendToThingSpeak(
                                apiKey,
                                data.latitude ?: 0.0,
                                data.longitude ?: 0.0,
                                data.heading ?: 0.0,
                                data.speed ?: 0.0,
                                data.pitch ?: 0.0,
                                data.roll ?: 0.0,
                                data.rot ?: 0.0
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(15_000) // 15 seconds
                }
            }
        }

        // Si Android mata el proceso, intenta recrear el service
        return START_STICKY
    }

    private fun autoConnectToFuruno() {
        // Simple auto-connect logic: check paired devices
        try {
            val pairedDevices = bluetoothManager.getPairedDevices()
            val targetDevice = pairedDevices.find { it.name == "SC50_FURUNO" }
            if (targetDevice != null) {
                bluetoothManager.connect(targetDevice.address)
            }
        } catch (e: SecurityException) {
            Log.e("ThingSpeakService", "Permission missing for Bluetooth scan/connect", e)
        }
    }

    private fun onBluetoothDataReceived(line: String) {
        // Parse and update GlobalData
        val data = nmeaParser.parse(line)
        GlobalData.update(data)
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        scope.cancel()
        bluetoothManager.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendToThingSpeak(
        apiKey: String,
        lat: Double, lon: Double, rumbo: Double, vel: Double, pitch: Double, roll: Double, rot: Double
    ) {
        // field1=Lat, field2=Lon, field3=Hdg, field4=Spd, field5=Pitch, field6=Roll, field7=ROT
        val urlStr =
            "https://api.thingspeak.com/update?api_key=$apiKey" +
                    "&field1=$lat&field2=$lon&field3=$rumbo&field4=$vel&field5=$pitch&field6=$roll&field7=$rot"

        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET" // ThingSpeak update via GET is simpler here than Volley POST for background service
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            conn.inputStream.use { it.readBytes() } // Consume response
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("LalitoTX Service")
            .setContentText(text)
            .setSmallIcon(R.drawable.barco)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Envío ThingSpeak",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
