package com.nikhilbhatt.telly.atv

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Just enough protobuf for the Android TV remote protocol. Every message it uses is made of
 * varints, strings, bytes and nested messages, so a hand-rolled codec beats pulling in protoc.
 * Fields must be written in ascending field-number order to match what the official encoder emits.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long) = apply {
        tag(field, WIRE_VARINT)
        writeVarint(out, value)
    }

    fun int(field: Int, value: Int) = int(field, value.toLong())

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, WIRE_LEN)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = writeVarint(out, ((field shl 3) or wire).toLong())
}

/** A decoded message: every field number maps to its values (Long for varints, ByteArray otherwise). */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun long(field: Int): Long = fields[field]?.lastOrNull() as? Long ?: 0L
    fun int(field: Int): Int = long(field).toInt()
    fun bool(field: Int): Boolean = long(field) != 0L
    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray
    fun string(field: Int): String = bytes(field)?.toString(Charsets.UTF_8) ?: ""
    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw IOException("Truncated varint")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IOException("Varint too long")
                }
            }
            while (pos < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any = when ((key and 7).toInt()) {
                    WIRE_VARINT -> varint()
                    WIRE_LEN -> {
                        val len = varint().toInt()
                        if (len < 0 || pos + len > data.size) throw IOException("Truncated field $field")
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    WIRE_FIXED64 -> { pos += 8; continue }
                    WIRE_FIXED32 -> { pos += 4; continue }
                    else -> throw IOException("Unsupported wire type in field $field")
                }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LEN = 2
private const val WIRE_FIXED32 = 5
private const val MAX_FRAME = 1 shl 20

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

/** Messages on the wire are prefixed with their length as a varint. */
internal fun writeFrame(out: OutputStream, message: ByteArray) {
    val buf = ByteArrayOutputStream(message.size + 5)
    writeVarint(buf, message.size.toLong())
    buf.write(message)
    out.write(buf.toByteArray())
    out.flush()
}

/** Reads one length-prefixed message, or returns null if the stream ended cleanly between messages. */
internal fun readFrame(input: InputStream): ByteArray? {
    var len = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) {
            if (shift == 0) return null
            throw IOException("Stream ended inside a frame header")
        }
        len = len or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift > 28) throw IOException("Frame length too long")
    }
    if (len > MAX_FRAME) throw IOException("Frame of $len bytes is too large")
    return ByteArray(len.toInt()).also { DataInputStream(input).readFully(it) }
}
