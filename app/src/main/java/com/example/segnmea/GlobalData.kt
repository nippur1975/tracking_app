package com.example.segnmea

object GlobalData {
    // Persistent state to hold the latest known values for all fields
    var currentData: NmeaData = NmeaData()
    private val listeners = mutableListOf<(NmeaData) -> Unit>()

    @Synchronized
    fun update(newData: NmeaData) {
        // Merge logic: only update fields that are present in the new data
        if (newData.latitude != null) currentData.latitude = newData.latitude
        if (newData.longitude != null) currentData.longitude = newData.longitude
        if (newData.speed != null) currentData.speed = newData.speed
        if (newData.heading != null) currentData.heading = newData.heading
        if (newData.pitch != null) currentData.pitch = newData.pitch
        if (newData.roll != null) currentData.roll = newData.roll
        if (newData.rot != null) currentData.rot = newData.rot
        if (newData.timestamp != null) currentData.timestamp = newData.timestamp

        listeners.forEach { it(currentData) }
    }

    fun addListener(listener: (NmeaData) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (NmeaData) -> Unit) {
        listeners.remove(listener)
    }
}
