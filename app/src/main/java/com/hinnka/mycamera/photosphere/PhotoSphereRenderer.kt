package com.hinnka.mycamera.photosphere

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.hinnka.mycamera.gallery.StreamingJpegEncoder
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.UUID
import kotlin.math.*

/** Rotation-only spherical reprojection. Translation/parallax and moving subjects cannot be
 * corrected by this renderer; capture must keep the optical centre steady. All sources are
 * immutable, full-resolution, display-referred sRGB JPEGs with calibrated pixel intrinsics. */
object PhotoSphereRenderer {
    private const val BIN_X = 72
    private const val BIN_Y = 36
    private const val TILE = 128
    private const val TILE_BUDGET = 24L * 1024 * 1024
    private val linear = DoubleArray(256) { i -> val c = i / 255.0; if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }

    fun render(context: Context, frames: List<SphereFrame>, outputFile: File, width: Int = 4096,
               onProgress: (Int, Int) -> Unit = { _, _ -> }, checkCancelled: () -> Unit = {}): File {
        require(width == 4096 || width == 8192) { "Select a supported spherical output resolution" }
        require(frames.size in 6..256)
        val height = width / 2
        require(!outputFile.exists()) { "Refusing to overwrite output" }
        val parent = outputFile.canonicalFile.parentFile ?: error("Missing output directory")
        require(listOf(context.cacheDir, context.filesDir).any { parent.path == it.canonicalPath || parent.path.startsWith(it.canonicalPath + File.separator) })
        check(parent.isDirectory || parent.mkdirs())
        val encoded = File(parent, "sphere-${UUID.randomUUID()}.jpg")
        val tagged = File(parent, "sphere-xmp-${UUID.randomUUID()}.jpg")
        try {
            TileSources(frames).use { sources ->
                val bins = candidateBins(frames)
                validateOverlap(frames, sources, bins, checkCancelled)
                // Dense preliminary coverage includes both exact poles and the wrap seam.
                for (y in 0..720) {
                    checkCancelled()
                    for (x in 0..1440) {
                        val ray = SphereGeometry.outputRay(x / 1440.0 * width, y / 720.0 * height, width, height)
                        check(frames.any { SphereGeometry.project(it, ray) != null }) { "Incomplete sphere coverage" }
                    }
                }
                check(StreamingJpegEncoder.write(context, encoded, width, height, 100) { y, row ->
                    checkCancelled()
                    for (x in 0 until width) {
                        val ray = SphereGeometry.outputRay(x + 0.5, y + 0.5, width, height)
                        val candidates = bins[min(BIN_Y - 1, y * BIN_Y / height) * BIN_X + min(BIN_X - 1, x * BIN_X / width)]
                        var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
                        for (index in candidates) {
                            val f = frames[index]
                            val p = SphereGeometry.project(f, ray) ?: continue
                            val rgb = sources.sample(index, p.x, p.y)
                            r += linear[rgb shr 16 and 255] * f.exposureGain * p.weight
                            g += linear[rgb shr 8 and 255] * f.exposureGain * p.weight
                            b += linear[rgb and 255] * f.exposureGain * p.weight
                            weight += p.weight
                        }
                        check(weight > 0.0) { "Missing sphere coverage at output pixel ($x,$y)" }
                        row[x] = (0xff shl 24) or (linearToSrgb(r / weight) shl 16) or (linearToSrgb(g / weight) shl 8) or linearToSrgb(b / weight)
                    }
                    onProgress(y + 1, height)
                }) { "Spherical JPEG encoding failed" }
            }
            checkCancelled()
            SphereXmp.write(encoded, tagged, width, height, checkCancelled)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tagged.absolutePath, bounds)
            check(bounds.outWidth == width && bounds.outHeight == height && tagged.length() > 0) { "Invalid spherical output" }
            checkCancelled()
            java.nio.file.Files.move(tagged.toPath(), outputFile.toPath())
            return outputFile
        } finally {
            encoded.delete()
            tagged.delete()
        }
    }

    /** A cap enclosing each bin is tested against each camera's enclosing view cone.
     * This conservative test cannot drop a frame whose rectangle intersects that bin. */
    private fun candidateBins(frames: List<SphereFrame>): Array<IntArray> = Array(BIN_X * BIN_Y) { bin ->
        val bx = bin % BIN_X; val by = bin / BIN_X
        val centre = SphereGeometry.outputRay(bx + 0.5, by + 0.5, BIN_X, BIN_Y)
        val binRadius = PI / BIN_Y / 2 + PI / BIN_X
        frames.indices.filter { i ->
            val f = frames[i]; val k = f.intrinsics
            val maxX = max(k.cx, k.width - k.cx) / k.fx
            val maxY = max(k.cy, k.height - k.cy) / k.fy
            val radius = atan(sqrt(maxX * maxX + maxY * maxY))
            acos(f.basis.forward.dot(centre).coerceIn(-1.0, 1.0)) <= radius + binRadius
        }.toIntArray()
    }
    /** Rejects gross rotation/calibration errors using normalized luminance correlation at
     * matching world rays. This is a diagnostic, not feature registration or parallax repair.
     * Textureless overlap cannot establish alignment and is left to calibrated capture. */
    private fun validateOverlap(frames: List<SphereFrame>, sources: TileSources,
                                bins: Array<IntArray>, checkCancelled: () -> Unit) {
        class Stats {
            var n = 0; var a = 0.0; var b = 0.0; var aa = 0.0; var bb = 0.0; var ab = 0.0
            fun add(x: Double, y: Double) { n++; a += x; b += y; aa += x*x; bb += y*y; ab += x*y }
        }
        val pairs = HashMap<Int, Stats>()
        fun luma(rgb: Int) = 0.2126 * (rgb shr 16 and 255) + 0.7152 * (rgb shr 8 and 255) + 0.0722 * (rgb and 255)
        for (y in 0 until 90) {
            checkCancelled()
            for (x in 0 until 180) {
                val ray = SphereGeometry.outputRay(x + 0.5, y + 0.5, 180, 90)
                val candidates = bins[min(BIN_Y - 1, y * BIN_Y / 90) * BIN_X + min(BIN_X - 1, x * BIN_X / 180)]
                val visible = candidates.asSequence().mapNotNull { i -> SphereGeometry.project(frames[i], ray)?.takeIf { it.weight > 0.04 }?.let { i to it } }.toList()
                for (i in visible.indices) for (j in i + 1 until visible.size) {
                    val (ai, ap) = visible[i]; val (bi, bp) = visible[j]
                    pairs.getOrPut(ai * frames.size + bi) { Stats() }.add(
                        luma(sources.sample(ai, ap.x, ap.y)), luma(sources.sample(bi, bp.x, bp.y)))
                }
            }
        }
        for (stats in pairs.values) {
            if (stats.n < 100) continue
            val va = stats.aa - stats.a * stats.a / stats.n
            val vb = stats.bb - stats.b * stats.b / stats.n
            if (va / stats.n < 225 || vb / stats.n < 225) continue
            val correlation = (stats.ab - stats.a * stats.b / stats.n) / sqrt(va * vb)
            check(correlation >= 0.20) { "Source overlap is misaligned or the scene moved; recapture from a steady optical centre" }
        }
    }
    private fun linearToSrgb(v: Double): Int { val c = v.coerceIn(0.0, 1.0); return ((if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055) * 255).roundToInt() }

    @Suppress("DEPRECATION")
    private class TileSources(private val frames: List<SphereFrame>) : AutoCloseable {
        private val decoders = LinkedHashMap<Int, BitmapRegionDecoder>(8, 0.75f, true)
        private data class Key(val frame: Int, val x: Int, val y: Int)
        private val tiles = LinkedHashMap<Key, Bitmap>(128, 0.75f, true)
        private var bytes = 0L
        private fun pixel(frame: Int, x: Int, y: Int): Int {
            val key = Key(frame, x / TILE, y / TILE)
            val tile = tiles[key] ?: run {
                val decoder = decoders.getOrPut(frame) {
                    if (decoders.size >= 8) {
                        val oldest = decoders.entries.iterator(); val entry = oldest.next()
                        entry.value.recycle(); oldest.remove()
                    }
                    requireNotNull(BitmapRegionDecoder.newInstance(frames[frame].jpeg.absolutePath, false)) { "Cannot open sphere source JPEG" }.also {
                        val k = frames[frame].intrinsics
                        check(it.width == k.width && it.height == k.height) { "Source dimensions differ from calibration" }
                    }
                }
                val left = key.x * TILE; val top = key.y * TILE
                val decoded = decoder.decodeRegion(Rect(left, top, min(left + TILE, decoder.width), min(top + TILE, decoder.height)), BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }) ?: throw IOException("Cannot decode sphere source")
                while (bytes + decoded.allocationByteCount > TILE_BUDGET && tiles.isNotEmpty()) {
                    val oldest = tiles.entries.iterator(); val entry = oldest.next(); bytes -= entry.value.allocationByteCount; entry.value.recycle(); oldest.remove()
                }
                tiles[key] = decoded; bytes += decoded.allocationByteCount
                decoded
            }
            return tile.getPixel(x % TILE, y % TILE)
        }
        fun sample(frame: Int, x: Double, y: Double): Int {
            val ix = x.toInt(); val iy = y.toInt(); val dx = x - ix; val dy = y - iy
            val p0 = pixel(frame, ix, iy); val p1 = pixel(frame, ix + 1, iy)
            val p2 = pixel(frame, ix, iy + 1); val p3 = pixel(frame, ix + 1, iy + 1)
            var result = 0xff shl 24
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val a = (p0 shr shift and 255) * (1 - dx) + (p1 shr shift and 255) * dx
                val b = (p2 shr shift and 255) * (1 - dx) + (p3 shr shift and 255) * dx
                result = result or ((a * (1 - dy) + b * dy).roundToInt() shl shift)
            }
            return result
        }
        override fun close() { tiles.values.forEach { it.recycle() }; tiles.clear(); decoders.values.forEach { it.recycle() }; decoders.clear() }
    }
}
