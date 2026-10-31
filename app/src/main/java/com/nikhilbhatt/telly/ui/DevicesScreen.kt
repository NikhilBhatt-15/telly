package com.nikhilbhatt.telly.ui

import android.hardware.ConsumerIrManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhilbhatt.telly.BuildConfig
import com.nikhilbhatt.telly.IrBlaster
import com.nikhilbhatt.telly.data.IrCode
import androidx.compose.material3.AlertDialog
import com.nikhilbhatt.telly.FoundTv
import com.nikhilbhatt.telly.atv.PairingException
import com.nikhilbhatt.telly.atv.PairingSession
import com.nikhilbhatt.telly.atv.TvState
import com.nikhilbhatt.telly.data.AppData
import com.nikhilbhatt.telly.data.Device
import com.nikhilbhatt.telly.data.DeviceKind
import com.nikhilbhatt.telly.discoverTvs
import com.nikhilbhatt.telly.telly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DevicesScreen(
    data: AppData,
    tv: TvState,
    pairing: Device?,
    onPair: (Device?) -> Unit,
    onPairingDone: () -> Unit,
    showAdd: Boolean,
    onShowAdd: (Boolean) -> Unit,
) {
    val app = LocalContext.current.telly
    val store = app.store
    var editing by remember { mutableStateOf<Device?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Devices", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        data.devices.forEach { d ->
            val active = d.id == data.activeDevice?.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Palette.SurfaceHigh else Palette.Surface)
                    .clickable { store.update { it.copy(activeDeviceId = d.id) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (active) Palette.Accent.copy(alpha = 0.18f) else Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (d.kind == DeviceKind.Box) Icons.Rounded.Router else Icons.Rounded.Tv,
                        null,
                        tint = if (active) Palette.Accent else Palette.Muted,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (d.kind == DeviceKind.Box) "Set-top box" else "TV") + "  ·  " + d.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                if (active) StatusPill(tv)
                IconButton(onClick = { editing = d }) { Icon(Icons.Rounded.Edit, "Edit ${d.name}", tint = Palette.Muted) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onShowAdd(true) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add a device")
        }

        SectionHeader("Settings", Modifier.padding(top = 24.dp))
        SettingSwitch("Vibrate on key press", data.haptics) { v -> store.update { it.copy(haptics = v) } }
        SettingSwitch("Phone volume buttons control the TV", data.volumeKeys) { v -> store.update { it.copy(volumeKeys = v) } }
        val context = LocalContext.current
        val hasIr = remember { context.getSystemService(ConsumerIrManager::class.java)?.hasIrEmitter() == true }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IR blaster on this phone", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (hasIr) "Yes" else "No", color = if (hasIr) Palette.Good else Palette.Muted, style = MaterialTheme.typography.labelLarge)
        }

        SectionHeader("How it works", Modifier.padding(top = 24.dp))
        Text(
            "Telly talks to Android TV and Google TV devices the same way Google's own remote app does: " +
                "a one-time pairing code on port 6467, then encrypted commands on port 6466. " +
                "Nothing goes through the internet, and the TV doesn't need developer mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(24.dp))
    }

    if (showAdd || pairing != null) {
        PairSheet(
            existing = pairing,
            onDone = { device ->
                store.upsertDevice(device)
                store.update { it.copy(activeDeviceId = device.id) }
                app.link.reconnect()
                onPairingDone()
            },
            onDismiss = { onPair(null); onShowAdd(false) },
        )
    }
    editing?.let { d ->
        DeviceEditor(
            initial = d,
            others = data.devices.filter { it.id != d.id },
            tv = tv,
            onSave = { store.upsertDevice(it); editing = null },
            onRemove = {
                store.update { s ->
                    s.copy(
                        devices = s.devices.filterNot { it.id == d.id },
                        activeDeviceId = s.activeDeviceId.takeIf { it != d.id },
                        channelDeviceId = s.channelDeviceId.takeIf { it != d.id },
                    )
                }
                editing = null
            },
            onRepair = { editing = null; onPair(d) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private sealed interface PairStep {
    data object Choose : PairStep
    data class Starting(val name: String, val host: String) : PairStep
    data class EnterCode(val name: String, val host: String, val session: PairingSession) : PairStep
}

/** Guess the kind from the advertised name; users can change it in the editor. */
private fun guessKind(name: String): DeviceKind {
    val n = name.lowercase()
    return when {
        listOf("airtel", "xstream", "box", "stick", "streamer", "chromecast", "shield").any { it in n } -> DeviceKind.Box
        "tv" in n -> DeviceKind.Tv
        else -> DeviceKind.Box
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairSheet(existing: Device?, onDone: (Device) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.telly
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<PairStep>(PairStep.Choose) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<FoundTv>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun begin(name: String, host: String) {
        error = null
        code = ""
        step = PairStep.Starting(name, host)
        scope.launch {
            step = try {
                val session = app.startPairing(host)
                // Typed-in IPs get the TV's own name instead.
                PairStep.EnterCode(if (name == host) session.serverName ?: name else name, host, session)
            } catch (e: Exception) {
                error = "Couldn't start pairing with $host: ${e.message ?: e.javaClass.simpleName}"
                PairStep.Choose
            }
        }
    }

    LaunchedEffect(existing) { if (existing != null) begin(existing.name, existing.host) }
    LaunchedEffect(Unit) {
        discoverTvs(context)
            .catch { discoveryError = it.message }
            .collect { found = it }
    }
    DisposableEffect(Unit) {
        onDispose { (step as? PairStep.EnterCode)?.session?.close() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            when (val s = step) {
                PairStep.Choose -> {
                    Text("Add a device", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Your phone and the TV or box need to be on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                    error?.let { Text(it, color = Palette.Power, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                    SectionHeader("Found on your network", Modifier.padding(top = 16.dp)) {
                        if (discoveryError == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    if (found.isEmpty()) {
                        Text(
                            discoveryError ?: "Searching… Turn the TV on if it doesn't show up.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                        )
                    }
                    found.forEach { tvFound ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.SurfaceHigh)
                                .clickable { begin(tvFound.name, tvFound.host) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (guessKind(tvFound.name) == DeviceKind.Box) Icons.Rounded.Router else Icons.Rounded.Tv, null, tint = Palette.Accent)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tvFound.name, style = MaterialTheme.typography.titleMedium)
                                Text(tvFound.host, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                            Text("Pair", color = Palette.Accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    SectionHeader("Or enter its IP address", Modifier.padding(top = 20.dp))
                    Text(
                        "On the TV: Settings → Network & Internet → your Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it.trim() },
                            placeholder = { Text("192.168.1.20") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = { begin(manualHost, manualHost) }, enabled = manualHost.isNotBlank()) { Text("Pair") }
                    }
                }
                is PairStep.Starting -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Asking ${s.name} for a code…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is PairStep.EnterCode -> {
                    Text("Enter the code on the TV", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${s.name} is now showing a 6-character code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = Palette.Power, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { s.session.finish(code) }
                                    val device = (existing ?: Device(name = s.name, host = s.host, kind = guessKind(s.name)))
                                        .copy(host = s.host, mac = s.session.serverMac ?: existing?.mac)
                                    s.session.close()
                                    onDone(device)
                                } catch (e: PairingException) {
                                    error = e.message
                                    // A rejected code ends the TV's pairing session; a typo caught locally doesn't.
                                    if (e.message?.contains("rejected") == true) {
                                        s.session.close()
                                        begin(s.name, s.host)
                                    }
                                } catch (e: Exception) {
                                    error = "Pairing failed: ${e.message ?: e.javaClass.simpleName}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = code.length == 6 && !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (busy) "Pairing…" else "Pair") }
                    TextButton(onClick = { s.session.close(); begin(s.name, s.host) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Show a new code")
                    }
                }
            }
        }
    }
}

/**
 * Universal-remote style search: send one candidate power code at a time until the TV reacts.
 * The codes are ordered by how many brands share them, so the likely ones come first.
 */
@Composable
private fun IrSearchDialog(ir: IrBlaster, onFound: (IrCode) -> Unit, onDismiss: () -> Unit) {
    val codes = ir.powerCodes
    var index by remember { mutableStateOf(0) }
    var sent by remember { mutableStateOf(false) }
    fun sendCurrent() {
        ir.send(codes[index])
        sent = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find your TV's IR code") },
        text = {
            Column {
                Text(
                    "Point the phone's top edge at the TV and tap Send. If the TV turns on or off, tap It worked. If not, tap Next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text("Code ${index + 1} of ${codes.size}", style = MaterialTheme.typography.titleMedium)
                Text(codes[index].label(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (index > 0) { index--; sendCurrent() } }, enabled = index > 0) { Text("Back") }
                    Button(onClick = ::sendCurrent) { Text("Send") }
                    OutlinedButton(onClick = { if (index < codes.lastIndex) { index++; sendCurrent() } }, enabled = index < codes.lastIndex) { Text("Next") }
                }
            }
        },
        confirmButton = { Button(onClick = { onFound(codes[index]) }, enabled = sent) { Text("It worked") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceEditor(
    initial: Device,
    others: List<Device>,
    tv: TvState,
    onSave: (Device) -> Unit,
    onRemove: () -> Unit,
    onRepair: () -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Edit device", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = d.name,
                onValueChange = { d = d.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = d.host,
                onValueChange = { d = d.copy(host = it.trim()) },
                label = { Text("IP address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(d.kind == DeviceKind.Box, { d = d.copy(kind = DeviceKind.Box) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Set-top box") }
                SegmentedButton(d.kind == DeviceKind.Tv, { d = d.copy(kind = DeviceKind.Tv) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("TV") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = d.mac ?: "",
                onValueChange = { d = d.copy(mac = it.trim().uppercase().ifBlank { null }) },
                label = { Text("MAC address (for Wake-on-LAN)") },
                leadingIcon = { Icon(Icons.Rounded.Wifi, null) },
                supportingText = { Text("Filled in during pairing when the TV reports it") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show which app is on screen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Turn off if the TV keeps showing a keyboard prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                Switch(checked = d.trackApps, onCheckedChange = { d = d.copy(trackApps = it) })
            }
            if (others.isNotEmpty()) {
                SectionHeader("Power button turns on or off", Modifier.padding(top = 12.dp))
                ChoiceRow("This device", d.powerDeviceId == null) { d = d.copy(powerDeviceId = null) }
                others.forEach { o ->
                    ChoiceRow(o.name, d.powerDeviceId == o.id) { d = d.copy(powerDeviceId = o.id) }
                }
                Text(
                    "Pick the TV this box is plugged into, so the power button switches the TV while you control the box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            val ir = LocalContext.current.telly.ir
            var searchingIr by remember { mutableStateOf(false) }
            if (ir.available) {
                SectionHeader("IR power (this phone's IR blaster)", Modifier.padding(top = 12.dp))
                Text(
                    d.irPower?.let { "Code saved: ${it.label()}" }
                        ?: "Turns the TV on even after a power cut, when Wi-Fi can't reach it. Find the code once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                Row {
                    TextButton(onClick = { searchingIr = true }) { Text(if (d.irPower == null) "Find my TV's code" else "Find again") }
                    d.irPower?.let { code -> TextButton(onClick = { ir.send(code) }) { Text("Test") } }
                }
            }
            if (searchingIr) {
                IrSearchDialog(ir, onFound = { d = d.copy(irPower = it); searchingIr = false }, onDismiss = { searchingIr = false })
            }
            TuningSettings(d, tv) { d = it }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRemove) { Text("Remove", color = Palette.Power) }
                TextButton(onClick = onRepair) { Text("Pair again") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(d.copy(name = d.name.trim().ifBlank { initial.name })) }, enabled = d.host.isNotBlank()) { Text("Save") }
            }
        }
    }
}
