package com.nikhilbhatt.telly.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nikhilbhatt.telly.Controller
import com.nikhilbhatt.telly.atv.TvState
import com.nikhilbhatt.telly.data.AppData
import com.nikhilbhatt.telly.data.Channel
import com.nikhilbhatt.telly.data.Device
import com.nikhilbhatt.telly.data.Presets
import com.nikhilbhatt.telly.data.Step
import com.nikhilbhatt.telly.data.Store
import com.nikhilbhatt.telly.data.TunePrefix
import com.nikhilbhatt.telly.telly
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(data: AppData, tv: TvState, store: Store, controller: Controller) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    var quickAdd by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var autoFind by remember { mutableStateOf(false) }

    val groups = remember(data.channels) { data.channels.mapNotNull { it.group?.ifBlank { null } }.distinct().sorted() }
    val shown = remember(data.channels, query, group) {
        data.channels
            .filter { group == null || it.group == group }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.number.startsWith(query) }
            .sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }))
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Channels", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tuning = true }) { Icon(Icons.Rounded.Tune, "Tuning settings") }
                    }
                    if (data.channels.isNotEmpty()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                            },
                            placeholder = { Text("Name or number") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        if (query.isNotBlank() && query.all(Char::isDigit)) {
                            AssistChip(
                                onClick = { controller.runSteps(listOf(Step.Tune(query))) },
                                label = { Text("Go to channel $query") },
                                leadingIcon = { Icon(Icons.Rounded.LiveTv, null, Modifier.size(18.dp)) },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (groups.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") }) }
                                items(groups) { g ->
                                    FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
                                }
                            }
                        }
                    }
                }
            }
            if (data.channels.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Rounded.LiveTv,
                        "Your channels, one tap away",
                        "Add channels with their number on your Airtel box. Tapping a tile switches the box to that channel.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { quickAdd = true }) { Text("Popular channels") }
                            Button(onClick = { editing = Channel(name = "", number = "") }) { Text("Add channel") }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { ch ->
                GridTile(
                    name = ch.name,
                    logo = ch.logo,
                    color = ch.color,
                    badge = ch.number,
                    onClick = { controller.tune(ch) },
                    onLongClick = { editing = ch },
                )
            }
        }

        if (data.channels.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, "Add channel") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("New channel") }, onClick = { addMenu = false; editing = Channel(name = "", number = "") })
                    DropdownMenuItem(text = { Text("Popular channels") }, onClick = { addMenu = false; quickAdd = true })
                }
            }
        }
    }

    editing?.let { ch ->
        ChannelEditor(
            initial = ch,
            isNew = data.channels.none { it.id == ch.id },
            groups = (groups + Presets.channelNames.map { it.second }).distinct(),
            store = store,
            autoFindLogo = autoFind,
            onTest = { controller.tune(it) },
            onDismiss = { editing = null; autoFind = false },
        )
    }
    if (quickAdd) {
        QuickAddSheet(
            existing = data.channels.map { it.name.lowercase() }.toSet(),
            onPick = { name, grp ->
                quickAdd = false
                autoFind = true
                editing = Channel(name = name, number = "", group = grp, color = Presets.colorFor(name))
            },
            onDismiss = { quickAdd = false },
        )
    }
    if (tuning) TuningSheet(data, tv, store, onDismiss = { tuning = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChannelEditor(
    initial: Channel,
    isNew: Boolean,
    groups: List<String>,
    store: Store,
    autoFindLogo: Boolean,
    onTest: (Channel) -> Unit,
    onDismiss: () -> Unit,
) {
    var ch by remember { mutableStateOf(initial) }
    var urlMode by remember { mutableStateOf(initial.logo?.startsWith("http") == true) }
    var finding by remember { mutableStateOf(false) }
    var findError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun findLogo() {
        finding = true
        findError = null
        scope.launch {
            val path = context.telly.logos.channelLogo(ch.name.trim())
            if (path != null) ch = ch.copy(logo = path) else findError = "No logo found online. Pick one from your gallery or paste an image URL."
            finding = false
        }
    }
    LaunchedEffect(Unit) { if (autoFindLogo && ch.logo == null && ch.name.isNotBlank()) findLogo() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyLogo(context, uri, store.logoDir)?.let { ch = ch.copy(logo = it) }
    }
    val valid = ch.name.isNotBlank() && ch.number.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (isNew) "New channel" else "Edit channel", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoArt(ch.name.ifBlank { "?" }, ch.logo, ch.color, Modifier.size(width = 120.dp, height = 92.dp).clip(RoundedCornerShape(18.dp)))
                Spacer(Modifier.width(16.dp))
                LogoButtons(
                    hasLogo = ch.logo != null,
                    finding = finding,
                    findLabel = "Find logo online",
                    onFind = if (ch.name.isNotBlank()) ::findLogo else null,
                    onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = { ch = ch.copy(logo = null); urlMode = false },
                ) {
                    TextButton(onClick = { urlMode = !urlMode }) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Image URL")
                    }
                }
            }
            findError?.let { Text(it, color = Palette.Muted, style = MaterialTheme.typography.bodySmall) }
            if (urlMode) {
                OutlinedTextField(
                    value = ch.logo?.takeIf { it.startsWith("http") } ?: "",
                    onValueChange = { ch = ch.copy(logo = it.trim().ifBlank { null }) },
                    label = { Text("Image URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ch.name,
                onValueChange = { ch = ch.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ch.number,
                onValueChange = { v -> ch = ch.copy(number = v.filter(Char::isDigit).take(5)) },
                label = { Text("Channel number") },
                supportingText = { Text("The number you'd type on the Airtel remote") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ch.group ?: "",
                onValueChange = { ch = ch.copy(group = it.ifBlank { null }) },
                label = { Text("Group (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                groups.take(8).forEach { g ->
                    FilterChip(selected = ch.group == g, onClick = { ch = ch.copy(group = if (ch.group == g) null else g) }, label = { Text(g) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Tile colour", style = SectionLabel)
            Spacer(Modifier.height(8.dp))
            ColorPicker(ch.color) { ch = ch.copy(color = it) }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { store.update { d -> d.copy(channels = d.channels.filterNot { it.id == ch.id }) }; onDismiss() }) {
                        Text("Delete", color = Palette.Power)
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onTest(ch) }, enabled = ch.number.isNotBlank()) { Text("Test") }
                Button(onClick = { store.upsertChannel(ch.copy(name = ch.name.trim())); onDismiss() }, enabled = valid) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAddSheet(existing: Set<String>, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Popular channels", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick one, then type its number from your Airtel channel list. Numbers vary by region and pack.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Presets.channelNames.groupBy({ it.second }, { it.first }).forEach { (grp, names) ->
                SectionHeader(grp, Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEach { n ->
                        val added = n.lowercase() in existing
                        AssistChip(onClick = { onPick(n, grp) }, label = { Text(n) }, enabled = !added)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningSheet(data: AppData, tv: TvState, store: Store, onDismiss: () -> Unit) {
    val target = data.device(data.channelDeviceId) ?: data.activeDevice
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Channel tuning", style = MaterialTheme.typography.titleLarge)
            if (data.devices.size > 1) {
                SectionHeader("Channels go to", Modifier.padding(top = 12.dp))
                ChoiceRow("Whichever device is selected", data.channelDeviceId == null) { store.update { it.copy(channelDeviceId = null) } }
                data.devices.forEach { d ->
                    ChoiceRow(d.name, data.channelDeviceId == d.id) { store.update { it.copy(channelDeviceId = d.id) } }
                }
            }
            if (target == null) {
                Text("Add a device first.", color = Palette.Muted, modifier = Modifier.padding(top = 12.dp))
            } else {
                TuningSettings(target, tv, onChange = store::upsertDevice)
            }
        }
    }
}

/** How a device gets to its live TV app before the digits are typed. Shared with the device editor. */
@Composable
fun TuningSettings(device: Device, tv: TvState, onChange: (Device) -> Unit) {
    val current = tv.currentApp.takeIf { tv.deviceId == device.id }
    SectionHeader("Before typing the number", Modifier.padding(top = 12.dp))
    ChoiceRow("Go to the home screen (Airtel boxes)", device.tunePrefix == TunePrefix.Home) { onChange(device.copy(tunePrefix = TunePrefix.Home)) }
    ChoiceRow("Press the Live TV key", device.tunePrefix == TunePrefix.LiveTvKey) { onChange(device.copy(tunePrefix = TunePrefix.LiveTvKey)) }
    ChoiceRow("Open the live TV app", device.tunePrefix == TunePrefix.LiveTvApp) { onChange(device.copy(tunePrefix = TunePrefix.LiveTvApp)) }
    ChoiceRow("Nothing, just type digits", device.tunePrefix == TunePrefix.None) { onChange(device.copy(tunePrefix = TunePrefix.None)) }

    SectionHeader("Live TV app", Modifier.padding(top = 12.dp))
    Text(
        device.liveTvPackage ?: "Not set yet. Telly learns it the first time it switches to live TV, or open live TV and tap below.",
        style = MaterialTheme.typography.bodyMedium,
        color = if (device.liveTvPackage == null) Palette.Muted else Palette.Text,
    )
    Text(
        "When live TV is already on screen, Telly skips the step above and types the number straight away.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Muted,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(Modifier.padding(top = 4.dp)) {
        TextButton(
            onClick = { onChange(device.copy(liveTvPackage = current, tunePrefix = TunePrefix.LiveTvApp)) },
            enabled = current != null,
        ) { Text(if (current != null) "Use app on screen ($current)" else "Use app on screen (connect first)") }
    }
    if (device.liveTvPackage != null) {
        TextButton(onClick = { onChange(device.copy(liveTvPackage = null)) }) { Text("Clear") }
    }

    SectionHeader("Timing", Modifier.padding(top = 12.dp))
    Text("Wait for live TV: ${device.tuneDelayMs} ms", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = device.tuneDelayMs.toFloat(),
        onValueChange = { onChange(device.copy(tuneDelayMs = (it / 100).toInt() * 100)) },
        valueRange = 300f..5000f,
    )
    Text("Gap between digits: ${device.digitGapMs} ms", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = device.digitGapMs.toFloat(),
        onValueChange = { onChange(device.copy(digitGapMs = (it / 50).toInt() * 50)) },
        valueRange = 100f..1000f,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Press OK after the number", style = MaterialTheme.typography.bodyMedium)
            Text("Switches faster on boxes that wait for more digits", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(checked = device.confirmWithOk, onCheckedChange = { onChange(device.copy(confirmWithOk = it)) })
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
