
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

fun parse(sentence: String): NmeaData {
        val data = NmeaData()
        if (!sentence.startsWith("$")) return data

        // Remove checksum if present
        val cleanSentence = if (sentence.contains("*")) sentence.split("*")[0] else sentence
        val parts = cleanSentence.split(",")

        try {
            when (parts[0]) {
                "\$PFEC" -> if (parts.size > 1 && parts[1] == "GPatt") parseGPatt(parts, data)
                "\$GPHDT" -> parseHeading(parts, data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return data
}

fun parseGPatt(parts: List<String>, data: NmeaData) {
    if (parts.size > 4) {
        data.heading = parts[2].toDoubleOrNull()
        data.pitch = parts[3].toDoubleOrNull()
        data.roll = parts[4].toDoubleOrNull()
    }
}

fun parseHeading(parts: List<String>, data: NmeaData) {
     if (parts.size > 1) {
         data.heading = parts[1].toDoubleOrNull()
     }
}

fun main() {
    val s1 = "\$PFEC,GPatt,187.1,+12.0,-25.0*45"
    val d1 = parse(s1)
    println("S1 -> H:${d1.heading} P:${d1.pitch} R:${d1.roll}")

    val s2 = "\$GPHDT,186.8,T*32"
    val d2 = parse(s2)
    println("S2 -> H:${d2.heading}")
}
