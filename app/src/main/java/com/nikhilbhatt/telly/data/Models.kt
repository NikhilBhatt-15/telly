package com.nikhilbhatt.telly.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class DeviceKind { Box, Tv }

/** What to do before typing a channel number, so the digits land in the live TV app. */
@Serializable
enum class TunePrefix {
    None,
    LiveTvKey,
    LiveTvApp,

    /** Press Home first. Airtel boxes take channel numbers on their home screen but not inside apps. */
    Home,
}

@Serializable
data class Device(
    val id: String = newId(),
    val name: String,
    val host: String,
    val kind: DeviceKind = DeviceKind.Box,
    val mac: String? = null,
    /** Reports the foreground app; a few TVs show an on-screen keyboard prompt when this is on. */
    val trackApps: Boolean = true,
    val tunePrefix: TunePrefix = if (kind == DeviceKind.Box) TunePrefix.Home else TunePrefix.LiveTvKey,
    val liveTvPackage: String? = null,
    val tuneDelayMs: Int = 1500,
    val digitGapMs: Int = 250,
    val confirmWithOk: Boolean = false,
    /** Device whose power the power button toggles, e.g. the TV a box is plugged into. Null means this one. */
    val powerDeviceId: String? = null,
    /** IR power code for this TV, sent from the phone's IR blaster when the TV can't be reached over Wi-Fi. */
    val irPower: IrCode? = null,
)

/** One IR command in Flipper-IRDB terms: protocol NEC, NECext or Samsung32. */
@Serializable
data class IrCode(val protocol: String, val address: Int, val command: Int) {
    fun label() = "%s %04X/%04X".format(protocol, address, command)
}

@Serializable
data class Channel(
    val id: String = newId(),
    val name: String,
    val number: String,
    /** Absolute path of a picked image, or an http(s) URL. */
    val logo: String? = null,
    val color: Long = 0xFF3B4252,
    val group: String? = null,
)

@Serializable
sealed interface Step {
    @Serializable @SerialName("key")
    data class Key(val code: Int, val long: Boolean = false) : Step

    @Serializable @SerialName("link")
    data class Link(val url: String) : Step

    @Serializable @SerialName("app")
    data class App(val pkg: String) : Step

    @Serializable @SerialName("text")
    data class Text(val text: String) : Step

    @Serializable @SerialName("wait")
    data class Wait(val ms: Int) : Step

    @Serializable @SerialName("channel")
    data class Tune(val number: String) : Step
}

/** An app tile or a multi-step shortcut; both are a named list of steps. */
@Serializable
data class Shortcut(
    val id: String = newId(),
    val name: String,
    val steps: List<Step>,
    val color: Long = 0xFF3B4252,
    val logo: String? = null,
    val isApp: Boolean = false,
    /** Shown in the quick row on the remote screen. */
    val pinned: Boolean = false,
    /** Fetch the real app icon when there's no logo; turned off once the user removes one. */
    val autoLogo: Boolean = true,
)

@Serializable
data class AppData(
    val devices: List<Device> = emptyList(),
    val activeDeviceId: String? = null,
    val channels: List<Channel> = emptyList(),
    val shortcuts: List<Shortcut> = Presets.apps,
    /** Device that channel tiles tune; null means whichever device is active. */
    val channelDeviceId: String? = null,
    val haptics: Boolean = true,
    val volumeKeys: Boolean = true,
    /** Package -> the link that last opened it, tried first next time. */
    val launchLinks: Map<String, String> = emptyMap(),
    /** Bumped when stored data needs migrating; see Store.migrate. */
    val version: Int = 0,
) {
    val activeDevice: Device? get() = devices.firstOrNull { it.id == activeDeviceId } ?: devices.firstOrNull()
    fun device(id: String?) = devices.firstOrNull { it.id == id }
}

fun Step.describe(): String = when (this) {
    is Step.Key -> com.nikhilbhatt.telly.atv.Keys.name(code) + if (long) " (hold)" else ""
    is Step.Link -> "Open $url"
    is Step.App -> "Launch $pkg"
    is Step.Text -> "Type \"$text\""
    is Step.Wait -> "Wait ${ms} ms"
    is Step.Tune -> "Tune channel $number"
}
