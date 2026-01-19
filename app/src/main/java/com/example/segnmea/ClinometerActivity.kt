package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.segnmea.databinding.ActivityClinometerBinding
import java.util.*

/**
 * Activity that displays the clinometer.
 */
class ClinometerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClinometerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"
    private var rollAlarm = 30f  // Solo alarma de roll
    private var mediaPlayer: MediaPlayer? = null
    private var lastAlarmTime = 0L
    private val ALARM_COOLDOWN = 5000L // 5 seconds

    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            updateUI(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClinometerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.clinometer) // Ensure string resource exists or use "Clinometer"

        // Get the channel ID from the intent
        channel = intent.getStringExtra("channel_id") ?: "3002133"
        binding.channelNameTextView.text = "My Boat (Bluetooth)"

        // Set up the button click listeners
        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.compassButton.setOnClickListener {
            val intent = Intent(this, CompassActivity::class.java)
            intent.putExtra("channel_id", channel)
            startActivity(intent)
        }
        binding.dataButton.setOnClickListener {
            val intent = Intent(this, DataActivity::class.java)
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
     * Updates UI with data from Bluetooth (GlobalData).
     * Replaces the original fetchData() which used Volley/ThingSpeak.
     */
    private fun updateUI(data: NmeaData) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        rollAlarm = sharedPreferences.getInt("rollAlarm", 30).toFloat()

        val pitch = data.pitch?.toFloat() ?: 0f
        val roll = data.roll?.toFloat() ?: 0f

        // Update the views
        (binding.pitchImageView as? PitchView)?.pitch = pitch
        (binding.rollImageView as? RollView)?.roll = roll

        checkAlarms(roll)
    }

    /**
     * Checks if the roll value exceeds the alarm threshold and plays an alarm sound if it does.
     */
    private fun checkAlarms(roll: Float) {
        val now = System.currentTimeMillis()
        if (now - lastAlarmTime < ALARM_COOLDOWN) return

        val language = Locale.getDefault().language
        var soundResId = 0

        if (roll > rollAlarm) {
            soundResId = if (language == "es") R.raw.alarma_estribor else R.raw.starboard_alarm
        } else if (roll < -rollAlarm) {
            soundResId = if (language == "es") R.raw.alarma_babor else R.raw.port_alarm
        }

        if (soundResId != 0) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(this, soundResId)
                mediaPlayer?.start()
                lastAlarmTime = now
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
