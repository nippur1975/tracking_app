package com.example.segnmea

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var channelIdEditText: EditText
    private lateinit var writeApiKeyEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        channelIdEditText = findViewById(R.id.channelIdEditText)
        writeApiKeyEditText = findViewById(R.id.writeApiKeyEditText)
        saveButton = findViewById(R.id.saveButton)

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // Load with defaults provided by the user
        val channelId = sharedPreferences.getString("channel_id", "3097347")
        val writeApiKey = sharedPreferences.getString("write_api_key", "A9UJBBGR06NP852V")

        channelIdEditText.setText(channelId)
        writeApiKeyEditText.setText(writeApiKey)
    }

    private fun saveSettings() {
        val channelId = channelIdEditText.text.toString().trim()
        val writeApiKey = writeApiKeyEditText.text.toString().trim()

        if (channelId.isEmpty() || writeApiKey.isEmpty()) {
            Toast.makeText(this, "Channel ID and Write API Key are required", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("channel_id", channelId)
        editor.putString("write_api_key", writeApiKey)
        editor.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish() // Close the activity
    }
}
