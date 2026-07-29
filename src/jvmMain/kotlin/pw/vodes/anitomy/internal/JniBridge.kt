package pw.vodes.anitomy.internal

import pw.vodes.anitomy.Element
import pw.vodes.anitomy.ElementKind

internal object JniBridge {
    init {
        NativeLibraryLoader.load()
    }

    fun parse(input: String, options: Int): List<Element> =
        WireResultDecoder.decode(JniBindings.parse(input.encodeToByteArray(), options))
}

private object WireResultDecoder {
    private const val HEADER_SIZE = 12
    private const val RECORD_HEADER_SIZE = 16

    fun decode(bytes: ByteArray): List<Element> {
        val cursor = ByteCursor(bytes)
        check(cursor.readByte() == 'A'.code)
        check(cursor.readByte() == 'K'.code)
        check(cursor.readByte() == 'M'.code)
        check(cursor.readByte() == 'P'.code) {
            "Invalid Anitomy JNI result header"
        }

        val version = cursor.readU16()
        check(version == 1) { "Unsupported Anitomy JNI result version: $version" }
        cursor.readU16()

        val count = cursor.readU32AsInt()
        check(count <= (bytes.size - HEADER_SIZE) / RECORD_HEADER_SIZE) {
            "Invalid Anitomy JNI element count: $count"
        }

        val result = ArrayList<Element>(count)
        repeat(count) {
            val kindId = cursor.readU32AsInt()
            val kind =
                ElementKind.entries.getOrNull(kindId)
                    ?: error("Unknown Anitomy element kind: $kindId")
            val position = cursor.readU64AsLong()
            val valueLength = cursor.readU32AsInt()
            val value = cursor.readBytes(valueLength).decodeToString(throwOnInvalidSequence = true)
            result += Element(kind, value, position)
        }
        check(cursor.remaining == 0) { "Trailing data in Anitomy JNI result" }
        return result
    }
}

private class ByteCursor(
    private val bytes: ByteArray,
) {
    private var position: Int = 0

    val remaining: Int
        get() = bytes.size - position

    fun readByte(): Int {
        requireAvailable(1)
        return bytes[position++].toInt() and 0xff
    }

    fun readU16(): Int =
        readByte() or (readByte() shl 8)

    fun readU32AsInt(): Int {
        val value =
            readByte().toLong() or
                (readByte().toLong() shl 8) or
                (readByte().toLong() shl 16) or
                (readByte().toLong() shl 24)
        check(value <= Int.MAX_VALUE) { "Anitomy JNI value exceeds Kotlin collection limits" }
        return value.toInt()
    }

    fun readU64AsLong(): Long {
        var value = 0L
        repeat(8) { index ->
            val byte = readByte().toLong()
            if (index == 7) {
                check(byte and 0x80 == 0L) { "Anitomy position exceeds Long.MAX_VALUE" }
            }
            value = value or (byte shl (index * 8))
        }
        return value
    }

    fun readBytes(count: Int): ByteArray {
        requireAvailable(count)
        val result = bytes.copyOfRange(position, position + count)
        position += count
        return result
    }

    private fun requireAvailable(count: Int) {
        check(count >= 0 && count <= remaining) { "Truncated Anitomy JNI result" }
    }
}
