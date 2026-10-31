package com.nikhilbhatt.telly

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nikhilbhatt.telly.atv.ClientIdentity
import com.nikhilbhatt.telly.atv.PairingSession
import com.nikhilbhatt.telly.atv.TvLink
import com.nikhilbhatt.telly.data.LogoFinder
import com.nikhilbhatt.telly.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TellyApp : Application() {
    val scope: CoroutineScope = MainScope()
    lateinit var store: Store
        private set
    lateinit var link: TvLink
        private set
    lateinit var controller: Controller
        private set
    lateinit var logos: LogoFinder
        private set

    /** App tiles already looked up this session, so a missing icon isn't fetched again and again. */
    private val iconAttempts = mutableSetOf<String>()

    private val identityLock = Mutex()
    private var identity: ClientIdentity? = null

    /** Created on first use; RSA key generation takes a moment, so it runs off the main thread. */
    suspend fun identity(): ClientIdentity = identityLock.withLock {
        identity ?: withContext(Dispatchers.Default) {
            ClientIdentity.loadOrCreate(File(filesDir, "identity"), "Telly")
        }.also { identity = it }
    }

    suspend fun startPairing(host: String): PairingSession = withContext(Dispatchers.IO) {
        PairingSession(host, identity(), clientName = "Telly").apply {
            try {
                start()
            } catch (e: Exception) {
                close()
                throw e
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(filesDir, scope)
        link = TvLink(scope) { identity() }
        controller = Controller(scope, link, store)
        logos = LogoFinder { store.logoDir }

        store.data
            .map { it.activeDevice?.linkTarget() }
            .distinctUntilChanged()
            .onEach { link.setTarget(it) }
            .launchIn(scope)

        store.data
            .map { d -> d.shortcuts.filter { it.isApp && it.logo == null && it.autoLogo }.mapNotNull { s -> LogoFinder.iconPackage(s)?.let { s.id to it } } }
            .distinctUntilChanged()
            .onEach { missing -> missing.filter { iconAttempts.add(it.first) }.forEach { (id, pkg) -> scope.launch { fetchIcon(id, pkg) } } }
            .launchIn(scope)

        // Hold the connection only while the app is visible; the TV drops idle clients anyway.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = link.resume()
            override fun onStop(owner: LifecycleOwner) = link.pause()
        })
    }
}

private suspend fun TellyApp.fetchIcon(shortcutId: String, pkg: String) {
    val path = logos.appIcon(pkg) ?: return
    store.update { d ->
        d.copy(shortcuts = d.shortcuts.map { s -> if (s.id == shortcutId && s.logo == null) s.copy(logo = path) else s })
    }
}

val Context.telly: TellyApp get() = applicationContext as TellyApp
