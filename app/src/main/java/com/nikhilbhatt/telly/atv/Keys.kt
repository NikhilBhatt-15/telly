package com.nikhilbhatt.telly.atv

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Android key codes, which are also the RemoteKeyCode values the TV expects. */
object Keys {
    const val HOME = 3
    const val BACK = 4
    const val DIGIT_0 = 7
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val PLAY_PAUSE = 85
    const val STOP = 86
    const val NEXT = 87
    const val PREVIOUS = 88
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val TV = 170
    const val GUIDE = 172
    const val CAPTIONS = 175
    const val SETTINGS = 176
    const val INPUT = 178
    const val RED = 183
    const val GREEN = 184
    const val YELLOW = 185
    const val BLUE = 186
    const val LAST_CHANNEL = 229
    const val ALL_APPS = 284

    fun digit(d: Int) = DIGIT_0 + d

    /** Keys offered when building a shortcut, with friendly names. */
    val named: List<Pair<Int, String>> = listOf(
        POWER to "Power", HOME to "Home", BACK to "Back", OK to "OK",
        UP to "Up", DOWN to "Down", LEFT to "Left", RIGHT to "Right",
        VOLUME_UP to "Volume up", VOLUME_DOWN to "Volume down", MUTE to "Mute",
        CHANNEL_UP to "Channel up", CHANNEL_DOWN to "Channel down", LAST_CHANNEL to "Last channel",
        PLAY_PAUSE to "Play / Pause", STOP to "Stop", REWIND to "Rewind", FAST_FORWARD to "Fast forward",
        NEXT to "Next", PREVIOUS to "Previous", MENU to "Menu", SETTINGS to "Settings", INFO to "Info",
        GUIDE to "Guide", TV to "Live TV", INPUT to "Input / Source", CAPTIONS to "Captions",
        SEARCH to "Search", ALL_APPS to "All apps", ENTER to "Enter", DEL to "Delete",
        RED to "Red", GREEN to "Green", YELLOW to "Yellow", BLUE to "Blue",
    ) + (0..9).map { digit(it) to "Digit $it" }

    fun name(code: Int) = named.firstOrNull { it.first == code }?.second ?: "Key $code"
}

object WakeOnLan {
    /** Broadcasts a magic packet. Only helps TVs that keep their network card powered in standby. */
    fun send(mac: String) {
        val macBytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(macBytes.size == 6) { "Bad MAC address" }
        val payload = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { macBytes }.toByteArray()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (port in intArrayOf(9, 7)) {
                socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), port))
            }
        }
    }
}
