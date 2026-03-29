package com.example.segnmea

object GlobalData {
    // Persistent state to hold the latest known values for all fields
    var currentData: NmeaData = NmeaData()
    val aisTargets = mutableMapOf<Int, AisTarget>()

    // Track History Persistence
    val trackHistory = mutableMapOf<String, MutableList<TrackPoint>>()
    var currentTrackDay: String = ""
    
    private val listeners = mutableListOf<(NmeaData) -> Unit>()
    private val aisListeners = mutableListOf<(Map<Int, AisTarget>) -> Unit>()

    @Synchronized
    fun updateAis(target: AisTarget) {
        val existing = aisTargets[target.mmsi]
        if (existing == null) {
            aisTargets[target.mmsi] = target
        } else {
            // Merge fields
            if (target.latitude != null) existing.latitude = target.latitude
            if (target.longitude != null) existing.longitude = target.longitude
            if (target.heading != null) existing.heading = target.heading
            if (target.speed != null) existing.speed = target.speed
            if (target.course != null) existing.course = target.course
            if (target.name != null) existing.name = target.name
            existing.timestamp = target.timestamp
        }
        aisListeners.forEach { it(aisTargets) }
    }
    
    fun addAisListener(listener: (Map<Int, AisTarget>) -> Unit) {
        aisListeners.add(listener)
    }
    
    fun removeAisListener(listener: (Map<Int, AisTarget>) -> Unit) {
        aisListeners.remove(listener)
    }

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
