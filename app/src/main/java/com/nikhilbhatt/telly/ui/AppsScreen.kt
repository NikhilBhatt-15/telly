package com.nikhilbhatt.telly.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nikhilbhatt.telly.Controller
import com.nikhilbhatt.telly.atv.Keys
import com.nikhilbhatt.telly.atv.TvState
import com.nikhilbhatt.telly.data.AppData
import com.nikhilbhatt.telly.data.LogoFinder
import com.nikhilbhatt.telly.data.Presets
import com.nikhilbhatt.telly.data.Shortcut
import com.nikhilbhatt.telly.data.Step
import com.nikhilbhatt.telly.data.Store
import com.nikhilbhatt.telly.data.describe
import com.nikhilbhatt.telly.telly
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(data: AppData, tv: TvState, store: Store, controller: Controller) {
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val apps = data.shortcuts.filter { it.isApp }
    val macros = data.shortcuts.filterNot { it.isApp }
    val savedPackages = remember(data.shortcuts) {
        data.shortcuts.flatMap { s -> s.steps.filterIsInstance<Step.App>().map { it.pkg } }.toSet()
    }
    val onScreen = tv.currentApp?.takeIf { it !in savedPackages && it !in IGNORED_PACKAGES }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Apps & shortcuts", style = MaterialTheme.typography.headlineMedium)
            }
            if (onScreen != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Palette.SurfaceHigh),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Open on the TV now", style = SectionLabel)
                                Text(onScreen, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = {
                                editing = Shortcut(
                                    name = appLabel(onScreen, emptyMap()) ?: onScreen,
                                    steps = listOf(Step.App(onScreen)),
                                    color = Presets.colorFor(onScreen),
                                    isApp = true,
                                    pinned = true,
                                )
                            }) { Text("Save as app") }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Apps", Modifier.padding(top = 8.dp)) }
            items(apps, key = { it.id }) { s ->
                GridTile(s.name, s.logo, s.color, onClick = { controller.run(s) }, onLongClick = { editing = s }, fillLogo = true)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Shortcuts", Modifier.padding(top = 12.dp)) }
            if (macros.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Shortcuts run several steps in a row. For example: open YouTube and search for lo-fi, " +
                            "or open a specific Netflix show. Tap + to make one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                }
            }
            items(macros, key = { it.id }) { s ->
                GridTile(
                    s.name,
                    s.logo,
                    s.color,
                    subtitle = "${s.steps.size} step" + if (s.steps.size == 1) "" else "s",
                    fillLogo = true,
                    onClick = { controller.run(s) },
                    onLongClick = { editing = s },
                )
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, "Add") }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(
                    text = { Text("App") },
                    leadingIcon = { Icon(Icons.Rounded.Apps, null) },
                    onClick = { addMenu = false; editing = Shortcut(name = "", steps = emptyList(), isApp = true, pinned = true, color = Presets.palette[5]) },
                )
                DropdownMenuItem(
                    text = { Text("Shortcut") },
                    leadingIcon = { Icon(Icons.Rounded.Bolt, null) },
                    onClick = { addMenu = false; editing = Shortcut(name = "", steps = emptyList(), color = Presets.palette[7]) },
                )
            }
        }
    }

    editing?.let { s ->
        ShortcutEditor(
            initial = s,
            isNew = data.shortcuts.none { it.id == s.id },
            tv = tv,
            store = store,
            onRun = { controller.run(it) },
            onDismiss = { editing = null },
        )
    }
}

