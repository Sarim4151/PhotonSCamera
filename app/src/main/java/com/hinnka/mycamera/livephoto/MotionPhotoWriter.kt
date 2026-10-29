package com.hinnka.mycamera.livephoto

import android.content.Context
import android.os.Build
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Motion Photo 文件合成器
 *
 * 将 JPEG 静态图片和 MP4 视频合成为符合特定厂商规范或 Android Motion Photo 1.0 规范的文件。
 */
object MotionPhotoWriter {
    private const val TAG = "MotionPhotoWriter"

    private const val JPEG_SOI = 0xFFD8     // Start of Image
    private const val JPEG_APP1 = 0xFFE1    // APP1 (EXIF/XMP)
    private const val JPEG_SOS = 0xFFDA     // Start of Scan
    private const val JPEG_EOI = 0xFFD9     // End of Image

    private val xmpItemTagRegex = "<(?:[A-Za-z_][\\w.-]*:)?Item\\b([^>]*)/?>".toRegex()
    private val xmpAttributeRegex =
        "(?:[A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)\\s*=\\s*\"([^\"]*)\"".toRegex()

    private data class XmpContainerItem(
        val mime: String?,
        val semantic: String?,
        val length: Long?,
        val padding: Long = 0L
    )

    private data class XmpVideoInfo(
        val containerLength: Long,
        val videoLength: Long,
        val secondaryLength: Long = containerLength,
        val primaryPadding: Long = 0L
    )

    private data class EmbeddedVideoRange(
        val offset: Long,
        val length: Long
    )

