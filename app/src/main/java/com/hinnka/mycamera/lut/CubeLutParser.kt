package com.hinnka.mycamera.lut

import com.hinnka.mycamera.utils.BoundedTextLineReader
import java.io.InputStream

/** Strict red-fastest 3D CUBE reader. Domain declarations describe INPUT coordinates. */
object CubeLutParser {
    const val MAX_DIMENSION = 65

    fun parse(inputStream: InputStream): LutConfig {
        var title = ""
        var size = 0
        var minimum = floatArrayOf(0f, 0f, 0f)
        var maximum = floatArrayOf(1f, 1f, 1f)
        val headers = mutableSetOf<String>()
        var data: FloatArray? = null
        var index = 0
        var lineNumber = 0
        var characters = 0L
        fun triple(value: String): FloatArray {
            val parts = value.trim().split(Regex("\\s+"))
            require(parts.size == 3) { "CUBE line $lineNumber: expected exactly three numbers" }
            return FloatArray(3) { channel ->
                parts[channel].toFloatOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("CUBE line $lineNumber: non-finite or invalid number")
            }
        }
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            BoundedTextLineReader.forEachLine(reader) { raw ->
                lineNumber++
                characters += raw.length + 1L
                require(characters <= 64L * 1024 * 1024) { "CUBE exceeds 64 MiB text limit" }
                val text = raw.trim().removePrefix("\uFEFF")
                var quoted = false
                val comment = text.indexOfFirst { character ->
                    if (character == '"') quoted = !quoted
                    character == '#' && !quoted
                }
                val line = (if (comment >= 0) text.substring(0, comment) else text).trim()
                if (line.isEmpty()) return@forEachLine
                val key = line.takeWhile { !it.isWhitespace() }
                if (key in setOf("TITLE", "LUT_3D_SIZE", "DOMAIN_MIN", "DOMAIN_MAX")) {
                    require(index == 0) { "CUBE line $lineNumber: headers must precede samples" }
                    require(headers.add(key)) { "CUBE line $lineNumber: duplicate $key" }
                    val value = line.removePrefix(key).trim()
                    when (key) {
                        "TITLE" -> title = value.removeSurrounding("\"")
                        "LUT_3D_SIZE" -> {
                            size = value.toIntOrNull() ?: 0
                            require(size in 2..MAX_DIMENSION) { "CUBE dimension must be 2..$MAX_DIMENSION" }
                            data = FloatArray(size * size * size * 3)
                        }
                        "DOMAIN_MIN" -> minimum = triple(value)
                        "DOMAIN_MAX" -> maximum = triple(value)
                    }
                } else {
                    require(!key.startsWith("LUT_1D")) { "Combined/1D CUBE LUTs are unsupported" }
                    val target = requireNotNull(data) { "CUBE samples require a preceding LUT_3D_SIZE" }
                    require(index + 3 <= target.size) { "CUBE contains excess sample rows" }
                    val values = triple(line)
                    require(values.all { it in 0f..1f }) {
                        "CUBE line $lineNumber: output outside 0..1 is unsupported by unsigned LUT storage"
                    }
                    for (channel in 0..2) target[index++] = values[channel]
                }
            }
        }
        val source = requireNotNull(data) { "CUBE requires LUT_3D_SIZE" }
        require(index == source.size) { "CUBE expected ${source.size / 3} rows, found ${index / 3}" }
        require((0..2).all { minimum[it] < maximum[it] }) { "CUBE domains must have strictly increasing finite endpoints" }
        return LutConfig(size = size, data = source, title = title, domainMin = minimum, domainMax = maximum)
    }
}
