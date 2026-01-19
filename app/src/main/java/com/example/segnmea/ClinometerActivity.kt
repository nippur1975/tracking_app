package com.example.segnmea

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ClinometerActivity : AppCompatActivity() {

    private lateinit var pitchTextView: TextView
    private lateinit var rollTextView: TextView
    private lateinit var horizonLine: View

    private val dataListener: (NmeaData) -> Unit = { data ->
        runOnUiThread {
            updateUI(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clinometer)
        title = "Clinometer"

        pitchTextView = findViewById(R.id.pitchTextView)
        rollTextView = findViewById(R.id.rollTextView)
        horizonLine = findViewById(R.id.artificialHorizonLine)

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
        val pitch = data.pitch
        val roll = data.roll

        if (pitch != null) {
            pitchTextView.text = "${pitch}°"
            // Simple visualization: move line up/down based on pitch
            horizonLine.translationY = (pitch * 5).toFloat()
        } else {
            pitchTextView.text = "--"
        }

        if (roll != null) {
            rollTextView.text = "${roll}°"
            // Simple visualization: rotate line based on roll
            horizonLine.rotation = roll.toFloat()
        } else {
            rollTextView.text = "--"
        }
    }
}
