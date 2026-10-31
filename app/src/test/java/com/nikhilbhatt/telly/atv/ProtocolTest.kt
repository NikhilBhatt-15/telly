package com.nikhilbhatt.telly.atv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** Checks the hand-rolled codec against vectors produced by androidtvremote2's protobuf schemas. */
class ProtocolTest {
    private val vectors = Json.parseToJsonElement(javaClass.getResource("/vectors.json")!!.readText()).jsonObject
    private fun vector(name: String) = hexToBytes(vectors.getValue(name).jsonPrimitive.content)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun outer(body: ProtoWriter.() -> Unit) = ProtoWriter().int(1, 2).int(2, 200).apply(body).toByteArray()

    @Test
    fun pairingMessagesMatchReference() {
        assertEquals(hex(vector("pairing_request")), hex(outer { message(10) { string(1, "atvremote"); string(2, "Telly") } }))
        assertEquals(hex(vector("options")), hex(outer { message(20) { message(1) { int(1, 3); int(2, 6) }; int(3, 1) } }))
        assertEquals(hex(vector("configuration")), hex(outer { message(30) { message(1) { int(1, 3); int(2, 6) }; int(2, 1) } }))
        assertEquals(hex(vector("secret")), hex(outer { message(40) { bytes(1, ByteArray(32) { (it + 1).toByte() }) } }))
        assertTrue(ProtoMessage.parse(vector("configuration_ack")).has(31))
    }

    @Test
    fun remoteMessagesMatchReference() {
        val configure = ProtoWriter().message(1) {
            int(1, 615)
            message(2) { int(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
        }.toByteArray()
        assertEquals(hex(vector("configure_reply")), hex(configure))
        assertEquals(hex(vector("set_active")), hex(ProtoWriter().message(2) { int(1, 615) }.toByteArray()))
        assertEquals(hex(vector("ping_response")), hex(ProtoWriter().message(9) { int(1, 7) }.toByteArray()))
        assertEquals(hex(vector("key_power")), hex(ProtoWriter().message(10) { int(1, Keys.POWER); int(2, KeyDirection.Short.wire) }.toByteArray()))
        assertEquals(hex(vector("app_link")), hex(ProtoWriter().message(90) { string(1, "https://www.netflix.com/title") }.toByteArray()))
        val ime = ProtoWriter().message(21) {
            int(1, 3)
            int(2, 5)
            message(3) { int(1, 1); message(2) { int(1, 4); int(2, 4); string(3, "hello") } }
        }.toByteArray()
        assertEquals(hex(vector("ime_text")), hex(ime))
    }

    @Test
    fun parsesMessagesFromTheTv() {
        val cfg = ProtoMessage.parse(vector("in_configure")).message(1)!!
        assertEquals(622, cfg.int(1))
        assertEquals("XStream", cfg.message(2)!!.string(1))
        assertEquals("Airtel", cfg.message(2)!!.string(2))

        val vol = ProtoMessage.parse(vector("in_volume")).message(50)!!
        assertEquals(37, vol.int(7))
        assertEquals(100, vol.int(6))
        assertTrue(vol.bool(8))

        assertEquals("com.netflix.ninja", ProtoMessage.parse(vector("in_current_app")).message(20)!!.message(1)!!.string(12))
        assertTrue(ProtoMessage.parse(vector("in_start")).message(40)!!.bool(1))
        assertEquals(1234567L, ProtoMessage.parse(vector("in_ping")).message(8)!!.long(1))
    }

    @Test
    fun pairingSecretMatchesReference() {
        val client = ClientIdentity.parseCertificate(vector("pair_client_cert")).publicKey as RSAPublicKey
        val server = ClientIdentity.parseCertificate(vector("pair_server_cert")).publicKey as RSAPublicKey
        val code = vectors.getValue("pair_code").jsonPrimitive.content
        val secret = pairingSecret(client, server, code)
        assertEquals(vectors.getValue("pair_secret").jsonPrimitive.content, hex(secret))
        assertEquals(code.substring(0, 2).toInt(16).toByte(), secret[0])
    }

    @Test
    fun framesSurviveRoundTripAndSplitReads() {
        val out = ByteArrayOutputStream()
        val big = ByteArray(300) { it.toByte() }
        writeFrame(out, vector("key_power"))
        writeFrame(out, big)
        // Deliver one byte per read() to exercise partial reads.
        val trickle = object : java.io.InputStream() {
            val src = ByteArrayInputStream(out.toByteArray())
            override fun read() = src.read()
            override fun read(b: ByteArray, off: Int, len: Int) = if (len == 0) 0 else src.read(b, off, 1)
        }
        assertArrayEquals(vector("key_power"), readFrame(trickle))
        assertArrayEquals(big, readFrame(trickle))
        assertEquals(null, readFrame(trickle))
    }

    @Test
    fun generatedCertificateIsValidAndWorksForTlsClientAuth() {
        val client = ClientIdentity.generate("Telly")
        client.certificate.verify(client.publicKey)
        assertEquals(3, client.certificate.version)
        assertEquals(2048, client.publicKey.modulus.bitLength())

        // A loopback TLS server that demands a client certificate, like the TV does.
        val serverIdentity = ClientIdentity.generate("atvremote/test/test/Fake TV/AA:BB:CC:DD:EE:FF")
        val serverCtx = javax.net.ssl.SSLContext.getInstance("TLS").apply {
            val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("s", serverIdentity.privateKey, CharArray(0), arrayOf(serverIdentity.certificate))
            }
            val kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
            val trustAll = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) = Unit
                override fun checkServerTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) = Unit
                override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
            }
            init(kmf.keyManagers, arrayOf(trustAll), null)
        }
        val server = serverCtx.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        server.needClientAuth = true
        var seenClientKey: RSAPublicKey? = null
        val t = thread {
            (server.accept() as SSLSocket).use { s ->
                s.startHandshake()
                seenClientKey = s.session.peerCertificates[0].publicKey as RSAPublicKey
                writeFrame(s.outputStream, readFrame(s.inputStream)!!)
            }
        }
        (client.socketFactory.createSocket("127.0.0.1", server.localPort) as SSLSocket).use { s ->
            writeFrame(s.outputStream, vector("key_power"))
            assertArrayEquals(vector("key_power"), readFrame(s.inputStream))
            assertEquals(serverIdentity.publicKey, s.session.peerCertificates[0].publicKey)
        }
        t.join(5_000)
        server.close()
        assertEquals(client.publicKey, seenClientKey)
    }
}
