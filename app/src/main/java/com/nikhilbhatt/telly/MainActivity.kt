package com.nikhilbhatt.telly

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nikhilbhatt.telly.atv.Keys
import com.nikhilbhatt.telly.atv.LinkStatus
import com.nikhilbhatt.telly.data.Device
import com.nikhilbhatt.telly.ui.AppsScreen
import com.nikhilbhatt.telly.ui.ChannelsScreen
import com.nikhilbhatt.telly.ui.DevicesScreen
import com.nikhilbhatt.telly.ui.LocalHaptics
import com.nikhilbhatt.telly.ui.Palette
import com.nikhilbhatt.telly.ui.RemoteScreen
import com.nikhilbhatt.telly.ui.TellyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Palette.Bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Palette.Surface.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent { TellyTheme { TellyRoot() } }
    }

    /** The phone's volume buttons drive the TV while the remote is open and connected. */
    private fun volumeKeyTarget(keyCode: Int): Int? {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return null
        if (!telly.store.data.value.volumeKeys || telly.link.state.value.status != LinkStatus.Connected) return null
        return if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) Keys.VOLUME_UP else Keys.VOLUME_DOWN
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val target = volumeKeyTarget(keyCode) ?: return super.onKeyDown(keyCode, event)
        telly.controller.key(target)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumeKeyTarget(keyCode) != null || super.onKeyUp(keyCode, event)
}

private enum class Tab(val label: String) { Remote("Remote"), Channels("Channels"), Apps("Apps"), Devices("Devices") }

@Composable
private fun TellyRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.telly
    val data by app.store.data.collectAsStateWithLifecycle()
    val tv by app.link.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (data.devices.isEmpty()) Tab.Devices else Tab.Remote) }
    var pairing by remember { mutableStateOf<Device?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        app.controller.messages.collect { snackbar.showSnackbar(it) }
    }

    CompositionLocalProvider(LocalHaptics provides data.haptics) {
        Scaffold(
            containerColor = Palette.Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Palette.Surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.Remote -> Icons.Rounded.SettingsRemote
                                        Tab.Channels -> Icons.Rounded.LiveTv
                                        Tab.Apps -> Icons.Rounded.Apps
                                        Tab.Devices -> Icons.Rounded.Devices
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Palette.Accent.copy(alpha = 0.2f)),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.Remote -> RemoteScreen(
                        data = data,
                        tv = tv,
                        controller = app.controller,
                        onSelectDevice = { id -> app.store.update { it.copy(activeDeviceId = id) } },
                        onRepair = { pairing = data.activeDevice; tab = Tab.Devices },
                        onRetry = { app.link.reconnect() },
                        onAddDevice = { showAdd = true; tab = Tab.Devices },
                    )
                    Tab.Channels -> ChannelsScreen(data, tv, app.store, app.controller)
                    Tab.Apps -> AppsScreen(data, tv, app.store, app.controller)
                    Tab.Devices -> DevicesScreen(
                        data = data,
                        tv = tv,
                        pairing = pairing,
                        onPair = { pairing = it },
                        onPairingDone = { pairing = null; showAdd = false; tab = Tab.Remote },
                        showAdd = showAdd,
                        onShowAdd = { showAdd = it },
                    )
                }
            }
        }
    }
}
