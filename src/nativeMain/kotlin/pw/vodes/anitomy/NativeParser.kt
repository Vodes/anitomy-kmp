@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package pw.vodes.anitomy

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import pw.vodes.anitomy.cinterop.anitomy_parse_utf8
import pw.vodes.anitomy.cinterop.anitomy_result_count
import pw.vodes.anitomy.cinterop.anitomy_result_destroy
import pw.vodes.anitomy.cinterop.anitomy_result_kind
import pw.vodes.anitomy.cinterop.anitomy_result_position
import pw.vodes.anitomy.cinterop.anitomy_result_value

internal actual fun parsePlatform(input: String, options: Options): List<Element> {
    val inputBytes = input.encodeToByteArray()
    val result =
        inputBytes.usePinned { pinned ->
            anitomy_parse_utf8(
                if (inputBytes.isEmpty()) null else pinned.addressOf(0).reinterpret<ByteVar>(),
                inputBytes.size.convert(),
                options.toBitMask().toUInt(),
            )
        } ?: error("Anitomy could not allocate a parse result")

    try {
        val count = anitomy_result_count(result).toLong()
        check(count <= Int.MAX_VALUE) { "Anitomy returned too many elements" }
        return memScoped {
            val valueLength = alloc<ULongVar>()
            List(count.toInt()) { index ->
                val valuePointer =
                    anitomy_result_value(result, index.convert(), valueLength.ptr)
                        ?: error("Anitomy returned a null element value")
                val length = valueLength.value.toLong()
                check(length <= Int.MAX_VALUE) { "Anitomy returned an oversized element" }
                val value = valuePointer.readBytes(length.toInt()).decodeToString(throwOnInvalidSequence = true)
                val kindId = anitomy_result_kind(result, index.convert()).toInt()
                val kind =
                    ElementKind.entries.getOrNull(kindId)
                        ?: error("Unknown Anitomy element kind: $kindId")
                val position = anitomy_result_position(result, index.convert()).toLong()
                check(position >= 0) { "Anitomy element position exceeds Long.MAX_VALUE" }
                Element(kind, value, position)
            }
        }
    } finally {
        anitomy_result_destroy(result)
    }
}
