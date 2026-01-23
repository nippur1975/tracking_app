package com.example.segnmea

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

class SupabaseReader(private val context: Context) {

    private val queue: RequestQueue = Volley.newRequestQueue(context)

    private fun getConfigs(): Triple<String, String, String> {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val url = prefs.getString("supabase_url", "https://lnxziegzyilfnibmfrtz.supabase.co") ?: ""
        val key = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxueHppZWd6eWlsZm5pYm1mcnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMjI1OTQsImV4cCI6MjA4NDU5ODU5NH0.ltom27lQCmTyI-3NfPW6tMWpEMOL6fXh2dc8ksx0DsQ") ?: ""
        val table = prefs.getString("supabase_table", "nmea_logs") ?: "nmea_logs"
        return Triple(url, key, table)
    }

    fun fetchShipData(shipId: String, callback: (NmeaData?) -> Unit) {
        val (baseUrl, key, table) = getConfigs()
        if (baseUrl.isEmpty() || key.isEmpty()) {
            callback(null)
            return
        }

        // URL encode shipId
        val encodedShipId = try {
            java.net.URLEncoder.encode(shipId, "UTF-8")
        } catch (e: Exception) {
            shipId
        }
        val url = "$baseUrl/rest/v1/$table?ship_id=eq.$encodedShipId&order=date_event.desc&limit=1"

        val request = object : JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                if (response.length() > 0) {
                    try {
                        val obj = response.getJSONObject(0)
                        val data = parseNmeaData(obj)
                        callback(data)
                    } catch (e: Exception) {
                        Log.e("SupabaseReader", "Error parsing ship data", e)
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            },
            { error ->
                Log.e("SupabaseReader", "Error fetching ship data: $error", error)
                callback(null)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["apikey"] = key
                headers["Authorization"] = "Bearer $key"
                return headers
            }
        }
        queue.add(request)
    }

    fun fetchAisData(callback: (List<AisTarget>) -> Unit) {
        val (baseUrl, key, _) = getConfigs()
        if (baseUrl.isEmpty() || key.isEmpty()) {
            callback(emptyList())
            return
        }

        val url = "$baseUrl/rest/v1/ais_live?select=*"

        val request = object : JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                val list = mutableListOf<AisTarget>()
                for (i in 0 until response.length()) {
                    try {
                        val obj = response.getJSONObject(i)
                        list.add(parseAisTarget(obj))
                    } catch (e: Exception) {
                         Log.e("SupabaseReader", "Error parsing AIS item", e)
                    }
                }
                callback(list)
            },
            { error ->
                Log.e("SupabaseReader", "Error fetching AIS: $error", error)
                callback(emptyList())
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["apikey"] = key
                headers["Authorization"] = "Bearer $key"
                return headers
            }
        }
        queue.add(request)
    }

    private fun parseNmeaData(json: JSONObject): NmeaData {
        val data = NmeaData()
        if (!json.isNull("lat")) data.latitude = json.optDouble("lat")
        if (!json.isNull("lon")) data.longitude = json.optDouble("lon")
        if (!json.isNull("velocidad")) data.speed = json.optDouble("velocidad")
        if (!json.isNull("rumbo")) data.heading = json.optDouble("rumbo")
        if (!json.isNull("pitch")) data.pitch = json.optDouble("pitch")
        if (!json.isNull("roll")) data.roll = json.optDouble("roll")
        if (!json.isNull("rot")) data.rot = json.optDouble("rot")
        if (!json.isNull("date_event")) data.timestamp = json.optString("date_event")
        return data
    }

    private fun parseAisTarget(json: JSONObject): AisTarget {
        val mmsi = json.optInt("mmsi")
        val target = AisTarget(mmsi)

        if (!json.isNull("lat")) target.latitude = json.optDouble("lat")
        if (!json.isNull("lon")) target.longitude = json.optDouble("lon")
        if (!json.isNull("sog")) target.speed = json.optDouble("sog")
        if (!json.isNull("cog")) target.course = json.optDouble("cog")
        if (!json.isNull("heading")) target.heading = json.optInt("heading")
        if (!json.isNull("rot")) target.rot = json.optDouble("rot").toFloat()
        if (!json.isNull("ship_name")) target.name = json.optString("ship_name")

        return target
    }
}