    /**
     * 获取适合当前设备的创建器
     */
    fun getCreator(context: Context? = null): LivePhotoCreator {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> 
                OppoLivePhotoCreator()
            manufacturer.contains("vivo") && context != null -> 
                VivoLivePhotoCreator(context)
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                LegacyLivePhotoCreator()
            else -> GoogleLivePhotoCreator()
        }
    }

    /**
     * 合成 Motion Photo 文件
     *
     * @param jpegPath 静态 JPEG 图片路径
     * @param videoPath MP4 视频路径
     * @param outputPath 输出 Motion Photo 路径
     * @param presentationTimestampUs 主要帧的显示时间戳（微秒）
     * @param context 可选的 Context
     * @return 是否成功
     */
    fun write(
        jpegPath: String,
        videoPath: String,
        outputPath: String,
        presentationTimestampUs: Long = 0,
        context: Context? = null,
        livePhotoCreator: LivePhotoCreator? = null
    ): Boolean {
        try {
            val jpegFile = File(jpegPath)
            val videoFile = File(videoPath)

            if (!jpegFile.exists()) return false
            if (!videoFile.exists()) return false

            val creator = livePhotoCreator ?: getCreator(context)
            
            return creator.create(jpegPath, videoPath, outputPath, presentationTimestampUs)

        } catch (e: Exception) {
            PLog.e(TAG, "Failed to write Motion Photo", e)
            return false
        }
    }

    /**
     * 从 Motion Photo 文件的 XMP 中获取视频长度
     */
    fun getVideoLength(motionPhotoPath: String): Long {
        try {
            val file = File(motionPhotoPath)
            if (!file.exists()) return 0L
            
            // 1. Check for Vivo separate file (beside the jpg)
            if (isVivoPhoto(motionPhotoPath)) {
                val videoFile = File(motionPhotoPath.substring(0, motionPhotoPath.lastIndexOf('.')) + ".mp4")
                if (videoFile.exists()) {
                    // For Vivo, the video length is the whole file minus the metadata at the end
                    // But usually, we just need the whole file to play it.
                    return videoFile.length()
                }
            }

            return resolveEmbeddedVideoRange(file)?.length ?: 0L
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to parse video length", e)
        }
        return 0L
    }

    /**
     * 检查文件是否是 Motion Photo
     */
    fun isMotionPhoto(filePath: String): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) return false
            
            // Check Vivo
            if (isVivoPhoto(filePath)) return true

            return resolveEmbeddedVideoRange(file) != null
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to check Motion Photo", e)
        }
        return false
    }

    fun isVivoPhoto(filePath: String): Boolean {
        return VivoLivePhotoCreator.isVivoLivePhoto(filePath)
    }

    /**
     * 获取 Motion Photo 的视频时间戳
     */
    fun getPresentationTimestampUs(filePath: String): Long {
        try {
            val file = File(filePath)
            return findInApp1Segments(file) { segmentString ->
                findFirstLongAttribute(
                    segmentString,
                    listOf(
                        "MicroVideoPresentationTimestampUs",
                        "MotionPhotoPresentationTimestampUs",
                        "MotionPhotoPrimaryPresentationTimestampUs"
                    )
                )
            } ?: -1L
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to get timestamp", e)
        }
        return -1L
    }

    /**
     * 从 Motion Photo 中提取视频文件
     */
    fun extractVideo(motionPhotoPath: String, outputVideoPath: String): Boolean {
        val destination = File(outputVideoPath)
        var writingStarted = false
        var completed = false
        try {
            if (File(motionPhotoPath).canonicalFile == destination.canonicalFile) return false
            if (isVivoPhoto(motionPhotoPath)) {
                // For Vivo, video is a separate file beside the JPEG
                val videoFile = File(motionPhotoPath.substring(0, motionPhotoPath.lastIndexOf('.')) + ".mp4")
                if (videoFile.exists()) {
                    if (videoFile.canonicalFile == destination.canonicalFile) return false
                    writingStarted = true
                    videoFile.copyTo(destination, overwrite = true)
                    completed = true
                    return true
                }
                return false
            }

            val file = File(motionPhotoPath)
            val videoRange = resolveEmbeddedVideoRange(file) ?: return false

            var copiedCompletely = false
            FileInputStream(file).use { input ->
                input.channel.position(videoRange.offset)
                writingStarted = true
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var lenRemaining = videoRange.length
                    while (lenRemaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), lenRemaining).toInt()
                        val bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        lenRemaining -= bytesRead
                    }
                    copiedCompletely = lenRemaining == 0L
                }
            }
            if (!copiedCompletely) {
                File(outputVideoPath).delete()
                return false
            }
            completed = true
            return true
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to extract video", e)
            return false
        } finally {
            if (writingStarted && !completed && destination.isFile) destination.delete()
        }
    }

    private fun parseXmpVideoInfo(file: File): XmpVideoInfo? {
        return findInApp1Segments(file, ::parseXmpVideoInfo)
    }

    private fun parseXmpVideoInfo(segmentString: String): XmpVideoInfo? {
        val hasDirectoryItems = xmpItemTagRegex.containsMatchIn(segmentString)
        val containerInfo = parseContainerMotionPhotoInfo(segmentString)
        // A present but malformed directory must not fall back to an unrelated legacy offset.
        if (hasDirectoryItems) return containerInfo
        val microVideoOffset = findLongAttribute(segmentString, "MicroVideoOffset")
        val legacyVideoLengthAttribute = findLongAttribute(segmentString, "VideoLength")
            ?.takeUnless { isOppoLivePhotoV2(segmentString) }

        val containerLength = listOfNotNull(
            microVideoOffset,
            legacyVideoLengthAttribute
        ).firstOrNull { it > 0L } ?: return null

        return XmpVideoInfo(
            containerLength = containerLength,
            videoLength = containerLength
        )
    }

    private fun isOppoLivePhotoV2(segmentString: String): Boolean {
        return findLongAttribute(segmentString, "OLivePhotoVersion")?.let { it >= 2L } == true ||
            segmentString.contains("http://ns.oplus.com/photos/1.0/camera/")
    }

    private fun parseContainerMotionPhotoInfo(segmentString: String): XmpVideoInfo? {
        val items = xmpItemTagRegex.findAll(segmentString).map { match ->
            val attributes = xmpAttributeRegex.findAll(match.groupValues[1]).associate {
                it.groupValues[1].lowercase() to it.groupValues[2]
            }
            val length = attributes["length"]?.toLongOrNull() ?: if ("length" in attributes) -1L else null
            val padding = attributes["padding"]?.toLongOrNull() ?: if ("padding" in attributes) -1L else 0L
            XmpContainerItem(attributes["mime"], attributes["semantic"], length, padding)
        }.toList()
        if (items.size < 2 || !items.first().semantic.equals("Primary", ignoreCase = true) ||
            items.first().padding < 0L || items.first().length?.let { it != 0L } == true) return null
        if (items.drop(1).any { it.length == null || it.length < 0L || it.padding != 0L }) return null
        val selected = items.indexOfFirst { it.isMotionPhotoSemantic() && it.isVideoMime() }
        if (selected < 1 || items[selected].length == 0L) return null
        // Directory resources are tightly packed. Work backwards from EOF, including every
        // following resource; primary-only Padding is before all secondary resources.
        var suffixLength = 0L
        for (item in items.drop(selected)) {
            val length = item.length ?: return null
            if (length > Long.MAX_VALUE - suffixLength) return null
            suffixLength += length
        }
        var secondaryLength = 0L
        for (item in items.drop(1)) {
            val length = item.length ?: return null
            if (length > Long.MAX_VALUE - secondaryLength) return null
            secondaryLength += length
        }
        return XmpVideoInfo(suffixLength, items[selected].length ?: return null,
            secondaryLength, items.first().padding)
    }

    private fun XmpContainerItem.isMotionPhotoSemantic(): Boolean {
        val value = semantic ?: return false
        return value.contains("MotionPhoto", ignoreCase = true) ||
            value.contains("MicroVideo", ignoreCase = true) ||
            value.contains("LivePhoto", ignoreCase = true)
    }

    private fun XmpContainerItem.isVideoMime(): Boolean {
        val value = mime ?: return false
        return value.startsWith("video/", ignoreCase = true) ||
            value.equals("application/mp4", ignoreCase = true)
    }

    private fun findFirstLongAttribute(segmentString: String, names: List<String>): Long? {
        for (name in names) {
            findLongAttribute(segmentString, name)?.let { return it }
        }
        return null
    }

    private fun findLongAttribute(segmentString: String, name: String): Long? {
        val regex = "(?:[A-Za-z_][\\w.-]*:)?${Regex.escape(name)}\\s*=\\s*\"(\\d+)\"".toRegex()
        return regex.find(segmentString)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun resolveEmbeddedVideoRange(file: File): EmbeddedVideoRange? {
        readLegacyVideoLength(file)?.let { videoLength ->
            val offset = file.length() - videoLength - 20L
            if (offset >= 0L && videoLength > 0L) {
                return validatedVideoRange(file, offset, videoLength)
            }
        }

        val xmpInfo = parseXmpVideoInfo(file) ?: return null
        if (xmpInfo.secondaryLength > file.length() - 2L ||
            xmpInfo.primaryPadding > file.length() - 2L - xmpInfo.secondaryLength) return null
        val offset = file.length() - xmpInfo.containerLength
        if (offset < 0L || xmpInfo.videoLength <= 0L) return null

        return validatedVideoRange(file, offset, xmpInfo.videoLength)
    }

    private fun validatedVideoRange(file: File, offset: Long, length: Long): EmbeddedVideoRange? {
        if (offset < 2L || length < 8L || offset > file.length() || length > file.length() - offset) return null
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val boxLength = input.readInt().toLong() and 0xffffffffL
            val boxType = input.readInt()
            // Supported JPEG motion-photo payloads start with an ordinary ISO-BMFF ftyp box.
            if (boxType != 0x66747970 || boxLength < 8L || boxLength > length) return null
        }
        return EmbeddedVideoRange(offset, length)
    }

    private fun readLegacyVideoLength(file: File): Long? {
        if (file.length() <= 20L) return null
        return RandomAccessFile(file, "r").use { input ->
            input.seek(input.length() - 20L)
            val buffer = ByteArray(20)
            input.readFully(buffer)
            val marker = String(buffer, StandardCharsets.UTF_8).trim()
            if (marker.startsWith("LIVE_")) marker.substring(5).toLongOrNull()?.takeIf { it > 0L }
            else null
        }
    }

    /** Scan only JPEG headers. One APP1 allocation is at most 65,533 bytes; appended
     * video and compressed image data are never materialized to inspect metadata. */
    private fun <T> findInApp1Segments(file: File, parser: (String) -> T?): T? {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 4L || input.readUnsignedShort() != JPEG_SOI) return null
            val headerLimit = minOf(input.length(), 16L * 1024L * 1024L)
            var segments = 0
            while (input.filePointer < headerLimit && segments++ < 4096) {
                if (input.readUnsignedByte() != 0xFF) return null
                var code: Int
                do {
                    if (input.filePointer >= headerLimit) return null
                    code = input.readUnsignedByte()
                } while (code == 0xFF)
                val marker = 0xFF00 or code
                if (marker == JPEG_SOS || marker == JPEG_EOI) return null
                if (marker == 0xFF01 || marker in 0xFFD0..0xFFD7) continue
                if (code == 0 || input.filePointer + 2L > headerLimit) return null
                val length = input.readUnsignedShort()
                if (length < 2 || length - 2L > headerLimit - input.filePointer) return null
                if (marker == JPEG_APP1) {
                    val payload = ByteArray(length - 2)
                    input.readFully(payload)
                    parser(String(payload, StandardCharsets.UTF_8))?.let { return it }
                } else input.seek(input.filePointer + length - 2L)
            }
        }
        return null
    }
}
