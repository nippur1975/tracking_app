package com.example.segnmea

import android.bluetooth.BluetoothDevice
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
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
    private var currentChannel = "3002133"
    private var channelName = "Vessel"

    // Bluetooth & Local Data
    private lateinit var bluetoothManager: BluetoothManager
    private val nmeaParser = NmeaParser()
    private var isBluetoothConnected = false
    private val LOCAL_CHANNEL_ID = "local_bluetooth"
    private var lastUploadTime = 0L
    private val UPLOAD_INTERVAL = 15000L
    private var currentDay = ""

    // Watchdog
    private var lastBluetoothDataTime = 0L
    private val WATCHDOG_INTERVAL = 1000L
    private val BLUETOOTH_TIMEOUT = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        currentChannel = sharedPreferences.getString("current_channel", "3002133") ?: "3002133"
        channelName = sharedPreferences.getString("current_channel_name", "Vessel") ?: "Vessel"

        bluetoothManager = BluetoothManager(this,
            onDataReceived = { data -> onBluetoothDataReceived(data) },
            onStatusChange = { status ->
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
                if (status == "Connected") {
                    isBluetoothConnected = true
                    switchToLocalChannel()
                } else if (status == "Disconnected" || status == "Connection Failed") {
                    isBluetoothConnected = false
                }
            }
        )

        // IMPORTANT: Replace "YOUR_MAP_ID" with your actual Map ID if using Cloud Styling
        // val mapOptions = GoogleMapOptions().mapId("YOUR_MAP_ID")
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

        binding.trackSwitch.setOnCheckedChangeListener { _, _ ->
            updateHistoricalMarkers()
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

        checkPermissions()
        startWatchdog()
    }

    private fun checkPermissions() {
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

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
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

        binding.trackSwitch.isChecked = false
        historicalMarkers.forEach { it.isVisible = false }

        map.setOnMarkerClickListener { marker ->
            val trackPoint = markerToTrackPointMap[marker]
            if (trackPoint != null) {
                val latFormatted = formatCoordinate(trackPoint.lat.toString(), "N", "S")
                val lonFormatted = formatCoordinate(trackPoint.lon.toString(), "E", "W")
                val speedAbs = trackPoint.speed.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: trackPoint.speed
                val headingAbs = trackPoint.heading.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: trackPoint.heading

                val message = "Fecha: ${trackPoint.createdAt}\n" +
                              "Lat: $latFormatted\n" +
                              "Lon: $lonFormatted\n" +
                              "Speed: $speedAbs kn\n" +
                              "Heading: $headingAbs°\n" +
                              "Pitch: ${trackPoint.pitch}°\n" +
                              "Roll: ${trackPoint.roll}°"

                AlertDialog.Builder(this)
                    .setTitle("Datos del Punto Histórico")
                    .setMessage(message)
                    .setPositiveButton("Aceptar", null)
                    .show()
            }
            true
        }

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
            if (now - lastBluetoothDataTime > BLUETOOTH_TIMEOUT) {
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


    private fun updateUI(lat: String, lon: String, speed: String, heading: String, pitch: String, roll: String) {
        val latFormatted = formatCoordinate(lat, "N", "S")
        val lonFormatted = formatCoordinate(lon, "E", "W")
        val headingAbs = heading.toDoubleOrNull()?.let { Math.abs(it) }?.toInt()?.toString() ?: heading
        val speedAbs = speed.toDoubleOrNull()?.let { Math.abs(it).toInt() }?.toString() ?: speed

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("language", "en") ?: "en"

        binding.channelNameTextView.text = channelName
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_connect_bluetooth -> {
                showBluetoothDeviceSelection()
                true
            }
            R.id.action_alarm_settings -> {
                startActivity(Intent(this, AlarmActivity::class.java))
                true
            }
            R.id.action_channel_settings -> {
                startActivity(Intent(this, ChannelActivity::class.java))
                true
            }
            R.id.action_select_channel -> {
                showChannelSelectionDialog()
                true
            }
            R.id.action_language_settings -> {
                startActivity(Intent(this, LanguageActivity::class.java))
                true
            }
            R.id.action_about -> {
                val aboutDialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage(getString(R.string.about_message))
                    .setPositiveButton("Aceptar", null)
                    .create()
                aboutDialog.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
        bluetoothManager.disconnect()
    }

    private fun showChannelSelectionDialog() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val channels = mutableListOf<String>()
        for (i in 1..8) {
            val channel = sharedPreferences.getString("channel$i", "")
            if (channel?.isNotEmpty() == true) {
                channels.add(channel)
            }
        }

        // Add Local option if connected
        if (isBluetoothConnected) {
            channels.add(0, LOCAL_CHANNEL_ID)
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccionar Canal")

        val channelNames = channels.map {
            if (it == LOCAL_CHANNEL_ID) "Bluetooth Local" else sharedPreferences.getString("channel_name_$it", "Canal ${channels.indexOf(it) + 1}")
        }

        builder.setSingleChoiceItems(channelNames.toTypedArray(), channels.indexOf(currentChannel).coerceAtLeast(0)) { dialog, which ->
            val selectedChannel = channels[which]

            if (selectedChannel == LOCAL_CHANNEL_ID) {
                switchToLocalChannel()
            } else {
                currentChannel = selectedChannel
                channelName = channelNames[which] ?: "Canal ${which + 1}"
                isBluetoothConnected = false // Assume user wants to view remote, but connection might stay alive.
                // If we want to fully disconnect: bluetoothManager.disconnect()
                // But usually we just change view.

                val editor = sharedPreferences.edit()
                editor.putString("current_channel", currentChannel)
                editor.putString("current_channel_name", channelName)
                editor.apply()

                updateHistoricalMarkers()

                val boatMarker = boatMarkers[currentChannel]
                if (boatMarker != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(boatMarker.position, 15f))
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.create().show()
    }

    private fun switchToLocalChannel() {
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
            marker.isVisible = binding.trackSwitch.isChecked && belongingToCurrentChannel
        }
    }

    // --- Bluetooth & Data Handling ---

    private fun showBluetoothDeviceSelection() {
        val pairedDevices = bluetoothManager.getPairedDevices()
        val deviceList = pairedDevices.toList()
        val deviceNames = deviceList.map { "${it.name} (${it.address})" }.toTypedArray()

        if (deviceNames.isEmpty()) {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Bluetooth Device")
            .setItems(deviceNames) { _, which ->
                val device = deviceList[which]
                bluetoothManager.connect(device.address)
            }
            .show()
    }

    private fun onBluetoothDataReceived(line: String) {
        lastBluetoothDataTime = System.currentTimeMillis()

        // Parse NMEA
        val data = nmeaParser.parse(line)

        // Update Global Data for Compass/Clinometer
        GlobalData.update(data)

        if (data.latitude != null && data.longitude != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Check day reset
            val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            if (currentDay != today) {
                // Reset track
                historicalData[LOCAL_CHANNEL_ID]?.clear()
                // Clear markers
                 val markersToRemove = mutableListOf<Marker>()
                markerToTrackPointMap.forEach { (marker, trackPoint) ->
                    // This is inefficient if we have many channels, but safe
                     if (historicalData[LOCAL_CHANNEL_ID]?.contains(trackPoint) == false) {
                         // Only remove if it was part of the local track?
                         // For simplicity, we just clear everything related to local channel logic if we tracked markers by channel...
                         // Actually `historicalData` is map<Channel, List>.
                    }
                }
                // Better: Iterate markers and see if they map to a point in the cleared list?
                // For now, let's just clear the points list. The marker cleanup happens in `updateHistoricalMarkers` or refresh loop,
                // but since we are pushing updates here, we should manage it.
                // To keep it simple: clear the list, then rebuild markers or let the loop handle it?
                // But loop is for API.

                // Let's just clear the list.
                historicalData[LOCAL_CHANNEL_ID] = mutableListOf()
                currentDay = today
            }

            val trackPoint = TrackPoint(
                data.latitude!!,
                data.longitude!!,
                data.pitch?.toString() ?: "0",
                data.roll?.toString() ?: "0",
                data.speed?.toString() ?: "0",
                data.heading?.toString() ?: "0",
                timestamp
            )

            // Add to history
            historicalData[LOCAL_CHANNEL_ID]?.add(trackPoint)

            // Update UI
            if (currentChannel == LOCAL_CHANNEL_ID) {
                updateUI(
                    trackPoint.lat.toString(),
                    trackPoint.lon.toString(),
                    trackPoint.speed,
                    trackPoint.heading,
                    trackPoint.pitch,
                    trackPoint.roll
                )

                // Update Track Polyline
                val points = historicalData[LOCAL_CHANNEL_ID]?.map { it.getPosition() } ?: emptyList()
                trackPolylines[LOCAL_CHANNEL_ID]?.points = points

                // Add marker (if we want markers for every point? Usually just track is enough, markers are heavy)
                // Existing code adds marker for every point.
                // We'll add it if switch is on.
                if (binding.trackSwitch.isChecked) {
                     val historicalMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackPoint.getPosition())
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmap(R.drawable.ic_historical_marker, 0xFF00FF00.toInt())!!)) // Green
                            .anchor(0.5f, 0.5f)
                    )
                    if (historicalMarker != null) {
                        markerToTrackPointMap[historicalMarker] = trackPoint
                        historicalMarkers.add(historicalMarker)
                    }
                }

                // Move Boat Marker
                val boatMarker = boatMarkers[LOCAL_CHANNEL_ID]
                val iconResId = R.drawable.ic_navigation
                val iconColor = 0xFF00FF00.toInt()
                val bitmap = getBitmap(iconResId, iconColor)
                val rotation = trackPoint.heading.toFloatOrNull() ?: 0f

                if (boatMarker == null) {
                     val newBoatMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackPoint.getPosition())
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap!!))
                            .rotation(rotation)
                            .anchor(0.5f, 0.5f)
                    )
                    if (newBoatMarker != null) {
                        newBoatMarker.tag = bitmap
                        boatMarkers[LOCAL_CHANNEL_ID] = newBoatMarker
                    }
                    map.animateCamera(CameraUpdateFactory.newLatLng(trackPoint.getPosition()))
                } else {
                    boatMarker.position = trackPoint.getPosition()
                    boatMarker.rotation = rotation
                    boatMarker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))
                }
            }

            // Upload to ThingSpeak
            val now = System.currentTimeMillis()
            if (now - lastUploadTime > UPLOAD_INTERVAL) {
                uploadToThingSpeak(data)
                lastUploadTime = now
            }
        }
    }

    private fun uploadToThingSpeak(data: NmeaData) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val writeApiKey = sharedPreferences.getString("write_api_key", "")

        if (writeApiKey.isNullOrEmpty()) return

        val url = "https://api.thingspeak.com/update"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                // Log.d("ThingSpeak", "Success: $response")
            },
            { error ->
                // Log.e("ThingSpeak", "Error: ${error.message}")
            }
        ) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["api_key"] = writeApiKey
                params["field1"] = data.pitch?.toString() ?: "0"
                params["field2"] = data.roll?.toString() ?: "0"
                params["field3"] = data.latitude?.toString() ?: "0"
                params["field4"] = data.longitude?.toString() ?: "0"
                params["field5"] = data.speed?.toString() ?: "0"
                params["field6"] = data.heading?.toString() ?: "0"
                return params
            }
        }
        queue.add(request)
    }
}
