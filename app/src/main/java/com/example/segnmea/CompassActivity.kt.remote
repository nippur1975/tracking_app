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
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.segnmea.databinding.ActivityCompassBinding
import org.json.JSONObject

/**
 * Activity that displays the compass.
 */
class CompassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompassBinding
    private val handler = Handler(Looper.getMainLooper())
    private var channel = "3002133"

    private var currentRotation = 0f
    private var lastHeadingValue = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.compass)

        // Get the channel ID from the intent
        channel = intent.getStringExtra("channel_id") ?: "3002133"

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

        // Fetch the initial data
        fetchData()
    }

    /**
     * Fetches the compass data from the ThingSpeak API.
     */
    private fun fetchData() {
        val url = "https://api.thingspeak.com/channels/$channel/feeds.json?results=1"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val channelObject = jsonObject.getJSONObject("channel")
                    val channelName = channelObject.getString("name")
                    binding.channelNameTextView.text = channelName

                    val feeds = jsonObject.getJSONArray("feeds")
                    if (feeds.length() > 0) {
                        val lastFeed = feeds.getJSONObject(0)
                        val headingValue = lastFeed.optString("field6", "0").toFloatOrNull() ?: 0f

                        // Animate the heading text if the value has changed
                        if (headingValue != lastHeadingValue) {
                            animateHeadingText("${headingValue.toInt()}°")
                            lastHeadingValue = headingValue
                        }

                        // Rotate the compass
                        rotateCompass(headingValue)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
        handler.postDelayed({ fetchData() }, 15000)
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

    /**
     * Called when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
