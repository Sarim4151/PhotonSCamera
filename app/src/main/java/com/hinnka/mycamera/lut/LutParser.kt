package com.hinnka.mycamera.lut

import android.content.Context
import android.util.Log
import com.hinnka.mycamera.color.TransferCurve
import com.hinnka.mycamera.raw.ColorSpace
import com.hinnka.mycamera.utils.PLog
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3D LUT 解析器，支持 .cube (文本) 和 .plut (二进制) 格式
 */
object LutParser {
    private const val TAG = "LutParser"
    private const val MAGIC_PLUT = 0x54554C50 // 'PLUT' in Little Endian

    /** Read only the binary header when building filter lists; do not load 3D textures. */
    fun readInputEncoding(inputStream: InputStream): Pair<TransferCurve, ColorSpace> {
        val input = java.io.DataInputStream(inputStream)
        fun readInt() = Integer.reverseBytes(input.readInt())
        require(readInt() == MAGIC_PLUT) { "Expected a PLUT header" }
        val version = readInt()
        require(version in 1..5) { "Unsupported PLUT version: $version" }
        require(readInt() in 2..65) { "Invalid LUT size" }
        require(readInt() in 0..1) { "Unsupported LUT data type" }
        val curve = if (version >= 2) {
            val id = readInt()
            requireNotNull(TransferCurve.entries.firstOrNull { it.storageId == id })
        } else TransferCurve.SRGB
        val gamut = if (version >= 3) {
            requireNotNull(ColorSpace.entries.getOrNull(readInt()))
        } else ColorSpace.SRGB
        return curve to gamut
    }

    /**
     * 解析 LUT 文件（自动识别格式）
     */
    fun parse(inputStream: InputStream, title: String = ""): LutConfig {
        val stream = if (inputStream.markSupported()) inputStream else inputStream.buffered()

        // 先读取前 4 个字节判断是否为二进制格式
        val header = ByteArray(4)
        stream.mark(16)
        var read = 0
        while (read < header.size) {
            val count = stream.read(header, read, header.size - read)
            if (count < 0) break
            if (count > 0) read += count
        }
        stream.reset()

        if (read == 4) {
            val magic = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
            if (magic == MAGIC_PLUT) {
                return parseBinary(stream, title)
            }
        }

        // 默认回退到文本解析
        return CubeLutParser.parse(stream)
    }

    /**
     * 解析二进制 .plut 格式
     */
    private fun parseBinary(inputStream: InputStream, title: String): LutConfig {
        val fullData = readBoundedPlut(inputStream)
        val header = validatePlut(fullData)
        val directBuffer = ByteBuffer.allocateDirect(header.payloadBytes).order(ByteOrder.nativeOrder())
        if (header.dataType == 1) {
            val source = ByteBuffer.wrap(fullData).order(ByteOrder.LITTLE_ENDIAN)
            source.position(header.payloadOffset)
            repeat(header.payloadBytes / 2) { directBuffer.putShort(source.short) }
        } else directBuffer.put(fullData, header.payloadOffset, header.payloadBytes)
        directBuffer.position(0)
        return LutConfig(size = header.size, byteBuffer = directBuffer, title = title,
            configDataType = header.dataType, curve = header.curve, colorSpace = header.colorSpace,
            domainMin = header.domainMin, domainMax = header.domainMax)
    }

    internal data class PlutHeader(val size: Int, val dataType: Int, val curve: TransferCurve,
        val colorSpace: ColorSpace, val domainMin: FloatArray, val domainMax: FloatArray,
        val payloadOffset: Int, val payloadBytes: Int, val recipe: String?)

