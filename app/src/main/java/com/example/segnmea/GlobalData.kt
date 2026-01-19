package com.example.segnmea

object GlobalData {
    var currentData: NmeaData? = null
    private val listeners = mutableListOf<(NmeaData) -> Unit>()

    fun update(data: NmeaData) {
        currentData = data
        listeners.forEach { it(data) }
    }

    fun addListener(listener: (NmeaData) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (NmeaData) -> Unit) {
        listeners.remove(listener)
    }
}
