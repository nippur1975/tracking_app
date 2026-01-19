package com.example.segnmea

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CompassActivity : AppCompatActivity() {

    private lateinit var headingTextView: TextView
    private lateinit var compassImageView: ImageView

    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            updateUI(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        title = "Compass"

        headingTextView = findViewById(R.id.headingTextView)
        compassImageView = findViewById(R.id.compassImageView)

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

    private fun updateUI(data: NmeaData) {
        val heading = data.heading
        if (heading != null) {
            headingTextView.text = "${heading.toInt()}°"
            compassImageView.rotation = -heading.toFloat()
        } else {
            headingTextView.text = "--"
        }
    }
}