    internal fun readBoundedPlut(input: InputStream): ByteArray {
        val maximum = 65 * 65 * 65 * 6 + 48 + 4 + 1024 * 1024
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            if (count == 0) continue
            require(output.size().toLong() + count <= maximum) { "PLUT exceeds size limit" }
            output.write(chunk, 0, count)
        }
        return output.toByteArray()
    }

    internal fun validatePlut(bytes: ByteArray): PlutHeader {
        require(bytes.size >= 16) { "Truncated PLUT header" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.int == MAGIC_PLUT) { "Invalid PLUT signature" }
        val version = buffer.int
        require(version in 1..5) { "Unsupported PLUT version: $version" }
        val size = buffer.int
        require(size in 2..65) { "PLUT dimension must be 2..65" }
        val type = buffer.int
        require(type in 0..1) { "Unsupported PLUT data type: $type" }
        require(buffer.remaining() >= (if (version >= 3) 8 else if (version >= 2) 4 else 0)) { "Truncated PLUT encoding header" }
        val curve = if (version >= 2) {
            val id = buffer.int
            requireNotNull(TransferCurve.entries.firstOrNull { it.storageId == id }) { "Unknown PLUT transfer curve: $id" }
        } else TransferCurve.SRGB
        val gamut = if (version >= 3) {
            val id = buffer.int
            requireNotNull(ColorSpace.entries.getOrNull(id)) { "Unknown PLUT color space: $id" }
        } else ColorSpace.SRGB
        val minimum = floatArrayOf(0f, 0f, 0f)
        val maximum = floatArrayOf(1f, 1f, 1f)
        if (version >= 5) {
            require(buffer.remaining() >= 24) { "Truncated PLUT input domains" }
            for (channel in 0..2) minimum[channel] = buffer.float
            for (channel in 0..2) maximum[channel] = buffer.float
            require((0..2).all { minimum[it].isFinite() && maximum[it].isFinite() && minimum[it] < maximum[it] }) {
                "Invalid PLUT input domains"
            }
        }
        val offset = buffer.position()
        val payload = size * size * size * 3 * (if (type == 1) 2 else 1)
        require(buffer.remaining() >= payload) { "Truncated PLUT pixel payload" }
        buffer.position(offset + payload)
        var recipe: String? = null
        if (version >= 4) {
            require(buffer.remaining() >= 4) { "PLUT v4/v5 requires a recipe length" }
            val length = buffer.int
            require(length in 0..1024 * 1024 && length == buffer.remaining()) { "Invalid PLUT recipe length" }
            if (length > 0) {
                val text = ByteArray(length).also { buffer.get(it) }
                recipe = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(text)).toString()
                JSONObject(recipe)
            }
        } else require(!buffer.hasRemaining()) { "Unexpected trailing PLUT data" }
        return PlutHeader(size, type, curve, gamut, minimum, maximum, offset, payload, recipe)
    }

    /**
     * 从 Assets 文件夹解析 LUT 文件
     */
    fun parseFromAssets(context: Context, fileName: String): LutConfig {
        return context.assets.open(fileName).use { inputStream ->
            parse(inputStream, fileName.substringAfterLast('/').substringBeforeLast('.'))
        }
    }

    /**
     * 列出 Assets 中可用的 LUT 文件（从 config.json 读取）
     */
    fun listAvailableLuts(context: Context, folder: String = "luts"): List<LutInfo> {
        return try {
            // 读取 config.json
            val configPath = "$folder/config.json"
            val configJson = context.assets.open(configPath).use {
                it.bufferedReader().readText()
            }

            val jsonObject = JSONObject(configJson)
            val lutsArray = jsonObject.getJSONArray("luts")

            // 按配置文件中的顺序读取 LUT
            val lutList = mutableListOf<LutInfo>()
            for (i in 0 until lutsArray.length()) {
                val lutObj = lutsArray.getJSONObject(i)
                val id = lutObj.getString("id")
                val path = lutObj.getString("path")
                val nameObj = lutObj.getJSONObject("name")
                val isDefault = lutObj.optBoolean("isDefault", false)
                val isVip = lutObj.getBoolean("isVip")
                val category = lutObj.optString("category", "").trim()
                val isFavorite = lutObj.optBoolean("isFavorite", false)

                // 读取多语言名称
                val nameMap = mutableMapOf<String, String>()
                nameObj.keys().forEach { lang ->
                    nameMap[lang] = nameObj.getString(lang)
                }

                lutList.add(
                    LutInfo(
                        id = id,
                        nameMap = nameMap,
                        fileName = if (path.isBlank()) "" else "$folder/$path",
                        isBuiltIn = true,
                        isDefault = isDefault,
                        isVip = isVip,
                        category = category,
                        isFavorite = isFavorite
                    )
                )
            }

            PLog.d(TAG, "Loaded ${lutList.size} LUTs from config.json")
            lutList
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to load LUT config", e)
            emptyList()
        }
    }
}
