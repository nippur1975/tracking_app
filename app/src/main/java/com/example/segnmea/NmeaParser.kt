package com.example.segnmea

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class NmeaData(
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speed: Double? = null,
    var heading: Double? = null,
    var pitch: Double? = null,
    var roll: Double? = null,
    var rot: Double? = null,
    var timestamp: String? = null
)

class NmeaParser {

    fun parse(sentence: String): NmeaData {
        val data = NmeaData()
        if (!sentence.startsWith("$")) return data
        
        // Remove checksum if present
        val cleanSentence = if (sentence.contains("*")) sentence.split("*")[0] else sentence
        val parts = cleanSentence.split(",")

        try {
            when (parts[0]) {
                "\$GPRMC", "\$GNRMC" -> parseRMC(parts, data)
                "\$GPGGA", "\$GNGGA" -> parseGGA(parts, data)
                "\$GPGLL", "\$GNGLL" -> parseGLL(parts, data)
                "\$GPZDA", "\$GNZDA" -> parseZDA(parts, data)
                "\$GPVTG", "\$GNVTG" -> parseVTG(parts, data)
                "\$HCHDG", "\$HCHDT", "\$GPHDT" -> parseHeading(parts, data)
                "\$GPROT" -> parseROT(parts, data)
                "\$IIXDR" -> parseXDR(parts, data)
                "\$PFEC" -> if (parts.size > 1 && parts[1] == "GPatt") parseGPatt(parts, data)
                // Custom formats or others can be added here
            }
            
            // Fallback/Custom parsing for Pitch/Roll if not standard
            if ((data.pitch == null || data.roll == null) && parts[0] != "\$PFEC") {
                 parseCustomPitchRoll(cleanSentence, data)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return data
    }

    private fun parseRMC(parts: List<String>, data: NmeaData) {
        // $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // 1: Time, 2: Status, 3: Lat, 4: N/S, 5: Lon, 6: E/W, 7: Speed, 8: Track, 9: Date
        if (parts.size > 9 && parts[2] == "A") {
            data.latitude = convertToDecimal(parts[3], parts[4])
            data.longitude = convertToDecimal(parts[5], parts[6])
            data.speed = parts[7].toDoubleOrNull()
            data.heading = parts[8].toDoubleOrNull()
            
            val time = parts[1]
            val date = parts[9]
            if (time.length >= 6 && date.length == 6) {
                try {
                     val day = date.substring(0, 2)
                     val month = date.substring(2, 4)
                     val year = "20" + date.substring(4, 6)
                     val hour = time.substring(0, 2)
                     val min = time.substring(2, 4)
                     val sec = time.substring(4, 6)
                     data.timestamp = "$year-$month-${day}T$hour:$min:${sec}Z"
                } catch (e: Exception) {}
            }
        }
    }

    private fun parseGGA(parts: List<String>, data: NmeaData) {
        // $GPGGA,232310,3544.0987,N,13521.4056,E,8,10,1.6,44,M,,M,,*7F
        // 1: Time, 2: Lat, 3: N/S, 4: Lon, 5: E/W
        if (parts.size > 5) {
             val lat = convertToDecimal(parts[2], parts[3])
             val lon = convertToDecimal(parts[4], parts[5])
             if (lat != 0.0 || lon != 0.0) {
                 data.latitude = lat
                 data.longitude = lon
             }
        }
    }

    private fun parseGLL(parts: List<String>, data: NmeaData) {
        // $GPGLL,3544.1019,N,13521.4064,E,232309,A,S*51
        // 1: Lat, 2: N/S, 3: Lon, 4: E/W, 5: Time, 6: Status
        if (parts.size > 6 && (parts[6] == "A" || parts[6] == "V")) { // Some GLL might use A for Valid
             val lat = convertToDecimal(parts[1], parts[2])
             val lon = convertToDecimal(parts[3], parts[4])
             if (lat != 0.0 || lon != 0.0) {
                 data.latitude = lat
                 data.longitude = lon
             }
        }
    }

    private fun parseZDA(parts: List<String>, data: NmeaData) {
        // $GPZDA,232310,11,01,2006,00,00*4C
        // 1: Time (HHMMSS.ss), 2: Day, 3: Month, 4: Year, 5: Local zone hour, 6: Local zone min
        if (parts.size > 4) {
            val time = parts[1]
            val day = parts[2]
            val month = parts[3]
            val year = parts[4]
            
            if (time.length >= 6) {
                try {
                     val hour = time.substring(0, 2)
                     val min = time.substring(2, 4)
                     val sec = time.substring(4, 6)
                     data.timestamp = "$year-$month-${day}T$hour:$min:${sec}Z"
                } catch (e: Exception) {}
            }
        }
    }

    private fun parseVTG(parts: List<String>, data: NmeaData) {
        // $GPVTG,120.8,T,120.9,M,0.0,N,0.0,K,D*27
        // Track True: 1, T
        // Track Mag: 3, M
        // Speed Knots: 5, N
        // Speed KPH: 7, K
        // Look for 'N' to find Knots value before it
        for (i in 1 until parts.size step 2) {
            if (i + 1 < parts.size) {
                if (parts[i+1] == "N") {
                     data.speed = parts[i].toDoubleOrNull()
                }
            }
        }
    }

    private fun parseHeading(parts: List<String>, data: NmeaData) {
         // $GPHDT,186.8,T*32
         if (parts.size > 1) {
             data.heading = parts[1].toDoubleOrNull()
         }
    }

    private fun parseROT(parts: List<String>, data: NmeaData) {
        // $GPROT,-57.3,A*2D
        if (parts.size > 1) {
            data.rot = parts[1].toDoubleOrNull()
        }
    }

    private fun parseGPatt(parts: List<String>, data: NmeaData) {
        // $PFEC,GPatt,187.1,+12.0,-25.0*45
        // 2: Heading, 3: Pitch, 4: Roll
        if (parts.size > 4) {
            data.heading = parts[2].toDoubleOrNull()
            data.pitch = parts[3].toDoubleOrNull()
            data.roll = parts[4].toDoubleOrNull()
        }
    }

    private fun parseXDR(parts: List<String>, data: NmeaData) {
        // $IIXDR,A,10.5,D,PITCH,A,-2.3,D,ROLL
        for (i in 0 until parts.size - 3 step 4) {
             if (i + 4 < parts.size) {
                 val name = parts[i + 4]
                 val value = parts[i + 2].toDoubleOrNull()
                 if (name.contains("PITCH", ignoreCase = true)) {
                     data.pitch = value
                 } else if (name.contains("ROLL", ignoreCase = true)) {
                     data.roll = value
                 }
             }
        }
    }
    
    private fun parseCustomPitchRoll(sentence: String, data: NmeaData) {
        val pitchRegex = Regex("PITCH[:= ]?([0-9.-]+)", RegexOption.IGNORE_CASE)
        val rollRegex = Regex("ROLL[:= ]?([0-9.-]+)", RegexOption.IGNORE_CASE)
        
        pitchRegex.find(sentence)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
            data.pitch = it
        }
        rollRegex.find(sentence)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
            data.roll = it
        }
    }

    private fun convertToDecimal(coordinate: String, direction: String): Double {
        if (coordinate.isEmpty()) return 0.0
        try {
            // format ddmm.mmmm
            val decimalPointIndex = coordinate.indexOf('.')
            if (decimalPointIndex == -1 || decimalPointIndex < 2) return 0.0
            
            val degreesStr = coordinate.substring(0, decimalPointIndex - 2)
            val minutesStr = coordinate.substring(decimalPointIndex - 2)
            
            val degrees = degreesStr.toDouble()
            val minutes = minutesStr.toDouble()
            
            var decimal = degrees + (minutes / 60.0)
            if (direction == "S" || direction == "W") {
                decimal = -decimal
            }
            return decimal
        } catch (e: Exception) {
            return 0.0
        }
    }
}
