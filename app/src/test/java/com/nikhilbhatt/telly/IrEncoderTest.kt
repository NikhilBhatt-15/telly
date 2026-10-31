package com.nikhilbhatt.telly

import com.nikhilbhatt.telly.data.IrCode
import org.junit.Assert.assertEquals
import org.junit.Test

class IrEncoderTest {
    /** Decodes the 32 data bits back out of a pattern, least significant bit of each byte first. */
    private fun bytes(pattern: IntArray): List<Int> = (0 until 4).map { byte ->
        (0 until 8).sumOf { bit -> if (pattern[2 + (byte * 8 + bit) * 2 + 1] > 1000) 1 shl bit else 0 }
    }

    @Test
    fun necSendsAddressCommandAndTheirInverses() {
        val p = IrEncoder.pattern(IrCode("NEC", 0x04, 0x08))
        assertEquals(67, p.size)
        assertEquals(listOf(9000, 4500), p.take(2))
        assertEquals(listOf(0x04, 0xFB, 0x08, 0xF7), bytes(p))
    }

    @Test
    fun necExtAndSamsungLayouts() {
        assertEquals(listOf(0x02, 0x7D, 0x46, 0xB9), bytes(IrEncoder.pattern(IrCode("NECext", 0x7D02, 0xB946))))
        val samsung = IrEncoder.pattern(IrCode("Samsung32", 0x07, 0x02))
        assertEquals(4500, samsung[0])
        assertEquals(listOf(0x07, 0x07, 0x02, 0xFD), bytes(samsung))
    }
}
