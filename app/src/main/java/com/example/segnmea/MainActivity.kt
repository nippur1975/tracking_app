package com.example.segnmea

import android.bluetooth.BluetoothDevice
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.LruCache
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private val bitmapCache = LruCache<String, Bitmap>(1024)
    private lateinit var map: GoogleMap
    private var boatMarker: Marker? = null // Generic marker ref if needed
    private var historicalMarkers = mutableListOf<Marker>()
    private lateinit var trackPolyline: Polyline // Generic polyline ref
    private var rulerPolyline: Polyline? = null
    private var rulerMarkers = mutableListOf<Marker>()
    private var rulerPoints = mutableListOf<LatLng>()
    private var handler = Handler(Looper.getMainLooper())
    private var boatMarkers = mutableMapOf<String, Marker>()
    private var historicalData = mutableMapOf<String, MutableList<TrackPoint>>()
    private var trackPolylines = mutableMapOf<String, Polyline>()
    private var markerToTrackPointMap = mutableMapOf<Marker, TrackPoint>()
    // Default to local bluetooth channel to show data immediately
    private var currentChannel = "local_bluetooth" 
    private var channelName = "My Boat (Bluetooth)"

    // Local Data
    private val LOCAL_CHANNEL_ID = "local_bluetooth"
    private var currentDay = ""
    
    // Explicitly declared state variables
    var lastBluetoothDataTime: Long = System.currentTimeMillis()
    var isBluetoothConnected: Boolean = false

    // Watchdog logic (UI only now)
    private val WATCHDOG_INTERVAL = 1000L
    private val BLUETOOTH_TIMEOUT = 3000L

    // UI Data Listener
    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            onGlobalDataUpdated(data)
        }
    }
    
    // UI Update Throttling
    private val uiUpdateHandler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateAisMarkers(GlobalData.aisTargets)
            uiUpdateHandler.postDelayed(this, 1000) // Update every 1 second
        }
    }
    
    private val aisMarkers = mutableMapOf<Int, Marker>()
    private var showAisNames = true

    // Remote Mode
    private lateinit var supabaseReader: SupabaseReader
    private val remoteShips = mapOf(
        "Barco 1" to "ship_1",
        "Barco 2" to "ship_2",
        "Barco 3" to "ship_3",
        "Barco 4" to "ship_4",
        "Barco 5" to "ship_5",
        "Barco 6" to "ship_6"
    )
    private val remotePollingHandler = Handler(Looper.getMainLooper())
    private var remotePollingRunnable: Runnable? = null
    
    // Icon Caching
    data class IconKey(val name: String, val bucket: Int, val showName: Boolean)
    private val iconCache = HashMap<IconKey, BitmapDescriptor>()

    private fun getIcon(name: String, heading: Float): BitmapDescriptor {
        val bucket = (((heading % 360) + 360) % 360 / 10).toInt() * 10  // 0,10,20...
        val key = IconKey(name, bucket, showAisNames)

        return iconCache.getOrPut(key) {
            BitmapDescriptorFactory.fromBitmap(
                createAisMarkerBitmap(this, name, bucket.toFloat(), showAisNames)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        supabaseReader = SupabaseReader(this)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        // Default to local_bluetooth if not set
        currentChannel = sharedPreferences.getString("current_channel", LOCAL_CHANNEL_ID) ?: LOCAL_CHANNEL_ID
        channelName = sharedPreferences.getString("current_channel_name", "My Boat (Bluetooth)") ?: "My Boat (Bluetooth)"

        // Check if we started in a remote channel
        if (currentChannel != LOCAL_CHANNEL_ID && remoteShips.containsValue(currentChannel)) {
             startRemotePolling(currentChannel)
        }

        // IMPORTANT: Replace "YOUR_MAP_ID" with your actual Map ID if using Cloud Styling
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        // Navigation buttons
        binding.compassButton.setOnClickListener {
            val intent = Intent(this, CompassActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }
        binding.clinometerButton.setOnClickListener {
            val intent = Intent(this, ClinometerActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }
        binding.dataButton.setOnClickListener {
            val intent = Intent(this, DataActivity::class.java)
            intent.putExtra("channel_id", currentChannel)
            startActivity(intent)
        }

        binding.rulerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.rulerInfoTextView.visibility = View.VISIBLE
                map.setOnMapClickListener { latLng ->
                    addRulerPoint(latLng)
                }
            } else {
                binding.rulerInfoTextView.visibility = View.GONE
                map.setOnMapClickListener(null)
                clearRuler()
            }
        }

        binding.showNamesSwitch.setOnCheckedChangeListener { _, isChecked ->
            showAisNames = isChecked
            // Force refresh of markers
            updateAisMarkers(GlobalData.aisTargets)
        }
        
        startWatchdog()
        checkPermissionsAndStartService()
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
        } else {
            // All permissions granted
            startThingSpeakService()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startThingSpeakService()
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth service", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startThingSpeakService() {
        val intent = Intent(this, SupabaseForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true

        // Initialize local polyline
        val localPolyline = map.addPolyline(
             PolylineOptions()
                .width(5f)
                .color(0xFF00FF00.toInt()) // Green for local
        )
        trackPolylines[LOCAL_CHANNEL_ID] = localPolyline
        historicalData[LOCAL_CHANNEL_ID] = mutableListOf()

        // historicalMarkers.forEach { it.isVisible = true } // Disable historical dots

        map.setPadding(0, 0, 0, binding.buttonContainer.height)

        map.setOnCameraIdleListener {
            val zoom = map.cameraPosition.zoom
            val scale = if (zoom >= map.maxZoomLevel) 0.5f else (zoom / 15f).coerceAtLeast(0.5f).coerceAtMost(2f)
            boatMarkers.values.forEach { marker ->
                val originalBitmap = marker.tag as? Bitmap
                if (originalBitmap != null) {
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (originalBitmap.width * scale).toInt(), (originalBitmap.height * scale).toInt(), false)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                }
            }
        }
    }

    private val watchdogTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            // Access using this@MainActivity to ensure correct scope resolution
            if (now - this@MainActivity.lastBluetoothDataTime > BLUETOOTH_TIMEOUT) {
                showNoSignal()
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL)
        }
    }

    private fun startWatchdog() {
        handler.post(watchdogTask)
    }

    private fun stopWatchdog() {
        handler.removeCallbacks(watchdogTask)
    }
    
    private fun showNoSignal() {
        binding.latTextView.text = "Sin señal"
        binding.lonTextView.text = "Sin señal"
        binding.speedTextView.text = "Sin señal"
        binding.headingTextView.text = "Sin señal"
        binding.pitchTextView.text = "Sin señal"
        binding.rollTextView.text = "Sin señal"
    }


    private fun updateUI(lat: String, lon: String, speed: String, heading: String, pitch: String, roll: String, rot: String = "0") {
        val latFormatted = formatCoordinate(lat, "N", "S")
        val lonFormatted = formatCoordinate(lon, "E", "W")
        
        // Update ROT view
        val rotValue = rot.toFloatOrNull() ?: 0f
        val rotView = findViewById<ROTView>(R.id.rotView)
        rotView?.setROT(rotValue)
        val headingAbs = heading.toDoubleOrNull()?.let { Math.abs(it) }?.toInt()?.toString() ?: heading
        val speedAbs = speed.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: speed

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("language", "en") ?: "en"
        val shipId = sharedPreferences.getString("ship_id", "LalitoTX") ?: "LalitoTX"

        binding.channelNameTextView.text = shipId
        binding.rotTextView.text = "ROT: ${rotValue.toInt()}"

        if (language == "es") {
            binding.latTextView.text = "${getString(R.string.lat_es)} : $latFormatted"
            binding.lonTextView.text = "${getString(R.string.lon_es)} : $lonFormatted"
            binding.speedTextView.text = "${getString(R.string.speed_es)} : $speedAbs kn"
            binding.headingTextView.text = "${getString(R.string.heading_es)} : $headingAbs°"
            binding.pitchTextView.text = "${getString(R.string.pitch_es)} : $pitch°"
            binding.rollTextView.text = "${getString(R.string.roll_es)} : $roll°"
        } else {
            binding.latTextView.text = "${getString(R.string.lat)} : $latFormatted"
            binding.lonTextView.text = "${getString(R.string.lon)} : $lonFormatted"
            binding.speedTextView.text = "${getString(R.string.speed)} : $speedAbs kn"
            binding.headingTextView.text = "${getString(R.string.heading)} : $headingAbs°"
            binding.pitchTextView.text = "${getString(R.string.pitch)} : $pitch°"
            binding.rollTextView.text = "${getString(R.string.roll)} : $roll°"
        }
    }

    private fun formatCoordinate(coordinate: String, positiveDirection: String, negativeDirection: String): String {
        return try {
            val value = coordinate.toDouble()
            val degrees = Math.abs(value.toInt())
            val minutes = Math.abs(value - value.toInt()) * 60
            val direction = if (value >= 0) positiveDirection else negativeDirection
            "$degrees° ${"%.3f".format(minutes)}' $direction"
        } catch (e: NumberFormatException) {
            coordinate 
        }
    }

    private fun getBitmap(resId: Int, color: Int): Bitmap? {
        val cacheKey = "$resId-$color"
        var bitmap = bitmapCache.get(cacheKey)
        if (bitmap == null) {
            val drawable = ContextCompat.getDrawable(this, resId) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth * 2 else 100
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight * 2 else 100
            drawable.setBounds(0, 0, width, height)
            drawable.setTint(color)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun createOwnShipBitmap(heading: Float): Bitmap {
        val width = 400
        val height = 200
        val centerX = width / 2f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Triángulo
        paint.color = Color.RED
        val path = Path()
        // Triangle centered horizontally, lower half of bitmap
        path.moveTo(centerX, 80f)
        path.lineTo(centerX - 20, 120f)
        path.lineTo(centerX + 20, 120f)
        path.close()

        canvas.save()
        canvas.rotate(heading, centerX, 100f) // Pivot around center of triangle
        canvas.drawPath(path, paint)
        canvas.restore()

        return bitmap
    }

    private fun createAisMarkerBitmap(
        context: Context,
        shipName: String,
        heading: Float,
        showName: Boolean
    ): Bitmap {

        val width = 400
        val height = 200
        val centerX = width / 2f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (showName) {
            // Texto
            paint.color = Color.RED
            paint.textSize = 48f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER

            // Nombre arriba
            canvas.drawText(shipName, centerX, 60f, paint)
        }

        // Triángulo
        paint.color = Color.BLUE
        val path = Path()
        path.moveTo(centerX, 80f)
        path.lineTo(centerX - 20, 120f)
        path.lineTo(centerX + 20, 120f)
        path.close()

        canvas.save()
        canvas.rotate(heading, centerX, 100f) // Pivot around center of triangle
        canvas.drawPath(path, paint)
        canvas.restore()

        return bitmap
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showPasswordDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPasswordDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "Password"
        
        AlertDialog.Builder(this)
            .setTitle("Service Settings")
            .setMessage("Enter Password:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == "29121975") {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        val message = getString(R.string.about_message) + "\n\n\n" + "desarrollado por Hdelacruz"
        val textView = android.widget.TextView(this)
        textView.text = message
        textView.gravity = android.view.Gravity.CENTER
        textView.setPadding(32, 32, 32, 32)
        textView.textSize = 16f
        textView.setTextColor(android.graphics.Color.BLACK)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setView(textView)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
    }

    private fun showChannelSelectionDialog() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channels = mutableListOf<String>()
        val channelDisplayNames = mutableListOf<String>()

        // 1. Bluetooth Local
        channels.add(LOCAL_CHANNEL_ID)
        channelDisplayNames.add("Bluetooth Local")

        // 2. Custom Channels (from Settings)
        for (i in 1..8) {
            val channel = sharedPreferences.getString("channel$i", "")
            if (channel?.isNotEmpty() == true) {
                channels.add(channel)
                val idx = i
                channelDisplayNames.add(sharedPreferences.getString("channel_name_$idx", "Canal $idx") ?: "Canal $idx")
            }
        }

        // 3. Remote Ships (Fixed List)
        remoteShips.forEach { (name, id) ->
            channels.add(id)
            channelDisplayNames.add(name)
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccionar Barco")
        
        // Find current index
        var currentIndex = channels.indexOf(currentChannel)
        if (currentIndex == -1) currentIndex = 0

        builder.setSingleChoiceItems(channelDisplayNames.toTypedArray(), currentIndex) { dialog, which ->
            val selectedChannel = channels[which]
            val selectedName = channelDisplayNames[which]

            stopRemotePolling()

            if (selectedChannel == LOCAL_CHANNEL_ID) {
                switchToLocalChannel()
            } else {
                currentChannel = selectedChannel
                channelName = selectedName
                isBluetoothConnected = false
                
                val editor = sharedPreferences.edit()
                editor.putString("current_channel", currentChannel)
                editor.putString("current_channel_name", channelName)
                editor.apply()
                
                updateHistoricalMarkers()
                
                // If it is a remote ship, start polling
                if (remoteShips.containsValue(selectedChannel)) {
                    startRemotePolling(selectedChannel)
                } else {
                    // It's a custom channel (maybe legacy), try to center if exists
                     val boatMarker = boatMarkers[currentChannel]
                    if (boatMarker != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(boatMarker.position, 15f))
                    }
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.create().show()
    }

    private fun startRemotePolling(shipId: String) {
        // Stop any existing
        stopRemotePolling()

        remotePollingRunnable = object : Runnable {
            override fun run() {
                // Fetch Ship Data
                supabaseReader.fetchShipData(shipId) { data ->
                    // Check for stale callback
                    if (currentChannel != shipId) return@fetchShipData

                    if (data != null) {
                        runOnUiThread {
                            if (currentChannel == shipId) {
                                updateRemoteUI(data, shipId)
                            }
                        }
                    }
                }

                // Fetch AIS Data
                supabaseReader.fetchAisData { targets ->
                    // Check for stale callback
                    if (currentChannel != shipId) return@fetchAisData

                    runOnUiThread {
                        if (currentChannel == shipId) {
                            val mapTargets = targets.associateBy { it.mmsi }
                            updateAisMarkers(mapTargets)
                            // Push to GlobalData so other activities (Data/Compass) see it
                            targets.forEach { GlobalData.updateAis(it) }
                        }
                    }
                }

                remotePollingHandler.postDelayed(this, 3000) // Poll every 3 seconds
            }
        }
        remotePollingHandler.post(remotePollingRunnable!!)
    }

    private fun stopRemotePolling() {
        remotePollingRunnable?.let { remotePollingHandler.removeCallbacks(it) }
        remotePollingRunnable = null
    }

    private fun updateRemoteUI(data: NmeaData, shipId: String) {
        // Prevent watchdog from showing "No Signal"
        lastBluetoothDataTime = System.currentTimeMillis()

        // Push to GlobalData so Compass/Data activities work
        GlobalData.update(data)

        // Update Dashboard
        updateUI(
            data.latitude?.toString() ?: "0",
            data.longitude?.toString() ?: "0",
            data.speed?.toString() ?: "0",
            data.heading?.toString() ?: "0",
            data.pitch?.toString() ?: "0",
            data.roll?.toString() ?: "0",
            data.rot?.toString() ?: "0"
        )

        // Update Map Marker for Remote Ship
        if (data.latitude != null && data.longitude != null) {
            val position = LatLng(data.latitude!!, data.longitude!!)
            val heading = data.heading?.toFloat() ?: 0f
            val redTriangleBitmap = createOwnShipBitmap(heading)

            val boatMarker = boatMarkers[shipId]
            if (boatMarker == null) {
                val newMarker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .icon(BitmapDescriptorFactory.fromBitmap(redTriangleBitmap))
                        .anchor(0.5f, 0.5f)
                        .title(channelName)
                )
                if (newMarker != null) {
                    newMarker.tag = redTriangleBitmap
                    boatMarkers[shipId] = newMarker
                }
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
            } else {
                boatMarker.position = position
                boatMarker.setIcon(BitmapDescriptorFactory.fromBitmap(redTriangleBitmap))
            }
        }
    }
    
    private fun switchToLocalChannel() {
        stopRemotePolling()
        currentChannel = LOCAL_CHANNEL_ID
        channelName = "My Boat (Bluetooth)"
        updateHistoricalMarkers()
        // If we have data, center on it
        val lastPoint = historicalData[LOCAL_CHANNEL_ID]?.lastOrNull()
        if (lastPoint != null) {
            updateUI(lastPoint.lat.toString(), lastPoint.lon.toString(), lastPoint.speed, lastPoint.heading, lastPoint.pitch, lastPoint.roll)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastPoint.getPosition(), 15f))
        }
    }

    private fun addRulerPoint(latLng: LatLng) {
        rulerPoints.add(latLng)

        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        if (marker != null) {
            rulerMarkers.add(marker)
        }

        if (rulerPoints.size >= 2) {
            if (rulerPolyline == null) {
                rulerPolyline = map.addPolyline(PolylineOptions().width(5f).color(ContextCompat.getColor(this, R.color.teal_700)))
            }
            rulerPolyline?.points = rulerPoints

            val (distance, bearing) = calculateDistance(rulerPoints[rulerPoints.size - 2], rulerPoints.last())
            binding.rulerInfoTextView.text = "Distancia: %.2f mn, Rumbo: %.2f°".format(distance, bearing)
        }
    }

    private fun clearRuler() {
        rulerPolyline?.remove()
        rulerPolyline = null
        rulerMarkers.forEach { it.remove() }
        rulerMarkers.clear()
        rulerPoints.clear()
        binding.rulerInfoTextView.text = ""
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Pair<Double, Double> {
        val R = 6371 // Radius of the Earth in km
        val latDistance = Math.toRadians(point2.latitude - point1.latitude)
        val lonDistance = Math.toRadians(point2.longitude - point1.longitude)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = R * c * 0.539957 // to nautical miles

        val y = Math.sin(lonDistance) * Math.cos(Math.toRadians(point2.latitude))
        val x = Math.cos(Math.toRadians(point1.latitude)) * Math.sin(Math.toRadians(point2.latitude)) -
                Math.sin(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) * Math.cos(lonDistance)
        val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360

        return Pair(distance, bearing)
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun updateHistoricalMarkers() {
        markerToTrackPointMap.forEach { (marker, trackPoint) ->
            val belongingToCurrentChannel = historicalData[currentChannel]?.contains(trackPoint) == true
            marker.isVisible = belongingToCurrentChannel
        }
    }

    // --- Bluetooth & Data Handling ---
    
    // Bluetooth is now managed by ThingSpeakForegroundService

    override fun onResume() {
        super.onResume()
        GlobalData.addListener(dataListener)
        // Start polling for AIS updates
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        GlobalData.removeListener(dataListener)
        // Stop polling
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    private fun updateAisMarkers(targets: Map<Int, AisTarget>) {
        // Use synchronized copy to avoid ConcurrentModificationException if service writes at same time
        val targetsCopy = synchronized(GlobalData) { HashMap(targets) }
        
        targetsCopy.forEach { (mmsi, target) ->
            val lat = target.latitude
            val lon = target.longitude
            
            if (lat != null && lon != null) {
                val position = LatLng(lat, lon)
                val heading = (target.heading?.toFloat() ?: target.course?.toFloat() ?: 0f)
                val name = target.name ?: "MMSI: $mmsi"
                
                val marker = aisMarkers[mmsi]
                if (marker == null) {
                    // Create new marker
                    val icon = getIcon(name, heading)
                    val newMarker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(name)
                            .icon(icon)
                            .anchor(0.5f, 0.7f)
                    )
                    if (newMarker != null) {
                        newMarker.tag = mmsi // Store MMSI in tag
                        aisMarkers[mmsi] = newMarker
                    }
                } else {
                    // Update existing marker
                    marker.position = position
                    marker.title = name // Update title in case name was resolved later
                    // Only update icon if bucket changed (optimization handled by getIcon cache lookup, 
                    // but setIcon is expensive so maybe check previous heading? 
                    // For now, getIcon is fast, setIcon is the IPC cost. 
                    // Optimization: We could store lastHeading in tag or a wrapper map. 
                    // But getIcon is cached, so creating the descriptor is fast. 
                    // Let's rely on setIcon being relatively optimized by Maps SDK if descriptor object is same reference?
                    // Actually, let's just set it. The throttling to 1s helps the most.
                    marker.setIcon(getIcon(name, heading))
                }
            }
        }
    }

    private fun onGlobalDataUpdated(aggregatedData: NmeaData) {
        // UI logic derived from GlobalData updates (pushed by Service)
        lastBluetoothDataTime = System.currentTimeMillis()
        isBluetoothConnected = true

        // Update UI even if no GPS fix yet (show Compass/Clino data)
        if (currentChannel == LOCAL_CHANNEL_ID) {
            updateUI(
                aggregatedData.latitude?.toString() ?: "0",
                aggregatedData.longitude?.toString() ?: "0",
                aggregatedData.speed?.toString() ?: "0",
                aggregatedData.heading?.toString() ?: "0",
                aggregatedData.pitch?.toString() ?: "0",
                aggregatedData.roll?.toString() ?: "0",
                aggregatedData.rot?.toString() ?: "0"
            )
        }

        if (aggregatedData.latitude != null && aggregatedData.longitude != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            // Check day reset
            val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            if (currentDay != today) {
                // Reset track
                historicalData[LOCAL_CHANNEL_ID]?.clear()
                // Clear markers
                 val markersToRemove = mutableListOf<Marker>()
                markerToTrackPointMap.forEach { (marker, trackPoint) ->
                     if (historicalData[LOCAL_CHANNEL_ID]?.contains(trackPoint) == false) {
                         // Only remove if it was part of the local track
                    }
                }
                historicalData[LOCAL_CHANNEL_ID] = mutableListOf()
                currentDay = today
            }

            val trackPoint = TrackPoint(
                aggregatedData.latitude!!, 
                aggregatedData.longitude!!, 
                aggregatedData.pitch?.toString() ?: "0", 
                aggregatedData.roll?.toString() ?: "0", 
                aggregatedData.speed?.toString() ?: "0", 
                aggregatedData.heading?.toString() ?: "0", 
                timestamp
            )

            // Avoid adding duplicates too frequently? Or just add.
            // For now, simple add.
            historicalData[LOCAL_CHANNEL_ID]?.add(trackPoint)
            
            // Update Track Polyline and Markers
            if (currentChannel == LOCAL_CHANNEL_ID) {
                // Update Track Polyline
                val points = historicalData[LOCAL_CHANNEL_ID]?.map { it.getPosition() } ?: emptyList()
                trackPolylines[LOCAL_CHANNEL_ID]?.points = points
                
                // Move Boat Marker
                val boatMarker = boatMarkers[LOCAL_CHANNEL_ID]
                val rotation = trackPoint.heading.toFloatOrNull() ?: 0f
                val redTriangleBitmap = createOwnShipBitmap(rotation) // Ensure using red triangle same size as AIS
                
                if (boatMarker == null) {
                     val newBoatMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackPoint.getPosition())
                            .icon(BitmapDescriptorFactory.fromBitmap(redTriangleBitmap))
                            // .rotation(rotation) // Rotation baked into bitmap now
                            .anchor(0.5f, 0.5f)
                    )
                    if (newBoatMarker != null) {
                        newBoatMarker.tag = redTriangleBitmap
                        boatMarkers[LOCAL_CHANNEL_ID] = newBoatMarker
                    }
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(trackPoint.getPosition(), 15f))
                } else {
                    boatMarker.position = trackPoint.getPosition()
                    // boatMarker.rotation = rotation // Rotation baked into bitmap now
                    boatMarker.setIcon(BitmapDescriptorFactory.fromBitmap(redTriangleBitmap))
                }
            }
        }
        
        map.setOnMarkerClickListener { marker ->
            // Check for AIS Marker tag (MMSI)
            val mmsi = marker.tag as? Int
            if (mmsi != null) {
                val target = GlobalData.aisTargets[mmsi]
                if (target != null) {
                    val latFormatted = formatCoordinate(target.latitude.toString(), "N", "S")
                    val lonFormatted = formatCoordinate(target.longitude.toString(), "E", "W")
                    
                    val message = "Name: ${target.name ?: "Unknown"}\n" +
                                  "MMSI: ${target.mmsi}\n" +
                                  "Lat: $latFormatted\n" +
                                  "Lon: $lonFormatted\n" +
                                  "Speed: ${target.speed} kn\n" +
                                  "Heading: ${target.heading}°\n" +
                                  "Course: ${target.course}°"
                    AlertDialog.Builder(this)
                        .setTitle("AIS Target")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnMarkerClickListener true
                }
            }
            
            // Check for TrackPoint marker (legacy logic)
            val trackPoint = markerToTrackPointMap[marker]
            if (trackPoint != null) {
                // ... (Existing logic for track points)
            }
            false
        }
    }
}