/** Launchers and system UI that are never worth saving as an app tile. */
private val IGNORED_PACKAGES = setOf(
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.android.tv.settings",
    "com.google.android.katniss",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditor(
    initial: Shortcut,
    isNew: Boolean,
    tv: TvState,
    store: Store,
    onRun: (Shortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    var stepEdit by remember { mutableStateOf<Pair<Int, Step?>?>(null) }
    var finding by remember { mutableStateOf(false) }
    var findError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val iconPkg = LogoFinder.iconPackage(s)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyLogo(context, uri, store.logoDir)?.let { s = s.copy(logo = it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                (if (isNew) "New " else "Edit ") + if (s.isApp) "app" else "shortcut",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoArt(s.name.ifBlank { "?" }, s.logo, s.color, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)), fill = true)
                Spacer(Modifier.width(16.dp))
                LogoButtons(
                    hasLogo = s.logo != null,
                    finding = finding,
                    findLabel = "Use the real app icon",
                    onFind = iconPkg?.let { pkg ->
                        {
                            finding = true
                            findError = null
                            scope.launch {
                                val path = context.telly.logos.appIcon(pkg)
                                if (path != null) s = s.copy(logo = path, autoLogo = true) else findError = "No icon found for $pkg"
                                finding = false
                            }
                        }
                    },
                    onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = { s = s.copy(logo = null, autoLogo = false) },
                )
            }
            findError?.let { Text(it, color = Palette.Muted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = s.name,
                onValueChange = { s = s.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = s.isApp, onClick = { s = s.copy(isApp = true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("App") }
                SegmentedButton(selected = !s.isApp, onClick = { s = s.copy(isApp = false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Shortcut") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Show on the remote screen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = s.pinned, onCheckedChange = { s = s.copy(pinned = it) })
            }
            Spacer(Modifier.height(8.dp))
            Text("Tile colour", style = SectionLabel)
            Spacer(Modifier.height(8.dp))
            ColorPicker(s.color) { s = s.copy(color = it) }

            SectionHeader("Steps", Modifier.padding(top = 16.dp)) {
                TextButton(onClick = { stepEdit = s.steps.size to null }) { Text("Add step") }
            }
            if (s.steps.isEmpty()) {
                Text(
                    if (s.isApp) "Add an \"Open app\" or \"Open link\" step." else "Add the steps to run, in order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
            }
            s.steps.forEachIndexed { i, step ->
                StepRow(
                    step,
                    onClick = { stepEdit = i to step },
                    onUp = if (i > 0) ({ s = s.copy(steps = s.steps.swap(i, i - 1)) }) else null,
                    onDown = if (i < s.steps.lastIndex) ({ s = s.copy(steps = s.steps.swap(i, i + 1)) }) else null,
                    onDelete = { s = s.copy(steps = s.steps.filterIndexed { j, _ -> j != i }) },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { store.update { d -> d.copy(shortcuts = d.shortcuts.filterNot { it.id == s.id }) }; onDismiss() }) {
                        Text("Delete", color = Palette.Power)
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onRun(s) }, enabled = s.steps.isNotEmpty()) { Text("Run") }
                Button(
                    onClick = { store.upsertShortcut(s.copy(name = s.name.trim())); onDismiss() },
                    enabled = s.name.isNotBlank() && s.steps.isNotEmpty(),
                ) { Text("Save") }
            }
        }
    }

    stepEdit?.let { (index, step) ->
        StepDialog(
            initial = step,
            currentApp = tv.currentApp,
            onDismiss = { stepEdit = null },
            onSave = { newStep ->
                s = s.copy(steps = if (step == null) s.steps + newStep else s.steps.mapIndexed { j, old -> if (j == index) newStep else old })
                stepEdit = null
            },
        )
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

@Composable
private fun StepRow(step: Step, onClick: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(stepIcon(step), null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(step.describe(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
        IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove step", tint = Palette.Muted) }
    }
}

private fun stepIcon(step: Step): ImageVector = when (step) {
    is Step.Key -> Icons.Rounded.SettingsRemote
    is Step.Link -> Icons.AutoMirrored.Rounded.OpenInNew
    is Step.App -> Icons.Rounded.Apps
    is Step.Text -> Icons.Rounded.Keyboard
    is Step.Wait -> Icons.Rounded.Schedule
    is Step.Tune -> Icons.Rounded.LiveTv
}

private enum class StepKind(val label: String) { Key("Press key"), App("Open app"), Link("Open link"), Text("Type text"), Wait("Wait"), Tune("Channel") }

private fun Step.kind() = when (this) {
    is Step.Key -> StepKind.Key
    is Step.App -> StepKind.App
    is Step.Link -> StepKind.Link
    is Step.Text -> StepKind.Text
    is Step.Wait -> StepKind.Wait
    is Step.Tune -> StepKind.Tune
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDialog(initial: Step?, currentApp: String?, onDismiss: () -> Unit, onSave: (Step) -> Unit) {
    var kind by remember { mutableStateOf(initial?.kind() ?: StepKind.App) }
    var keyCode by remember { mutableStateOf((initial as? Step.Key)?.code ?: Keys.OK) }
    var hold by remember { mutableStateOf((initial as? Step.Key)?.long ?: false) }
    var pkg by remember { mutableStateOf((initial as? Step.App)?.pkg ?: "") }
    var url by remember { mutableStateOf((initial as? Step.Link)?.url ?: "") }
    var text by remember { mutableStateOf((initial as? Step.Text)?.text ?: "") }
    var wait by remember { mutableStateOf(((initial as? Step.Wait)?.ms ?: 1000).toString()) }
    var channel by remember { mutableStateOf((initial as? Step.Tune)?.number ?: "") }

    val result: Step? = when (kind) {
        StepKind.Key -> Step.Key(keyCode, hold)
        StepKind.App -> pkg.trim().takeIf { it.isNotEmpty() }?.let { Step.App(it) }
        StepKind.Link -> url.trim().takeIf { it.contains(':') }?.let { Step.Link(it) }
        StepKind.Text -> text.takeIf { it.isNotEmpty() }?.let { Step.Text(it) }
        StepKind.Wait -> wait.toIntOrNull()?.takeIf { it in 1..60_000 }?.let { Step.Wait(it) }
        StepKind.Tune -> channel.takeIf { it.isNotEmpty() }?.let { Step.Tune(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add step" else "Edit step") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                }
                Spacer(Modifier.height(12.dp))
                when (kind) {
                    StepKind.Key -> {
                        Text("Key: ${Keys.name(keyCode)}", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        ) {
                            Keys.named.forEach { (code, name) ->
                                FilterChip(selected = code == keyCode, onClick = { keyCode = code }, label = { Text(name) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hold, onCheckedChange = { hold = it })
                            Text("Hold (long press)")
                        }
                    }
                    StepKind.App -> {
                        OutlinedTextField(
                            value = pkg,
                            onValueChange = { pkg = it },
                            label = { Text("Package name") },
                            placeholder = { Text("com.netflix.ninja") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (currentApp != null) {
                            TextButton(onClick = { pkg = currentApp }) { Text("Use app on screen: $currentApp") }
                        } else {
                            Text(
                                "Tip: open the app on the TV, and its package shows up here to pick.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Muted,
                            )
                        }
                    }
                    StepKind.Link -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Link") },
                            placeholder = { Text("https://www.youtube.com/watch?v=…") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "The TV opens the link in whichever app handles it, which lets you jump straight to a video, show or search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Presets.linkTemplates.forEach { (label, prefix) ->
                                AssistChip(onClick = { url = prefix }, label = { Text(label) })
                            }
                        }
                    }
                    StepKind.Text -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Text to type") },
                        supportingText = { Text("Goes into the focused search box on the TV") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StepKind.Wait -> OutlinedTextField(
                        value = wait,
                        onValueChange = { wait = it.filter(Char::isDigit).take(5) },
                        label = { Text("Milliseconds") },
                        supportingText = { Text("Give apps time to open before the next step (1000 = 1 second)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StepKind.Tune -> OutlinedTextField(
                        value = channel,
                        onValueChange = { channel = it.filter(Char::isDigit).take(5) },
                        label = { Text("Channel number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { result?.let(onSave) }, enabled = result != null) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
