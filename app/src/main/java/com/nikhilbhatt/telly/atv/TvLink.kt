package com.nikhilbhatt.telly.atv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.coroutines.coroutineContext

enum class LinkStatus { Idle, Connecting, Connected, Offline, NeedsPairing }

data class Volume(val level: Int, val max: Int, val muted: Boolean)

data class TvState(
    val deviceId: String? = null,
    val status: LinkStatus = LinkStatus.Idle,
    /** False when the TV is in standby (screen off but still on the network). */
    val screenOn: Boolean? = null,
    /** Package of the app in the foreground, reported when app tracking is enabled. */
    val currentApp: String? = null,
    val volume: Volume? = null,
    val vendor: String? = null,
    val model: String? = null,
)

enum class KeyDirection(val wire: Int) { StartLong(1), EndLong(2), Short(3) }

data class LinkTarget(val deviceId: String, val host: String, val trackApps: Boolean)

/**
 * Keeps one connection to the remote port (6466) of the selected device while the app is in
 * the foreground, reconnecting with backoff when it drops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TvLink(
    private val scope: CoroutineScope,
    private val identity: suspend () -> ClientIdentity,
) {
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state.asStateFlow()

    /** Sends go through one thread so key presses arrive in the order they were made. */
    private val sendDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var target: LinkTarget? = null
    private var running = false
    private var job: Job? = null

    @Volatile private var connection: Connection? = null

    fun setTarget(newTarget: LinkTarget?) {
        if (newTarget == target) return
        target = newTarget
        _state.value = TvState(deviceId = newTarget?.deviceId)
        restart()
    }

    fun resume() {
        if (running) return
        running = true
        restart()
    }

    fun pause() {
        running = false
        restart()
    }

    /** Retry right away, e.g. after pairing or after waking the TV. */
    fun reconnect() = restart()

    private fun restart() {
        job?.cancel()
        connection?.close()
        connection = null
        val t = target
        if (t == null || !running) {
            _state.update { it.copy(status = LinkStatus.Idle) }
            return
        }
        job = scope.launch(Dispatchers.IO) { connectLoop(t) }
    }

    private suspend fun connectLoop(t: LinkTarget) {
        var backoff = 1_000L
        while (coroutineContext.isActive) {
            if (_state.value.status != LinkStatus.Connected) {
                _state.update { it.copy(status = LinkStatus.Connecting) }
            }
            val conn = Connection(t.host, identity(), t.trackApps)
            // Blocking socket calls ignore cancellation, so closing the socket is what stops them.
            val closer = coroutineContext[Job]?.invokeOnCompletion { conn.close() }
            connection = conn
            var lost: Exception? = null
            try {
                conn.open()
                conn.run()
            } catch (e: Exception) {
                lost = e
            } finally {
                closer?.dispose()
                conn.close()
                if (connection === conn) connection = null
            }
            if (!coroutineContext.isActive) return
            // A TV that doesn't know our certificate rejects it during the TLS handshake (TLS 1.2)
            // or drops the connection right after it (TLS 1.3). Either way no message ever arrives.
            val rejected = !conn.gotMessage && when {
                !conn.handshakeDone -> lost is SSLException
                else -> lost is IOException && lost !is SocketTimeoutException
            }
            if (rejected) {
                _state.update { it.copy(status = LinkStatus.NeedsPairing, screenOn = null) }
                return
            }
            if (conn.gotMessage) backoff = 1_000L
            _state.update { it.copy(status = LinkStatus.Offline, screenOn = null) }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(8_000L)
        }
    }

    /** Waits until connected, for callers that want to send right after picking a device. */
    suspend fun awaitConnected(timeoutMs: Long = 5_000): Boolean =
        withTimeoutOrNull(timeoutMs) { state.first { it.status == LinkStatus.Connected } } != null

    suspend fun sendKey(code: Int, direction: KeyDirection = KeyDirection.Short): Boolean = send {
        message(F_KEY_INJECT) {
            int(1, code)
            int(2, direction.wire)
        }
    }

    /** Opens a URL with whichever app on the TV handles it, or a package via market://launch?id=. */
    suspend fun sendLink(link: String): Boolean = send {
        message(F_APP_LINK) { string(1, link) }
    }

    /** Types into the focused text field on the TV. */
    suspend fun sendText(text: String): Boolean {
        if (text.isEmpty()) return false
        val conn = connection ?: return false
        return send {
            message(F_IME_BATCH_EDIT) {
                int(1, conn.imeCounter)
                int(2, conn.imeFieldCounter)
                message(3) {
                    int(1, 1)
                    message(2) {
                        int(1, text.length - 1)
                        int(2, text.length - 1)
                        string(3, text)
                    }
                }
            }
        }
    }

    private suspend fun send(body: ProtoWriter.() -> Unit): Boolean = withContext(sendDispatcher) {
        val conn = connection ?: return@withContext false
        runCatching { conn.send(ProtoWriter().apply(body).toByteArray()) }
            .onFailure { conn.close() }
            .isSuccess
    }

    private inner class Connection(
        private val host: String,
        private val identity: ClientIdentity,
        trackApps: Boolean,
    ) {
        private var socket: SSLSocket? = null
        private var features = Feature.PING or Feature.KEY or Feature.POWER or Feature.VOLUME or Feature.APP_LINK or
            (if (trackApps) Feature.IME else 0)

        @Volatile var handshakeDone = false
        @Volatile var gotMessage = false
        @Volatile var imeCounter = 0
        @Volatile var imeFieldCounter = 0

        fun open() {
            val s = identity.socketFactory.createSocket() as SSLSocket
            socket = s
            s.connect(InetSocketAddress(host, REMOTE_PORT), CONNECT_TIMEOUT_MS)
            // The TV pings every 5 s, so three missed pings means the link is dead.
            s.soTimeout = IDLE_TIMEOUT_MS
            s.startHandshake()
            handshakeDone = true
        }

        fun run() {
            val input = socket?.inputStream ?: throw IOException("Not connected")
            while (true) {
                val frame = readFrame(input) ?: throw EOFException("Connection closed by the TV")
                gotMessage = true
                handle(ProtoMessage.parse(frame))
            }
        }

        @Synchronized
        fun send(message: ByteArray) {
            val s = socket ?: throw IOException("Not connected")
            writeFrame(s.outputStream, message)
        }

        fun close() {
            runCatching { socket?.close() }
        }

        private fun handle(msg: ProtoMessage) {
            msg.message(F_CONFIGURE)?.let { cfg ->
                features = features and cfg.int(1)
                val info = cfg.message(2)
                _state.update { it.copy(vendor = info?.string(2)?.ifBlank { null }, model = info?.string(1)?.ifBlank { null }) }
                send(
                    ProtoWriter().message(F_CONFIGURE) {
                        int(1, features)
                        message(2) {
                            int(3, 1)
                            string(4, "1")
                            string(5, "atvremote")
                            string(6, "1.0.0")
                        }
                    }.toByteArray(),
                )
            }
            if (msg.has(F_SET_ACTIVE)) {
                send(ProtoWriter().message(F_SET_ACTIVE) { int(1, features) }.toByteArray())
            }
            msg.message(F_PING_REQUEST)?.let { ping ->
                send(ProtoWriter().message(F_PING_RESPONSE) { int(1, ping.long(1)) }.toByteArray())
            }
            msg.message(F_START)?.let { start ->
                _state.update { it.copy(status = LinkStatus.Connected, screenOn = start.bool(1)) }
            }
            msg.message(F_SET_VOLUME)?.let { v ->
                _state.update { it.copy(volume = Volume(level = v.int(7), max = v.int(6), muted = v.bool(8))) }
            }
            msg.message(F_IME_KEY_INJECT)?.message(1)?.string(12)?.takeIf { it.isNotBlank() }?.let { pkg ->
                _state.update { it.copy(currentApp = pkg) }
            }
            msg.message(F_IME_BATCH_EDIT)?.let { edit ->
                imeCounter = edit.int(1)
                imeFieldCounter = edit.int(2)
            }
        }
    }

    private object Feature {
        const val PING = 1
        const val KEY = 2
        const val IME = 4
        const val POWER = 32
        const val VOLUME = 64
        const val APP_LINK = 512
    }

    companion object {
        const val REMOTE_PORT = 6466
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val IDLE_TIMEOUT_MS = 16_000

        // RemoteMessage field numbers (remotemessage.proto)
        private const val F_CONFIGURE = 1
        private const val F_SET_ACTIVE = 2
        private const val F_PING_REQUEST = 8
        private const val F_PING_RESPONSE = 9
        private const val F_KEY_INJECT = 10
        private const val F_IME_KEY_INJECT = 20
        private const val F_IME_BATCH_EDIT = 21
        private const val F_START = 40
        private const val F_SET_VOLUME = 50
        private const val F_APP_LINK = 90
    }
}
