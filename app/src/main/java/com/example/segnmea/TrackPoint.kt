package com.example.segnmea

import com.google.android.gms.maps.model.LatLng

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val pitch: String,
    val roll: String,
    val speed: String,
    val heading: String,
    val createdAt: String
) {
    fun getPosition(): LatLng {
        return LatLng(lat, lon)
    }
}
