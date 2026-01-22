package com.example.segnmea

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var shipIdEditText: EditText
    private lateinit var supabaseUrlEditText: EditText
    private lateinit var supabaseKeyEditText: EditText
    private lateinit var supabaseTableEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        shipIdEditText = findViewById(R.id.shipIdEditText)
        supabaseUrlEditText = findViewById(R.id.supabaseUrlEditText)
        supabaseKeyEditText = findViewById(R.id.supabaseKeyEditText)
        supabaseTableEditText = findViewById(R.id.supabaseTableEditText)
        saveButton = findViewById(R.id.saveButton)

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // Load with defaults provided by the user
        val shipId = sharedPreferences.getString("ship_id", "LalitoTX")
        val url = sharedPreferences.getString("supabase_url", "https://lnxziegzyilfnibmfrtz.supabase.co")
        val key = sharedPreferences.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxueHppZWd6eWlsZm5pYm1mcnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMjI1OTQsImV4cCI6MjA4NDU5ODU5NH0.ltom27lQCmTyI-3NfPW6tMWpEMOL6fXh2dc8ksx0DsQ")
        val table = sharedPreferences.getString("supabase_table", "nmea_logs")

        shipIdEditText.setText(shipId)
        supabaseUrlEditText.setText(url)
        supabaseKeyEditText.setText(key)
        supabaseTableEditText.setText(table)
    }

    private fun saveSettings() {
        val shipId = shipIdEditText.text.toString().trim()
        val url = supabaseUrlEditText.text.toString().trim()
        val key = supabaseKeyEditText.text.toString().trim()
        val table = supabaseTableEditText.text.toString().trim()

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "URL and API Key are required", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("ship_id", shipId)
        editor.putString("supabase_url", url)
        editor.putString("supabase_key", key)
        editor.putString("supabase_table", table)
        editor.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish() // Close the activity
    }
}
