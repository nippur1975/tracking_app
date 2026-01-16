package com.example.segnmea

import org.junit.Test
import org.junit.Assert.*

class NmeaParserTest {

    private val parser = NmeaParser()

    @Test
    fun testParseGPRMC() {
        val sentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val data = parser.parse(sentence)

        assertEquals(48.1173, data.latitude!!, 0.0001)
        assertEquals(11.5166, data.longitude!!, 0.0001)
        assertEquals(22.4, data.speed!!, 0.1)
        assertEquals(84.4, data.heading!!, 0.1)
    }

    @Test
    fun testParseGPGLL() {
        val sentence = "\$GPGLL,3544.1019,N,13521.4064,E,232309,A,S*51"
        val data = parser.parse(sentence)
        // 35 deg 44.1019 min = 35 + 0.7350316 = 35.73503
        // 135 deg 21.4064 min = 135 + 0.356773 = 135.35677
        assertEquals(35.7350, data.latitude!!, 0.0001)
        assertEquals(135.3567, data.longitude!!, 0.0001)
    }

    @Test
    fun testParseGPHDT() {
        val sentence = "\$GPHDT,186.8,T*32"
        val data = parser.parse(sentence)
        assertEquals(186.8, data.heading!!, 0.1)
    }

    @Test
    fun testParseGPZDA() {
        val sentence = "\$GPZDA,232310,11,01,2006,00,00*4C"
        val data = parser.parse(sentence)
        assertEquals("2006-01-11T23:23:10Z", data.timestamp)
    }

    @Test
    fun testParseGPVTG() {
        val sentence = "\$GPVTG,120.8,T,120.9,M,0.0,N,0.0,K,D*27"
        val data = parser.parse(sentence)
        assertEquals(0.0, data.speed!!, 0.1)

        val sentence2 = "\$GPVTG,186.1,T,186.2,M,6.5,N,12.0,K,S*02"
        val data2 = parser.parse(sentence2)
        assertEquals(6.5, data2.speed!!, 0.1)
    }

    @Test
    fun testParseGPatt() {
        val sentence = "\$PFEC,GPatt,187.1,+12.0,-25.0*45"
        val data = parser.parse(sentence)
        assertEquals(187.1, data.heading!!, 0.1)
        assertEquals(12.0, data.pitch!!, 0.1)
        assertEquals(-25.0, data.roll!!, 0.1)
    }
}
