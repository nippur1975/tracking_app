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
    private val aisParser = AisParser()
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
        if (line.startsWith("!")) {
            val target = aisParser.parse(line)
            if (target != null) {
                GlobalData.updateAis(target)
                scope.launch {
                    sendAisToSupabase(target)
                }
            }
        } else {
            val data = nmeaParser.parse(line)
            GlobalData.update(data)
        }
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
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val shipId = sharedPreferences.getString("ship_id", "LalitoTX") ?: "LalitoTX"
        
        // Exact schema requested for own ship:
        // ship_id, date_event, lat, lon, rumbo, velocidad, pitch, roll, rot
        
        val json = JSONObject()
        json.put("ship_id", shipId)
        
        // Use NMEA timestamp if available, else current time in ISO 8601 format
        val timestamp = data.timestamp ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        
        json.put("date_event", timestamp)
        json.put("lat", data.latitude ?: 0.0)
        json.put("lon", data.longitude ?: 0.0)
        json.put("rumbo", data.heading ?: 0.0)
        json.put("velocidad", data.speed ?: 0.0)
        json.put("pitch", data.pitch ?: 0.0)
        json.put("roll", data.roll ?: 0.0)
        json.put("rot", data.rot ?: 0.0)

        postJson(fullUrl, key, json)
    }

    private fun sendAisToSupabase(target: AisTarget) {
        // Config for AIS table
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val supabaseUrl = sharedPreferences.getString("supabase_url", "https://lnxziegzyilfnibmfrtz.supabase.co") ?: ""
        val supabaseKey = sharedPreferences.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxueHppZWd6eWlsZm5pYm1mcnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMjI1OTQsImV4cCI6MjA4NDU5ODU5NH0.ltom27lQCmTyI-3NfPW6tMWpEMOL6fXh2dc8ksx0DsQ") ?: ""
        val shipId = sharedPreferences.getString("ship_id", "LalitoTX") ?: "LalitoTX"
        val aisTable = "ais_live" // Fixed table name as per requirement

        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) return

        val fullUrl = "$supabaseUrl/rest/v1/$aisTable"
        
        // Schema: mmsi, lat, lon, sog, cog, heading, rot, ship_name, source_id, last_seen (auto?)
        // We will send what we have.
        val json = JSONObject()
        json.put("mmsi", target.mmsi)
        if (target.latitude != null) json.put("lat", target.latitude)
        if (target.longitude != null) json.put("lon", target.longitude)
        if (target.speed != null) json.put("sog", target.speed)
        if (target.course != null) json.put("cog", target.course)
        if (target.heading != null) json.put("heading", target.heading)
        if (target.rot != null) json.put("rot", target.rot)
        if (target.name != null) json.put("ship_name", target.name)
        json.put("source_id", shipId)
        
        // For UPSERT to work on MMSI primary key, we need specific header
        // Header "Prefer: resolution=merge-duplicates"
        
        postJson(fullUrl, supabaseKey, json, true)
    }

    private fun postJson(urlStr: String, key: String, json: JSONObject, isUpsert: Boolean = false) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            // Supabase Headers
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            if (isUpsert) {
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            } else {
                conn.setRequestProperty("Prefer", "return=minimal")
            }
            
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray())
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Log.e("SupabaseService", "Error sending data to $urlStr: $responseCode")
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
