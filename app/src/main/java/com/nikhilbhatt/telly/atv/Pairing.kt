package com.nikhilbhatt.telly.atv

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/**
 * The "polo" pairing handshake on port 6467. [start] makes the TV show a six-character code,
 * [finish] proves we saw it. Afterwards the TV accepts our certificate on the remote port.
 *
 * Both calls block, so run them off the main thread.
 */
class PairingSession(
    private val host: String,
    private val identity: ClientIdentity,
    private val clientName: String,
    private val port: Int = PAIRING_PORT,
) : Closeable {
    private var socket: SSLSocket? = null
    private var serverCert: X509Certificate? = null

    /** The name the TV announced, e.g. "Airtel Xstream" (null until [start] succeeds). */
    var serverName: String? = null
        private set

    /** The TV's MAC address, which TVs embed in their certificate. Useful for Wake-on-LAN. */
    val serverMac: String? get() = serverCert?.subjectX500Principal?.name?.let { MAC.find(it)?.value?.uppercase() }

    fun start() {
        val s = identity.socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, port), TIMEOUT_MS)
        s.soTimeout = TIMEOUT_MS
        s.startHandshake()
        serverCert = s.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw PairingException("The TV did not present a certificate")

        send {
            message(F_PAIRING_REQUEST) {
                string(1, "atvremote")
                string(2, clientName)
            }
        }
        serverName = expect(F_PAIRING_REQUEST_ACK).message(F_PAIRING_REQUEST_ACK)?.string(1)?.ifBlank { null }
        send {
            message(F_OPTIONS) {
                message(1) { hexEncoding() }
                int(3, ROLE_INPUT)
            }
        }
        expect(F_OPTIONS)
        send {
            message(F_CONFIGURATION) {
                message(1) { hexEncoding() }
                int(2, ROLE_INPUT)
            }
        }
        expect(F_CONFIGURATION_ACK)
    }

    fun finish(code: String) {
        val pin = code.trim().uppercase()
        if (!PIN.matches(pin)) throw PairingException("The code is 6 characters, using 0-9 and A-F")
        val server = serverCert?.publicKey as? RSAPublicKey ?: throw PairingException("Pairing was not started")
        val secret = pairingSecret(identity.publicKey, server, pin)
        if (secret[0] != pin.substring(0, 2).toInt(16).toByte()) {
            throw PairingException("That code doesn't match the one on the TV. Check for typos")
        }
        send { message(F_SECRET) { bytes(1, secret) } }
        expect(F_SECRET_ACK)
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun ProtoWriter.hexEncoding() {
        int(1, ENCODING_HEX)
        int(2, 6)
    }

    private fun send(body: ProtoWriter.() -> Unit) {
        val s = socket ?: throw PairingException("Not connected")
        val message = ProtoWriter().int(1, PROTOCOL_VERSION).int(2, STATUS_OK).apply(body).toByteArray()
        writeFrame(s.outputStream, message)
    }

    private fun expect(field: Int): ProtoMessage {
        val s = socket ?: throw PairingException("Not connected")
        val frame = readFrame(s.inputStream) ?: throw PairingException("The TV closed the connection")
        val message = ProtoMessage.parse(frame)
        when (val status = message.int(2)) {
            STATUS_OK -> Unit
            STATUS_BAD_SECRET -> throw PairingException("The TV rejected the code")
            else -> throw PairingException("The TV refused pairing (status $status)")
        }
        if (!message.has(field)) throw PairingException("Unexpected reply from the TV")
        return message
    }

    companion object {
        const val PAIRING_PORT = 6467
        private const val TIMEOUT_MS = 10_000
        private const val PROTOCOL_VERSION = 2
        private const val STATUS_OK = 200
        private const val STATUS_BAD_SECRET = 402
        private const val ROLE_INPUT = 1
        private const val ENCODING_HEX = 3

        // OuterMessage field numbers (polo.proto)
        private const val F_PAIRING_REQUEST = 10
        private const val F_PAIRING_REQUEST_ACK = 11
        private const val F_OPTIONS = 20
        private const val F_CONFIGURATION = 30
        private const val F_CONFIGURATION_ACK = 31
        private const val F_SECRET = 40
        private const val F_SECRET_ACK = 41

        private val PIN = Regex("[0-9A-F]{6}")
        private val MAC = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    }
}
