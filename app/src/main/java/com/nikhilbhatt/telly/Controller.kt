package com.nikhilbhatt.telly

import com.nikhilbhatt.telly.atv.KeyDirection
import com.nikhilbhatt.telly.atv.Keys
import com.nikhilbhatt.telly.atv.LinkStatus
import com.nikhilbhatt.telly.atv.LinkTarget
import com.nikhilbhatt.telly.atv.TvLink
import com.nikhilbhatt.telly.atv.WakeOnLan
import com.nikhilbhatt.telly.data.Channel
import com.nikhilbhatt.telly.data.Device
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
import kotlinx.coroutines.launch

/** Turns taps into protocol commands: single keys, app launches, channel tuning and macros. */
class Controller(
    private val scope: CoroutineScope,
    private val link: TvLink,
    private val store: Store,
) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notices for the UI, e.g. "Not connected". */
    val messages: SharedFlow<String> = _messages

    private var macroJob: Job? = null

    fun key(code: Int, direction: KeyDirection = KeyDirection.Short) {
        scope.launch { if (!link.sendKey(code, direction)) notConnected() }
    }

    fun text(text: String) {
        scope.launch { if (!link.sendText(text)) notConnected() }
    }

    /** Power also tries Wake-on-LAN when the TV is unreachable, since a sleeping TV can't take keys. */
    fun power() {
        val state = link.state.value
        val device = store.data.value.activeDevice
        if (state.status != LinkStatus.Connected && device?.mac != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { WakeOnLan.send(device.mac) }
                _messages.tryEmit("Sent a wake-up signal to ${device.name}")
                link.reconnect()
            }
            return
        }
        key(Keys.POWER)
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
        is Step.App -> link.sendLink(appLink(step.pkg))
        is Step.Text -> link.sendText(step.text)
        is Step.Wait -> {
            delay(step.ms.toLong())
            true
        }
        is Step.Tune -> tuneNow(step.number, store.data.value.activeDevice)
    }

    /**
     * Set-top boxes only take channel digits inside their live TV app, so get there first
     * (unless we know it's already in front), then type the number.
     */
    private suspend fun tuneNow(number: String, device: Device?): Boolean {
        val digits = number.filter(Char::isDigit)
        if (digits.isEmpty() || device == null) return false
        val current = link.state.value.currentApp
        val alreadyLive = device.liveTvPackage != null && current == device.liveTvPackage
        if (!alreadyLive) {
            val sent = when (device.tunePrefix) {
                TunePrefix.None -> null
                TunePrefix.LiveTvKey -> link.sendKey(Keys.TV)
                TunePrefix.LiveTvApp -> device.liveTvPackage?.let { link.sendLink(appLink(it)) }
            }
            if (sent == false) return false
            if (sent == true) delay(device.tuneDelayMs.toLong())
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

    private fun notConnected() {
        _messages.tryEmit("Not connected to the TV")
    }

    companion object {
        private const val STEP_GAP_MS = 150L

        /** Android TV opens an installed app by package through this link. */
        fun appLink(pkg: String) = "market://launch?id=$pkg"
    }
}

fun Device.linkTarget() = LinkTarget(deviceId = id, host = host, trackApps = trackApps)
