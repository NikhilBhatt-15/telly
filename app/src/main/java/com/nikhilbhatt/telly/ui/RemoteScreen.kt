package com.nikhilbhatt.telly.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhilbhatt.telly.atv.KeyDirection
import com.nikhilbhatt.telly.atv.Keys
import com.nikhilbhatt.telly.atv.LinkStatus
import com.nikhilbhatt.telly.atv.TvState
import com.nikhilbhatt.telly.data.AppData
import com.nikhilbhatt.telly.data.DeviceKind
import com.nikhilbhatt.telly.data.Step
import com.nikhilbhatt.telly.Controller
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun RemoteScreen(
    data: AppData,
    tv: TvState,
    controller: Controller,
    onSelectDevice: (String) -> Unit,
    onRepair: () -> Unit,
    onRetry: () -> Unit,
    onAddDevice: () -> Unit,
) {
    val device = data.activeDevice
    if (device == null) {
        EmptyState(
            Icons.Rounded.TouchApp,
            "No TV yet",
            "Add your Airtel Xstream box or Android TV. Telly finds it on your Wi-Fi and pairs with a code shown on screen.",
            Modifier.padding(top = 80.dp),
        ) { Button(onClick = onAddDevice) { Text("Add a device") } }
        return
    }
    var showNumpad by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var touchpad by rememberSaveable { mutableStateOf(false) }
    val key = { code: Int -> controller.key(code) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteHeader(data, tv, onSelectDevice)
        when (tv.status) {
            LinkStatus.NeedsPairing -> Notice("${device.name} doesn't recognise this phone yet.", "Pair", onRepair)
            LinkStatus.Offline -> Notice("Can't reach ${device.name}. Is it on and on the same Wi-Fi?", "Retry", onRetry)
            else -> Unit
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Rounded.PowerSettingsNew, "Power", controller::power, container = Palette.Power.copy(alpha = 0.18f), tint = Palette.Power)
            Spacer(Modifier.weight(1f))
            if (device.kind == DeviceKind.Tv) {
                IconKey(Icons.Rounded.Input, "Input", { key(Keys.INPUT) }, size = 48.dp)
                Spacer(Modifier.width(10.dp))
            }
            IconKey(Icons.Rounded.Keyboard, "Keyboard", { showKeyboard = true }, size = 48.dp)
            Spacer(Modifier.width(10.dp))
            IconKey(Icons.Rounded.Dialpad, "Number pad", { showNumpad = true }, size = 48.dp)
        }

        val pinned = data.shortcuts.filter { it.pinned }
        if (pinned.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(pinned, key = { it.id }) { s ->
                    PressKey(
                        onPress = { controller.run(s) },
                        shape = RoundedCornerShape(16.dp),
                        container = if (s.logo != null) Palette.SurfaceHigh else Color(s.color),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Row(
                            Modifier.padding(start = if (s.logo != null) 8.dp else 18.dp, end = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (s.logo != null) {
                                LogoArt(s.name, s.logo, s.color, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), fill = true)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(s.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            if (touchpad) {
                Touchpad(onKey = { code, dir -> controller.key(code, dir) }, modifier = Modifier.align(Alignment.Center))
            } else {
                DPad(onKey = { code, dir -> controller.key(code, dir) }, modifier = Modifier.align(Alignment.Center))
            }
            IconButton(onClick = { touchpad = !touchpad }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (touchpad) Icons.Rounded.Dialpad else Icons.Rounded.SwipeVertical,
                    contentDescription = if (touchpad) "Use D-pad" else "Use touchpad",
                    tint = Palette.Muted,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { key(Keys.BACK) })
            IconKey(Icons.Rounded.Home, "Home", { key(Keys.HOME) })
            IconKey(Icons.Rounded.Menu, "Menu", { key(Keys.MENU) })
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Rocker("VOL", up = { key(Keys.VOLUME_UP) }, down = { key(Keys.VOLUME_DOWN) })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IconKey(
                    Icons.AutoMirrored.Rounded.VolumeOff,
                    "Mute",
                    { key(Keys.MUTE) },
                    tint = if (tv.volume?.muted == true) Palette.Accent else Palette.Text,
                )
                IconKey(Icons.Rounded.Settings, "TV settings", { key(Keys.SETTINGS) })
            }
            Rocker("CH", up = { key(Keys.CHANNEL_UP) }, down = { key(Keys.CHANNEL_DOWN) })
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconKey(Icons.Rounded.FastRewind, "Rewind", { key(Keys.REWIND) }, size = 52.dp)
            IconKey(Icons.Rounded.PlayArrow, "Play / pause", { key(Keys.PLAY_PAUSE) }, size = 52.dp, container = Palette.SurfaceHigher)
            IconKey(Icons.Rounded.FastForward, "Fast forward", { key(Keys.FAST_FORWARD) }, size = 52.dp)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showNumpad) NumpadSheet(controller, onDismiss = { showNumpad = false })
    if (showKeyboard) KeyboardDialog(controller, onDismiss = { showKeyboard = false })
}

@Composable
private fun RemoteHeader(data: AppData, tv: TvState, onSelectDevice: (String) -> Unit) {
    val device = data.activeDevice ?: return
    var menu by remember { mutableStateOf(false) }
    val known = remember(data.shortcuts) {
        data.shortcuts.mapNotNull { s -> (s.steps.singleOrNull() as? Step.App)?.let { it.pkg to s.name } }.toMap()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = data.devices.size > 1) { menu = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(device.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (data.devices.size > 1) Icon(Icons.Rounded.ExpandMore, "Switch device", tint = Palette.Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    data.devices.forEach { d ->
                        DropdownMenuItem(text = { Text(d.name) }, onClick = { menu = false; onSelectDevice(d.id) })
                    }
                }
            }
            val now = appLabel(tv.currentApp, known)
            val details = listOfNotNull(
                now?.let { "On screen: $it" },
                tv.volume?.let { "Volume ${it.level}" },
            ).joinToString("  ·  ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        StatusPill(tv)
    }
}

@Composable
fun StatusPill(tv: TvState) {
    val (label, color) = when (tv.status) {
        LinkStatus.Connected -> if (tv.screenOn == false) "Standby" to Palette.Warn else "Connected" to Palette.Good
        LinkStatus.Connecting -> "Connecting" to Palette.Muted
        LinkStatus.Offline -> "Offline" to Palette.Power
        LinkStatus.NeedsPairing -> "Not paired" to Palette.Warn
        LinkStatus.Idle -> "Idle" to Palette.Muted
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.SurfaceHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private enum class PadZone(val code: Int) { Up(Keys.UP), Down(Keys.DOWN), Left(Keys.LEFT), Right(Keys.RIGHT), Ok(Keys.OK) }

/**
 * Circular D-pad. Arrows repeat while held; holding OK sends a long press, which opens context
 * menus on Android TV.
 */
@Composable
private fun DPad(onKey: (Int, KeyDirection) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<PadZone?>(null) }
    val haptic = rememberHaptic()
    val send by rememberUpdatedState(onKey)
    val ring = Palette.SurfaceHigh
    val highlight = Palette.SurfaceHigher.lighten(0.06f)
    Box(
        modifier
            .size(236.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val zone = zoneAt(down.position, Size(size.width.toFloat(), size.height.toFloat())) ?: return@awaitEachGesture
                    active = zone
                    haptic()
                    if (zone == PadZone.Ok) {
                        val quick = withTimeoutOrNull(500) { waitForUpOrCancellation(); true }
                        if (quick != null) {
                            send(Keys.OK, KeyDirection.Short)
                        } else {
                            send(Keys.OK, KeyDirection.StartLong)
                            waitForUpOrCancellation()
                            send(Keys.OK, KeyDirection.EndLong)
                        }
                    } else {
                        send(zone.code, KeyDirection.Short)
                        var released = withTimeoutOrNull(450) { waitForUpOrCancellation(); true }
                        while (released == null) {
                            send(zone.code, KeyDirection.Short)
                            released = withTimeoutOrNull(130) { waitForUpOrCancellation(); true }
                        }
                    }
                    active = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(ring, r)
            val zone = active
            if (zone != null && zone != PadZone.Ok) {
                val start = when (zone) {
                    PadZone.Right -> -45f
                    PadZone.Down -> 45f
                    PadZone.Left -> 135f
                    else -> 225f
                }
                drawArc(highlight, start, 90f, useCenter = true)
            }
            drawCircle(Palette.Bg, r * 0.40f)
            drawCircle(if (zone == PadZone.Ok) Palette.Accent.lighten(0.1f) else Palette.Accent, r * 0.34f)
        }
        Text("OK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        PadArrow(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        PadArrow(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        PadArrow(Icons.Rounded.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
        PadArrow(Icons.Rounded.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
    }
}

@Composable
private fun PadArrow(icon: ImageVector, modifier: Modifier) {
    Icon(icon, null, tint = Palette.Muted, modifier = modifier.size(34.dp))
}

private fun zoneAt(p: Offset, size: Size): PadZone? {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = size.minDimension / 2
    val dist = hypot(p.x - cx, p.y - cy)
    if (dist > r) return null
    if (dist < r * 0.40f) return PadZone.Ok
    val angle = (Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())) + 360) % 360
    return when {
        angle < 45 || angle >= 315 -> PadZone.Right
        angle < 135 -> PadZone.Down
        angle < 225 -> PadZone.Left
        else -> PadZone.Up
    }
}

/** Swipe to move the focus one step per ~36 dp, tap for OK. */
@Composable
private fun Touchpad(onKey: (Int, KeyDirection) -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val send by rememberUpdatedState(onKey)
    val stepPx = with(LocalDensity.current) { 36.dp.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Outline, RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var dx = 0f
                    var dy = 0f
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        change.consume()
                        dx += delta.x
                        dy += delta.y
                        if (abs(dx) >= stepPx || abs(dy) >= stepPx) {
                            val code = if (abs(dx) > abs(dy)) {
                                if (dx > 0) Keys.RIGHT else Keys.LEFT
                            } else {
                                if (dy > 0) Keys.DOWN else Keys.UP
                            }
                            haptic()
                            send(code, KeyDirection.Short)
                            moved = true
                            dx = 0f
                            dy = 0f
                        }
                    }
                    if (!moved) {
                        haptic()
                        send(Keys.OK, KeyDirection.Short)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.TouchApp, null, tint = Palette.Muted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("Swipe to move  ·  Tap for OK", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Rocker(label: String, up: () -> Unit, down: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(36.dp)).background(Palette.SurfaceHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PressKey(up, Modifier.size(width = 72.dp, height = 64.dp), repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowUp, "$label up", modifier = Modifier.size(30.dp))
        }
        Text(label, style = SectionLabel)
        PressKey(down, Modifier.size(width = 72.dp, height = 64.dp), repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowDown, "$label down", modifier = Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumpadSheet(controller: Controller, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                number.ifEmpty { "Channel" },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (number.isEmpty()) Palette.Muted else Palette.Text,
            )
            Spacer(Modifier.height(16.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "Last"))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { k ->
                        PressKey(
                            onPress = {
                                when (k) {
                                    "⌫" -> number = number.dropLast(1)
                                    "Last" -> controller.key(Keys.LAST_CHANNEL)
                                    else -> if (number.length < 5) number += k
                                }
                            },
                            modifier = Modifier.padding(6.dp).size(72.dp),
                        ) {
                            if (k == "⌫") {
                                Icon(Icons.Rounded.Backspace, "Delete")
                            } else {
                                Text(k, fontSize = if (k.length > 1) 15.sp else 26.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    controller.runSteps(listOf(Step.Tune(number)))
                    onDismiss()
                },
                enabled = number.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Go to channel $number") }
            TextButton(
                onClick = { number.forEach { controller.key(Keys.digit(it - '0')) }; number = "" },
                enabled = number.isNotEmpty(),
            ) { Text("Just send the digits") }
        }
    }
}

@Composable
private fun KeyboardDialog(controller: Controller, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type on TV") },
        text = {
            Column {
                Text(
                    "Select a search box or text field on the TV first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Search or type…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { controller.text(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconKey(Icons.Rounded.Backspace, "Delete on TV", { controller.key(Keys.DEL) }, size = 48.dp, repeat = true)
                    IconKey(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter", { controller.key(Keys.ENTER) }, size = 48.dp)
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = { controller.text(text) }, enabled = text.isNotEmpty()) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
