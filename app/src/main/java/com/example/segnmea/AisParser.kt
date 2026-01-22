package com.example.segnmea

data class AisTarget(
    val mmsi: Int,
    var name: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var heading: Int? = null,
    var speed: Double? = null,
    var course: Double? = null,
    var rot: Float? = null,
    var timestamp: Long = System.currentTimeMillis()
)

class AisParser {

    private val fragmentMap = mutableMapOf<String, MutableMap<Int, String>>() // msgId -> (fragNum -> payload)

    fun parse(sentence: String): AisTarget? {
        if (!sentence.startsWith("!AIVDM")) return null

        val parts = sentence.split(",")
        if (parts.size < 6) return null

        try {
            val numFragments = parts[1].toInt()
            val fragmentNum = parts[2].toInt()
            val seqId = parts[3].takeIf { it.isNotEmpty() } ?: "0"
            val channel = parts[4]
            val payload = parts[5]

            var fullPayload = payload

            // Handle fragmentation
            if (numFragments > 1) {
                val key = "$seqId-$channel-${parts[1]}" // Unique key for this message sequence
                val fragments = fragmentMap.getOrPut(key) { mutableMapOf() }
                fragments[fragmentNum] = payload

                if (fragments.size == numFragments) {
                    // Reassemble
                    fullPayload = (1..numFragments).joinToString("") { fragments[it] ?: "" }
                    fragmentMap.remove(key)
                } else {
                    return null // Wait for more fragments
                }
            }

            return decodePayload(fullPayload)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun decodePayload(payload: String): AisTarget? {
        val bits = payloadToBits(payload)
        if (bits.length < 6) return null

        val msgType = bits.substring(0, 6).toInt(2)
        val mmsi = bits.substring(8, 38).toInt(2)

        val target = AisTarget(mmsi)

        try {
            when (msgType) {
                1, 2, 3 -> { // Position Report Class A
                    if (bits.length >= 137) {
                        // Nav Status (38, 4)
                        var rotRaw = bits.substring(42, 50).toInt(2)
                        if (rotRaw > 127) rotRaw -= 256 // Signed 8-bit

                        val rot = if (rotRaw == -128) null else {
                            // Standard AIS ROT formula or just raw?
                            // Formula: ROT = 4.733 * sign(rotRaw) * sqrt(|rotRaw|)
                            // For simplicity/display, passing raw or simple conversion is often enough.
                            // Let's pass the raw signed value roughly mapping to deg/min
                            rotRaw.toFloat()
                        }

                        val sog = bits.substring(50, 60).toInt(2) / 10.0
                        val lonRaw = bits.substring(61, 89).toLong(2)
                        val latRaw = bits.substring(89, 116).toLong(2)
                        val cog = bits.substring(116, 128).toInt(2) / 10.0
                        val hdg = bits.substring(128, 137).toInt(2)

                        target.speed = sog
                        target.course = cog
                        target.heading = if (hdg == 511) null else hdg
                        target.rot = rot
                        target.longitude = decodeCoordinate(lonRaw, 28)
                        target.latitude = decodeCoordinate(latRaw, 27)

                        return target
                    }
                }
                18 -> { // Standard Class B CS Position Report
                    if (bits.length >= 133) {
                        val sog = bits.substring(46, 56).toInt(2) / 10.0
                        val lonRaw = bits.substring(57, 85).toLong(2)
                        val latRaw = bits.substring(85, 112).toLong(2)
                        val cog = bits.substring(112, 124).toInt(2) / 10.0
                        val hdg = bits.substring(124, 133).toInt(2)

                        target.speed = sog
                        target.course = cog
                        target.heading = if (hdg == 511) null else hdg
                        target.longitude = decodeCoordinate(lonRaw, 28)
                        target.latitude = decodeCoordinate(latRaw, 27)

                        return target
                    }
                }
                5 -> { // Static and Voyage Related Data
                    if (bits.length >= 424) {
                        // Ship Name: 112, 120 bits (20 chars)
                        val nameBits = bits.substring(112, 112 + 120)
                        target.name = decodeSixBitString(nameBits).trim().replace("@", "")
                        return target
                    }
                }
                24 -> { // Static Data Report
                    val partNum = bits.substring(38, 40).toInt(2)
                    if (partNum == 0 && bits.length >= 160) {
                        val nameBits = bits.substring(40, 160)
                        target.name = decodeSixBitString(nameBits).trim().replace("@", "")
                        return target
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null // Return null if not a position/static report we care about
    }

    private fun decodeCoordinate(raw: Long, bits: Int): Double {
        var value = raw
        val maxVal = 1L shl (bits - 1)
        if (value >= maxVal) {
            value -= (1L shl bits)
        }
        return value / 600000.0
    }

    private fun payloadToBits(payload: String): String {
        val sb = StringBuilder()
        for (char in payload) {
            var value = char.code - 48
            if (value > 40) value -= 8
            val binary = Integer.toBinaryString(value)
            sb.append(binary.padStart(6, '0'))
        }
        return sb.toString()
    }

    private fun decodeSixBitString(bits: String): String {
        val sb = StringBuilder()
        for (i in 0 until bits.length step 6) {
            if (i + 6 <= bits.length) {
                var code = bits.substring(i, i + 6).toInt(2)
                if (code < 32) code += 64
                sb.append(code.toChar())
            }
        }
        return sb.toString()
    }
}
