package com.nikhilbhatt.telly

import android.content.Context
import android.hardware.ConsumerIrManager
import com.nikhilbhatt.telly.data.IrCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The phone's IR blaster, for the one thing Wi-Fi can't do: turning on a TV that lost power, whose
 * network is off until it's switched on the old-fashioned way.
 */
class IrBlaster(private val context: Context) {
    private val manager: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)

    val available: Boolean get() = manager?.hasIrEmitter() == true

    /** TV power codes from Flipper-IRDB (CC0), most widely shared first. */
    val powerCodes: List<IrCode> by lazy {
        val text = context.assets.open("ir_power_codes.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonArray.map { e ->
            val o = e.jsonObject
            IrCode(o.getValue("p").jsonPrimitive.content, o.getValue("a").jsonPrimitive.int, o.getValue("c").jsonPrimitive.int)
        }
    }

    fun send(code: IrCode): Boolean {
        val m = manager ?: return false
        if (!m.hasIrEmitter()) return false
        return runCatching { m.transmit(CARRIER_HZ, IrEncoder.pattern(code)) }.isSuccess
    }

    companion object {
        private const val CARRIER_HZ = 38_000
    }
}

/** Builds on/off timings in microseconds for the NEC family, which almost every TV remote uses. */
object IrEncoder {
    private const val UNIT = 560
    private const val ONE_GAP = 1690

    fun pattern(code: IrCode): IntArray {
        val a = code.address
        val c = code.command
        val (leaderOn, bytes) = when (code.protocol) {
            "NEC" -> 9000 to intArrayOf(a and 0xFF, a.inv() and 0xFF, c and 0xFF, c.inv() and 0xFF)
            "NECext" -> 9000 to intArrayOf(a and 0xFF, (a shr 8) and 0xFF, c and 0xFF, (c shr 8) and 0xFF)
            "Samsung32" -> 4500 to intArrayOf(a and 0xFF, a and 0xFF, c and 0xFF, c.inv() and 0xFF)
            else -> error("Unsupported IR protocol ${code.protocol}")
        }
        val out = ArrayList<Int>(68)
        out += leaderOn
        out += 4500
        for (b in bytes) {
            for (bit in 0 until 8) { // least significant bit first
                out += UNIT
                out += if ((b shr bit) and 1 == 1) ONE_GAP else UNIT
            }
        }
        out += UNIT
        return out.toIntArray()
    }
}
