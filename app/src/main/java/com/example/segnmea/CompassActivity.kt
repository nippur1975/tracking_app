package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.animation.ValueAnimator
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
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
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
            animateHeadingChange(lastHeadingValue, headingValue)
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
     * Animates the heading text change digit by digit.
     */
    private fun animateHeadingChange(start: Float, end: Float) {
        // Handle 0/360 wrap-around if needed, or just standard linear interpolation
        // For simple "digit by digit" counting:

        // If the jump is large (e.g. initialization), just set it?
        // Or if we cross 0/360 boundary?
        // E.g. 350 -> 10. Math.abs is 340. Shortest path is +20.
        // If we want the number to roll 350, 351... 359, 0, 1... 10

        var startVal = start
        var endVal = end

        // Shortest path logic for display
        if (Math.abs(endVal - startVal) > 180) {
            if (endVal > startVal) {
                startVal += 360
            } else {
                endVal += 360
            }
        }

        val animator = ValueAnimator.ofFloat(startVal, endVal)
        animator.duration = 500 // 500ms duration
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            var value = animation.animatedValue as Float
            // Normalize back to 0-360 for display
            value = (value % 360 + 360) % 360
            binding.headingValueTextView.text = "${value.toInt()}°"
        }
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
