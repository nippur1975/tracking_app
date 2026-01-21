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
import org.json.JSONObject

class SupabaseForegroundService : Service() {

    private val channelId = "supabase_service"
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
                Log.d("ServiceBT", status)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifId, buildNotification("Sending data to Supabase every 15s"))

        // Try to connect if not connected
        autoConnectToFuruno()

        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    try {
                        val data = GlobalData.currentData

                        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
                        val supabaseUrl = sharedPreferences.getString("supabase_url", "https://lnxziegzyilfnibmfrtz.supabase.co") ?: ""
                        val supabaseKey = sharedPreferences.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxueHppZWd6eWlsZm5pYm1mcnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMjI1OTQsImV4cCI6MjA4NDU5ODU5NH0.ltom27lQCmTyI-3NfPW6tMWpEMOL6fXh2dc8ksx0DsQ") ?: ""
                        val tableName = sharedPreferences.getString("supabase_table", "nmea_logs") ?: "nmea_logs"

                        if (supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty() && tableName.isNotEmpty()) {
                            sendToSupabase(
                                supabaseUrl,
                                supabaseKey,
                                tableName,
                                data
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(15_000) // 15 seconds
                }
            }
        }

        return START_STICKY
    }

    private fun autoConnectToFuruno() {
        try {
            val pairedDevices = bluetoothManager.getPairedDevices()
            val targetDevice = pairedDevices.find { it.name == "SC50_FURUNO" }
            if (targetDevice != null) {
                bluetoothManager.connect(targetDevice.address)
            }
        } catch (e: SecurityException) {
            Log.e("SupabaseService", "Permission missing for Bluetooth scan/connect", e)
        }
    }

    private fun onBluetoothDataReceived(line: String) {
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

    private fun sendToSupabase(urlBase: String, key: String, table: String, data: NmeaData) {
        // Construct Endpoint
        // https://<project>.supabase.co/rest/v1/<table>
        val fullUrl = "$urlBase/rest/v1/$table"

        val json = JSONObject()
        json.put("latitude", data.latitude ?: 0.0)
        json.put("longitude", data.longitude ?: 0.0)
        json.put("heading", data.heading ?: 0.0)
        json.put("speed", data.speed ?: 0.0)
        json.put("pitch", data.pitch ?: 0.0)
        json.put("roll", data.roll ?: 0.0)
        json.put("rot", data.rot ?: 0.0)

        // Optional: timestamp provided by device or server time.
        // Supabase usually handles 'created_at' automatically if configured.
        // If we want to send the NMEA timestamp:
        if (data.timestamp != null) {
             json.put("nmea_timestamp", data.timestamp)
        }

        try {
            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            // Supabase Headers
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")

            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray())
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Log.e("SupabaseService", "Error sending data: $responseCode")
            }
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
                "Envío Supabase",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
