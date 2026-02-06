package com.example.segnmea

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.example.segnmea.databinding.ActivityDataBinding

/**
 * Activity that displays the boat's data.
 */
class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            updateUI(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.data)

        // Get the channel ID from the intent
        channel = intent.getStringExtra("channel_id") ?: "3002133"

        // Set up the button click listeners
        binding.mainButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.compassButton.setOnClickListener {
            val intent = Intent(this, CompassActivity::class.java)
            intent.putExtra("channel_id", channel)
            startActivity(intent)
        }
        binding.clinometerButton.setOnClickListener {
            val intent = Intent(this, ClinometerActivity::class.java)
            intent.putExtra("channel_id", channel)
            startActivity(intent)
        }

        // Initialize with current data if available
        GlobalData.currentData?.let { updateUI(it) }
    }

    override fun onResume() {
        super.onResume()
        GlobalData.addListener(dataListener)
    }

    override fun onPause() {
        super.onPause()
        GlobalData.removeListener(dataListener)
    }

    /**
     * Updates UI using Bluetooth data (GlobalData).
     */
    private fun updateUI(data: NmeaData) {
        val pitch = data.pitch?.toFloat() ?: 0f
        val roll = data.roll?.toFloat() ?: 0f
        val lat = data.latitude ?: 0.0
        val lon = data.longitude ?: 0.0
        val speed = data.speed?.toFloat() ?: 0f
        val heading = data.heading?.toFloat() ?: 0f
        val rot = data.rot?.toFloat() ?: 0f
        
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val shipId = sharedPreferences.getString("ship_id", "LalitoTX") ?: "LalitoTX"

        binding.channelNameTextView.text = shipId
        binding.pitchTextView.text = "%.1f°".format(pitch)
        binding.rollTextView.text = "%.1f°".format(roll)
        binding.latTextView.text = formatLat(lat)
        binding.lonTextView.text = formatLon(lon)
        binding.speedTextView.text = "%.1f knots".format(speed)
        binding.headingTextView.text = "${heading.toInt()}°"
        binding.rotTextView.text = "${rot.toInt()}"

        val timestamp = data.timestamp
        if (timestamp != null) {
             binding.realTimeDataTextView.text = "Real-Time Data: $timestamp"
        }
    }

    /**
     * Formats a latitude value to a string with degrees and minutes.
     */
    private fun formatLat(lat: Double): String {
        val hemi = if (lat >= 0) "N" else "S"
        val absLat = kotlin.math.abs(lat)
        val grados = absLat.toInt()
        val minutos = (absLat - grados) * 60
        return String.format(Locale.US, "%02d° %.3f' %s", grados, minutos, hemi)
    }

    /**
     * Formats a longitude value to a string with degrees and minutes.
     */
    private fun formatLon(lon: Double): String {
        val hemi = if (lon >= 0) "E" else "W"
        val absLon = kotlin.math.abs(lon)
        val grados = absLon.toInt()
        val minutos = (absLon - grados) * 60
        return String.format(Locale.US, "%03d° %.3f' %s", grados, minutos, hemi)
    }
}
