package com.nikhilbhatt.telly

import com.nikhilbhatt.telly.atv.KeyDirection
import com.nikhilbhatt.telly.atv.Keys
import com.nikhilbhatt.telly.atv.LinkStatus
import com.nikhilbhatt.telly.atv.LinkTarget
import com.nikhilbhatt.telly.atv.TvLink
import com.nikhilbhatt.telly.atv.WakeOnLan
import com.nikhilbhatt.telly.data.Channel
import com.nikhilbhatt.telly.data.Device
import com.nikhilbhatt.telly.data.Presets
import com.nikhilbhatt.telly.data.Shortcut
import com.nikhilbhatt.telly.data.Step
import com.nikhilbhatt.telly.data.Store
import com.nikhilbhatt.telly.data.TunePrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Turns taps into protocol commands: single keys, app launches, channel tuning and macros. */
class Controller(
    private val scope: CoroutineScope,
    private val link: TvLink,
    private val store: Store,
    private val ir: () -> IrBlaster,
    /** Makes a second, short-lived connection, e.g. to the TV while the remote controls a box. */
    private val newLink: () -> TvLink,
) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notices for the UI, e.g. "Not connected". */
    val messages: SharedFlow<String> = _messages

    private var macroJob: Job? = null
    private var powerJob: Job? = null

    fun key(code: Int, direction: KeyDirection = KeyDirection.Short) {
        scope.launch { if (!link.sendKey(code, direction)) notConnected() }
    }

    fun text(text: String) {
        scope.launch { if (!link.sendText(text)) notConnected() }
    }

    /**
     * Toggles the power of the active device, or of the device its power button is set to control
     * (typically the TV a set-top box is plugged into).
     */
    fun power() {
        val data = store.data.value
        val active = data.activeDevice ?: return notConnected()
        val target = data.device(active.powerDeviceId) ?: active
        powerJob?.cancel()
        powerJob = scope.launch {
            if (target.id == active.id) {
                togglePower(link, target, quiet = true)
            } else {
                val other = newLink()
                other.setTarget(target.linkTarget())
                other.resume()
                try {
                    togglePower(other, target, quiet = false)
                    delay(500) // let the key leave before the connection closes
                } finally {
                    other.pause()
                }
            }
        }
    }

    /**
     * A TV turned off with its remote stays on the network in standby and wakes on the Power key.
     * One that lost mains power doesn't; Wake-on-LAN is the last resort, for TVs that support it.
     */
    private suspend fun togglePower(l: TvLink, device: Device, quiet: Boolean) {
        // Wait until the connection either works or fails, rather than a fixed time.
        withTimeoutOrNull(if (quiet) 1_500 else 8_000) {
            l.state.first { it.status == LinkStatus.Connected || it.status == LinkStatus.Offline || it.status == LinkStatus.NeedsPairing }
        }
        if (l.state.value.status == LinkStatus.Connected) {
            val wasOn = l.state.value.screenOn
            l.sendKey(Keys.POWER)
            if (!quiet) _messages.tryEmit(if (wasOn == false) "Turning on ${device.name}" else "Turning off ${device.name}")
            return
        }
        // Unreachable usually means the TV lost power; only IR gets through then.
        val code = device.irPower
        if (code != null && ir().available) {
            withContext(Dispatchers.IO) { ir().send(code) }
            _messages.tryEmit("Sent IR power to ${device.name}. Point the phone at the TV.")
            return
        }
        if (l.state.value.status == LinkStatus.NeedsPairing) {
            _messages.tryEmit("${device.name} needs pairing again. Open Devices to pair it.")
            return
        }
        _messages.tryEmit("Waking ${device.name}…")
        device.mac?.let { mac -> withContext(Dispatchers.IO) { runCatching { WakeOnLan.send(mac, device.host) } } }
        l.reconnect()
        if (l.awaitConnected(30_000)) {
            if (l.state.value.screenOn == false) l.sendKey(Keys.POWER)
            _messages.tryEmit("${device.name} is on")
        } else {
            _messages.tryEmit("Couldn't reach ${device.name}. If it lost power, turn it on with its remote once.")
        }
    }

    fun run(shortcut: Shortcut) = runSteps(shortcut.steps)

    fun tune(channel: Channel) {
        val data = store.data.value
        val target = data.device(data.channelDeviceId)
        if (target != null && target.id != data.activeDevice?.id) {
            store.update { it.copy(activeDeviceId = target.id) }
            // Retarget now rather than via the store collector, so the digits can't reach the old device.
            link.setTarget(target.linkTarget())
            _messages.tryEmit("Switched to ${target.name}")
        }
        runSteps(listOf(Step.Tune(channel.number)))
    }

    fun runSteps(steps: List<Step>) {
        macroJob?.cancel()
        macroJob = scope.launch {
            if (!link.awaitConnected(4_000)) {
                notConnected()
                return@launch
            }
            steps.forEachIndexed { i, step ->
                if (i > 0) delay(STEP_GAP_MS)
                if (!perform(step)) {
                    notConnected()
                    return@launch
                }
            }
        }
    }

    private suspend fun perform(step: Step): Boolean = when (step) {
        is Step.Key -> if (step.long) {
            link.sendKey(step.code, KeyDirection.StartLong).also {
                delay(800)
                link.sendKey(step.code, KeyDirection.EndLong)
            }
        } else {
            link.sendKey(step.code)
        }
        is Step.Link -> link.sendLink(step.url)
        is Step.App -> launchApp(step.pkg)
        is Step.Text -> link.sendText(step.text)
        is Step.Wait -> {
            delay(step.ms.toLong())
            true
        }
        is Step.Tune -> tuneNow(step.number, store.data.value.activeDevice)
    }

    private enum class Launch { Opened, Rejected, Chooser }

    /**
     * Opens an app by trying its known links in turn. The TV reports links nothing can open, and
     * reports the app that comes to the front, so each attempt is checked before moving on.
     * Returns false only when the connection is gone.
     */
    private suspend fun launchApp(pkg: String): Boolean {
        val remembered = store.data.value.launchLinks[pkg]
        val candidates = (listOfNotNull(remembered) + Presets.appLinks[pkg].orEmpty() + appLink(pkg)).distinct()
        for (candidate in candidates) {
            val chooserBefore = link.state.value.currentApp == CHOOSER
            val seen = link.rejected.value.first
            if (!link.sendLink(candidate)) return false
            val outcome = withTimeoutOrNull(LAUNCH_CHECK_MS) {
                combine(link.rejected, link.state) { (count, rejectedLink), state ->
                    when {
                        count > seen && rejectedLink == candidate -> Launch.Rejected
                        state.currentApp == pkg -> Launch.Opened
                        state.currentApp == CHOOSER && !chooserBefore -> Launch.Chooser
                        else -> null
                    }
                }.filterNotNull().first()
            }
            when (outcome) {
                Launch.Rejected -> continue
                Launch.Chooser -> {
                    // The TV asks which app should open the link. Leave it up: once the user picks
                    // "Always", this link opens the app directly from then on.
                    if (candidate != remembered) store.update { it.copy(launchLinks = it.launchLinks + (pkg to candidate)) }
                    _messages.tryEmit("On the TV, pick ${appName(pkg)} and then Always. Next time it opens straight away.")
                    return true
                }
                Launch.Opened -> {
                    if (candidate != remembered) store.update { it.copy(launchLinks = it.launchLinks + (pkg to candidate)) }
                    return true
                }
                // No complaint from the TV; it opened, or this TV doesn't report the app in front.
                null -> return true
            }
        }
        _messages.tryEmit("Couldn't open ${appName(pkg)}. Is it installed on the TV?")
        return true
    }

    private fun appName(pkg: String) =
        store.data.value.shortcuts.firstOrNull { s -> s.steps.any { it is Step.App && it.pkg == pkg } }?.name ?: pkg

    /**
     * Channel numbers only reach live TV: Airtel boxes take them on their home screen but not inside
     * apps, and TVs need their live input in front. Get there first (unless it's already in front),
     * then type the number.
     */
    private suspend fun tuneNow(number: String, device: Device?): Boolean {
        val digits = number.filter(Char::isDigit)
        if (digits.isEmpty() || device == null) return false
        val current = link.state.value.currentApp
        val live = device.liveTvPackage
        if (current == null || current != live) {
            val ok = when (device.tunePrefix) {
                TunePrefix.None -> true
                TunePrefix.Home -> link.sendKey(Keys.HOME) && reachLive(device, current, maxWaitMs = 2_500, settleMs = 700)
                TunePrefix.LiveTvKey -> link.sendKey(Keys.TV) && reachLive(device, current, device.tuneDelayMs.toLong(), settleMs = 500)
                TunePrefix.LiveTvApp -> if (live == null) {
                    true
                } else {
                    launchApp(live) && reachLive(device, current, device.tuneDelayMs.toLong(), settleMs = 500)
                }
            }
            if (!ok) return false
        }
        for ((i, c) in digits.withIndex()) {
            if (i > 0) delay(device.digitGapMs.toLong())
            if (!link.sendKey(Keys.digit(c - '0'))) return false
        }
        if (device.confirmWithOk) {
            delay(device.digitGapMs.toLong())
            return link.sendKey(Keys.OK)
        }
        return true
    }

    /**
     * Waits for the screen to change after the key or launch that leads to live TV, then a moment
     * more for the picture to settle. The app that comes up is remembered as the live TV app, so
     * later tunes skip this step when it's already in front.
     */
    private suspend fun reachLive(device: Device, before: String?, maxWaitMs: Long, settleMs: Long): Boolean {
        val target = device.liveTvPackage
        val arrived = link.awaitApp(maxWaitMs) { app -> app != null && (app == target || (target == null && app != before)) }
        if (arrived != null && target == null && arrived != CHOOSER) {
            store.upsertDevice(store.data.value.device(device.id)?.copy(liveTvPackage = arrived) ?: return true)
        }
        delay(settleMs)
        return link.state.value.status == LinkStatus.Connected
    }

    private fun notConnected() {
        _messages.tryEmit("Not connected to the TV")
    }

    companion object {
        private const val STEP_GAP_MS = 150L
        private const val LAUNCH_CHECK_MS = 2_500L

        /** The system "Open with" chooser shows up as this package. */
        private const val CHOOSER = "android"

        /** The old way to open an app by package; kept as the last fallback. */
        fun appLink(pkg: String) = "market://launch?id=$pkg"
    }
}

fun Device.linkTarget() = LinkTarget(deviceId = id, host = host, trackApps = trackApps)
