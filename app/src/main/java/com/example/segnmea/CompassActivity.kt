package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.segnmea.databinding.ActivityCompassBinding

/**
 * Activity that displays the compass.
 */
class CompassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompassBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    private var currentRotation = 0f
    private var lastHeadingValue = -1f

    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            updateUI(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.compass) // Make sure string resource exists or use "Compass"

        // Get the channel ID from the intent (kept for compatibility with user logic, though not used for fetch)
        channel = intent.getStringExtra("channel_id") ?: "3002133"
        binding.channelNameTextView.text = "My Boat (Bluetooth)" // Fixed name for Bluetooth mode

        // Set up the button click listeners
        binding.mainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.clinometerButton.setOnClickListener {
            val intent = Intent(this, ClinometerActivity::class.java)
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
        val headingValue = data.heading?.toFloat() ?: 0f

        // Animate the heading text if the value has changed
        if (headingValue != lastHeadingValue) {
            animateHeadingText("${headingValue.toInt()}°")
            lastHeadingValue = headingValue
        }

        // Rotate the compass
        rotateCompass(headingValue)
    }

    /**
     * Rotates the compass to the specified rotation.
     */
    private fun rotateCompass(targetRotation: Float) {
        (binding.compassRose as? CompassView)?.compassRotation = targetRotation
    }

    /**
     * Animates the heading text.
     */
    private fun animateHeadingText(newText: String) {
        // Combined animation: scale + opacity
        val scaleUpX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f, 1f)
        val scaleUpY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f, 1f)
        val fade = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)

        binding.headingValueTextView.text = newText
        ObjectAnimator.ofPropertyValuesHolder(binding.headingValueTextView, scaleUpX, scaleUpY, fade).apply {
            duration = 500 // half a second
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
