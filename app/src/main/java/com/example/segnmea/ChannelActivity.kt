package com.example.segnmea

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.segnmea.databinding.ActivityChannelBinding

class ChannelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChannelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.channel)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // Cargar valores actuales de los canales
        binding.channel1EditText.setText(sharedPreferences.getString("channel1", "3002133"))
        binding.channel2EditText.setText(sharedPreferences.getString("channel2", "3007462"))
        binding.channel3EditText.setText(sharedPreferences.getString("channel3", "3017966"))
        binding.channel4EditText.setText(sharedPreferences.getString("channel4", "3017982"))
        binding.myChannelIdEditText.setText(sharedPreferences.getString("my_channel_id", ""))
        binding.writeApiKeyEditText.setText(sharedPreferences.getString("write_api_key", ""))

        // Guardar cambios al presionar "Guardar"
        binding.saveButton.setOnClickListener {
            val editor = sharedPreferences.edit()
            editor.putString("channel1", binding.channel1EditText.text.toString().trim())
            editor.putString("channel2", binding.channel2EditText.text.toString().trim())
            editor.putString("channel3", binding.channel3EditText.text.toString().trim())
            editor.putString("channel4", binding.channel4EditText.text.toString().trim())
            editor.putString("my_channel_id", binding.myChannelIdEditText.text.toString().trim())
            editor.putString("write_api_key", binding.writeApiKeyEditText.text.toString().trim())
            editor.apply()

            // Volver a MainActivity y refrescar los datos
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
