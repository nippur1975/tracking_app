package com.example.segnmea

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "Settings"

        val apiKeyEditText = findViewById<EditText>(R.id.apiKeyEditText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        apiKeyEditText.setText(sharedPreferences.getString("write_api_key", ""))

        saveButton.setOnClickListener {
            val apiKey = apiKeyEditText.text.toString()
            sharedPreferences.edit().putString("write_api_key", apiKey).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
