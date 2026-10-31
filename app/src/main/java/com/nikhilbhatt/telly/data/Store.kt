package com.nikhilbhatt.telly.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** All app data in one small JSON file, exposed as a StateFlow and saved on every change. */
@OptIn(ExperimentalCoroutinesApi::class)
class Store(private val dir: File, private val scope: CoroutineScope) {
    private val file = File(dir, "telly.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val writer = Dispatchers.IO.limitedParallelism(1)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    val logoDir: File get() = File(dir, "logos").apply { mkdirs() }

    private fun load(): AppData =
        runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrNull() ?: AppData()

    fun update(transform: (AppData) -> AppData) {
        _data.update(transform)
        val snapshot = _data.value
        scope.launch(writer) {
            val tmp = File(dir, "telly.json.tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            cleanUnusedLogos(snapshot)
        }
    }

    private fun cleanUnusedLogos(data: AppData) {
        val used = (data.channels.map { it.logo } + data.shortcuts.map { it.logo }).toSet()
        // Leave fresh files alone: an editor may have copied a picked image but not saved yet.
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        logoDir.listFiles()?.filter { it.absolutePath !in used && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun upsertDevice(device: Device) = update { d ->
        val exists = d.devices.any { it.id == device.id }
        d.copy(devices = if (exists) d.devices.map { if (it.id == device.id) device else it } else d.devices + device)
    }

    fun upsertChannel(channel: Channel) = update { d ->
        val exists = d.channels.any { it.id == channel.id }
        d.copy(channels = if (exists) d.channels.map { if (it.id == channel.id) channel else it } else d.channels + channel)
    }

    fun upsertShortcut(shortcut: Shortcut) = update { d ->
        val exists = d.shortcuts.any { it.id == shortcut.id }
        d.copy(shortcuts = if (exists) d.shortcuts.map { if (it.id == shortcut.id) shortcut else it } else d.shortcuts + shortcut)
    }
}
