package ext

import java.nio.ByteBuffer

fun ByteBuffer.medium(): Int {
    return (((get().toInt() shl 16) and 0xff0000) + ((get().toInt() shl 8) and 0xff00) + (get().toInt() and 0xff))
//    return ((get().toInt() and 0xff) + (get().toInt() shl 8 and 0xff00) + )
}

fun ByteBuffer.putJagexString(string: String) {
    put(0)
    put(string.toByteArray())
    put(0)
}

fun ByteBuffer.putJagexStringQuickChat(string: String) {
    put(1)
    put(string.toByteArray())
    put(0)
}

fun ByteBuffer.getJagexString(): String {
    val stringBuilder = StringBuilder()
    var b: Byte
    while (get().also { b = it }.toInt() != 0) {
        stringBuilder.append(b.toInt().toChar())
    }
    return stringBuilder.toString()
}

fun ByteBuffer.readNullCircumfixedString(): String {
    if (unsignedByte() != 0)
        throw IllegalArgumentException("byte != 0 infront of null-circumfixed string")
    return getJagexString()
}

fun ByteBuffer.unsignedByte(): Int {
    return get().toInt() and 0xFF
}

fun ByteBuffer.unsignedShort(): Int {
    return short.toInt() and 0xFFFF
}

fun ByteBuffer.getBigSmart(): Int {
    var value = 0
    var current: Int = getSmart()
    while (current == 32767) {
        current = getSmart()
        value += 32767
    }
    value += current
    return value
}

fun ByteBuffer.getSmart(): Int {
    val peek: Int = get().toInt() and 0xFF
    return if (peek <= Byte.MAX_VALUE) {
        peek
    } else (peek shl 8 or (get().toInt() and 0xFF)) - 32768
}