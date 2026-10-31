package com.nikhilbhatt.telly.atv

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * The client certificate this app presents to the TV. The TV remembers it after pairing, so it
 * must stay the same across launches: it is generated once and kept in app-private storage.
 */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {
    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    val socketFactory: SSLSocketFactory by lazy {
        val password = CharArray(0)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("client", privateKey, password, arrayOf(certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(TrustAnyServer), SecureRandom()) }.socketFactory
    }

    fun save(dir: File) {
        dir.mkdirs()
        File(dir, KEY_FILE).writeBytes(privateKey.encoded)
        File(dir, CERT_FILE).writeBytes(certificate.encoded)
    }

    companion object {
        private const val KEY_FILE = "client.key"
        private const val CERT_FILE = "client.crt"
        private const val DAY = 24L * 60 * 60 * 1000

        fun loadOrCreate(dir: File, commonName: String): ClientIdentity {
            val key = File(dir, KEY_FILE)
            val cert = File(dir, CERT_FILE)
            if (key.isFile && cert.isFile) {
                runCatching {
                    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key.readBytes()))
                    return ClientIdentity(privateKey, parseCertificate(cert.readBytes()))
                }
            }
            return generate(commonName).also { it.save(dir) }
        }

        /** A self-signed RSA-2048 certificate, the same shape the official remote apps use. */
        fun generate(commonName: String): ClientIdentity {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val sha256WithRsa = Der.seq(Der.oid(1, 2, 840, 113549, 1, 1, 11), Der.nul())
            val name = Der.seq(Der.set(Der.seq(Der.oid(2, 5, 4, 3), Der.utf8(commonName))))
            val basicConstraints = Der.seq(
                Der.oid(2, 5, 29, 19),
                Der.octets(Der.seq(Der.bool(true), Der.int(BigInteger.ZERO))),
            )
            val now = System.currentTimeMillis()
            val tbs = Der.seq(
                Der.explicit(0, Der.int(BigInteger.valueOf(2))),
                Der.int(BigInteger(63, SecureRandom()).add(BigInteger.ONE)),
                sha256WithRsa,
                name,
                Der.seq(Der.time(now - DAY), Der.time(now + 20 * 365 * DAY)),
                name,
                pair.public.encoded,
                Der.explicit(3, Der.seq(basicConstraints)),
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(pair.private)
                update(tbs)
                sign()
            }
            val der = Der.seq(tbs, sha256WithRsa, Der.bits(signature))
            return ClientIdentity(pair.private, parseCertificate(der))
        }

        fun parseCertificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }
}

/**
 * The pairing secret: SHA-256 over both RSA keys and the last four hex digits of the code on the
 * TV screen. The first two digits of that code are the first byte of this hash, which lets us
 * reject a mistyped code before sending it.
 */
fun pairingSecret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(client.modulus.unsignedBytes())
    digest.update(client.publicExponent.unsignedBytes())
    digest.update(server.modulus.unsignedBytes())
    digest.update(server.publicExponent.unsignedBytes())
    digest.update(hexToBytes(code.substring(2)))
    return digest.digest()
}

internal fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Odd-length hex" }
    return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun BigInteger.unsignedBytes(): ByteArray {
    val bytes = toByteArray()
    return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}

/** TVs present self-signed certificates, so there is no chain to verify; pairing is what authenticates. */
private object TrustAnyServer : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Minimal DER encoder for building the certificate above. */
internal object Der {
    fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { parts.forEach(::write) }.toByteArray()
        return byteArrayOf(tag.toByte()) + length(body.size) + body
    }

    private fun length(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    fun seq(vararg parts: ByteArray) = tlv(0x30, *parts)
    fun set(vararg parts: ByteArray) = tlv(0x31, *parts)
    fun int(value: BigInteger) = tlv(0x02, value.toByteArray())
    fun bool(value: Boolean) = tlv(0x01, byteArrayOf(if (value) 0xFF.toByte() else 0))
    fun nul() = byteArrayOf(0x05, 0x00)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    fun octets(bytes: ByteArray) = tlv(0x04, bytes)
    fun bits(bytes: ByteArray) = tlv(0x03, byteArrayOf(0) + bytes)
    fun explicit(n: Int, body: ByteArray) = tlv(0xA0 or n, body)

    fun oid(vararg arcs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf((arc and 0x7F).toInt())
            var v = arc ushr 7
            while (v > 0) {
                groups += ((v and 0x7F) or 0x80).toInt()
                v = v ushr 7
            }
            groups.asReversed().forEach(out::write)
        }
        return tlv(0x06, out.toByteArray())
    }

    /** UTCTime until 2049, GeneralizedTime after, as RFC 5280 requires. */
    fun time(epochMillis: Long): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = Calendar.getInstance(utc).apply { timeInMillis = epochMillis }.get(Calendar.YEAR)
        val (tag, pattern) = if (year < 2050) 0x17 to "yyMMddHHmmss'Z'" else 0x18 to "yyyyMMddHHmmss'Z'"
        val text = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        return tlv(tag, text.toByteArray(Charsets.US_ASCII))
    }
}
