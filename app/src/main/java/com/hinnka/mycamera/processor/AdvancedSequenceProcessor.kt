package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Computational stacking of rendered sRGB photographs, not RAW radiometric HDR.
 * Exposure is estimated from unclipped corresponding pixels; a luminance shoulder compresses
 * HDR highlights into the display range. Unknown camera tone curves and
 * clipped channels cannot be recovered. HDR retains the reference brightness and rejects moving
 * samples. Astro registration models modest field rotation, translation and scale, but cannot
 * correct lens distortion or parallax. Optional manual sky/foreground separation registers only
 * compact sky peaks above a horizontal user boundary and retains reference foreground. It does
 * not infer sky or follow irregular silhouettes. Star trails deliberately use tripod
 * coordinates without registration. Rejected frames are reported and reference pixels fill edges.
 */
internal object AdvancedSequenceProcessor {
    suspend fun process(frames: List<Bitmap>, operation: PhotoSequenceOperation,
        progress: (Int, Int) -> Unit, astroHorizon: Float? = null): PhotoSequenceResult =
        processFrames(frames.map(::BitmapSequenceFrame), operation, progress, astroHorizon)

    private val linear = FloatArray(256) {
        val v = it / 255f
        if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    }
    private data class Transform(val dx: Double = 0.0, val dy: Double = 0.0,
                                 val angle: Double = 0.0, val scale: Double = 1.0) {
        fun point(x: Double, y: Double, w: Int, h: Int): Pair<Double, Double> {
            val xx = x - (w - 1) * .5; val yy = y - (h - 1) * .5
            return (scale * (cos(angle) * xx - sin(angle) * yy) + (w - 1) * .5 + dx) to
                (scale * (sin(angle) * xx + cos(angle) * yy) + (h - 1) * .5 + dy)
        }
    }
    private data class Plane(val p: FloatArray, val w: Int, val h: Int)
    private fun luminance(r: Float, g: Float, b: Float) = .2126f * r + .7152f * g + .0722f * b
    private fun color(c: Int, channel: Int) = linear[(c ushr (16 - channel * 8)) and 255]
    private fun encode(v: Float): Int {
        val x = v.coerceIn(0f, 1f)
        return ((if (x <= .0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - .055f) * 255f + .5f).toInt()
    }
    private suspend fun plane(b: SequenceFrame): Plane {
        val factor = max(1, ceil(max(b.width, b.height) / 256.0).toInt())
        val w = (b.width + factor - 1) / factor; val h = (b.height + factor - 1) / factor
        val p = FloatArray(w * h); val stripe = IntArray(b.width * factor)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val rows = min(factor, b.height - y * factor)
            b.getPixels(stripe, 0, b.width, 0, y * factor, b.width, rows)
            for (x in 0 until w) {
                val columns = min(factor, b.width - x * factor); var sum = 0f
                for (yy in 0 until rows) for (xx in 0 until columns) {
                    val c = stripe[yy * b.width + x * factor + xx]
                    sum += luminance(color(c, 0), color(c, 1), color(c, 2))
                }
                p[y * w + x] = sum / (rows * columns)
            }
        }
        return Plane(p, w, h)
    }
    /** Compact measured peaks inside the manually declared sky, never semantic segmentation.
     * Reject extended edges/cloud structure and require separated peaks in both axes. */
    private suspend fun stellarPlane(source: Plane, skyBottom: Int): Plane? {
        if (skyBottom < 10 || source.w < 10) return null
        val sky = source.p.copyOf(source.w * skyBottom).also { it.sort() }
        val floor = sky[sky.size / 3]
        val noise = max(1e-6f, sky[sky.size / 2] - sky[sky.size / 5])
        val peaks = ArrayList<Pair<Int, Int>>()
        val signals = ArrayList<Float>()
        for (y in 3 until skyBottom - 3) {
            currentCoroutineContext().ensureActive()
            for (x in 3 until source.w - 3) {
                val center = source.p[y * source.w + x]
                if (center - floor <= 8 * noise || center >= .7f) continue
                var maximum = true
                for (dy in -1..1) for (dx in -1..1) {
                    if ((dx != 0 || dy != 0) && source.p[(y + dy) * source.w + x + dx] >= center) maximum = false
                }
                if (!maximum) continue
                var ring = 0f; var ringMax = 0f; var count = 0
                for (dy in -3..3) for (dx in -3..3) if (abs(dx) == 3 || abs(dy) == 3) {
                    val value = source.p[(y + dy) * source.w + x + dx]
                    ring += value; ringMax = max(ringMax, value); count++
                }
                val background = ring / count
                if (center - background <= 8 * noise || ringMax - background > (center - background) * .65f) continue
                peaks += x to y; signals += center - background
            }
        }
        if (peaks.size < 3 || peaks.maxOf { it.first } - peaks.minOf { it.first } < source.w * .15 ||
            peaks.maxOf { it.second } - peaks.minOf { it.second } < skyBottom * .1) return null
        val output = FloatArray(source.p.size)
        for (i in peaks.indices) {
            val (x, y) = peaks[i]
            // A small symmetric kernel supports subpixel correlation without foreground texture.
            for (dy in -2..2) for (dx in -2..2) {
                val at = (y + dy) * source.w + x + dx
                output[at] += signals[i] * exp(-(dx * dx + dy * dy) / 2.0).toFloat()
            }
        }
        return Plane(output, source.w, source.h)
    }
    private fun sample(p: FloatArray, w: Int, h: Int, x: Double, y: Double): Float {
        if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return Float.NaN
        val ix = x.toInt(); val iy = y.toInt(); val fx = (x - ix).toFloat(); val fy = (y - iy).toFloat()
        val nx = min(w - 1, ix + 1); val ny = min(h - 1, iy + 1)
        return (p[iy * w + ix] * (1 - fx) + p[iy * w + nx] * fx) * (1 - fy) +
            (p[ny * w + ix] * (1 - fx) + p[ny * w + nx] * fx) * fy
    }
    // Correlation is exposure-invariant. Fixed interior support avoids favoring small overlaps.
    private suspend fun score(a: Plane, b: Plane, t: Transform, skyBottom: Int = a.h): Double {
        var aa = 0.0; var bb = 0.0; var ab = 0.0; var sa = 0.0; var sb = 0.0; var n = 0
        val margin = max(2, (max(a.w, a.h) * .1).toInt())
        for (y in margin until min(a.h - margin, skyBottom - margin) step 2) {
            currentCoroutineContext().ensureActive()
            for (x in margin until a.w - margin step 2) {
                val q = t.point(x.toDouble(), y.toDouble(), a.w, a.h)
                if (skyBottom < a.h && q.second >= skyBottom - 2) return -1.0
                val u = a.p[y * a.w + x].toDouble(); val v = sample(b.p, b.w, b.h, q.first, q.second).toDouble()
                if (!v.isFinite()) return -1.0
                sa += u; sb += v; aa += u * u; bb += v * v; ab += u * v; n++
            }
        }
        if (n < 16) return -1.0
        val va = aa - sa * sa / n; val vb = bb - sb * sb / n
        if (va / n < 1e-10 || vb / n < 1e-10) return -1.0
        return (ab - sa * sb / n) / sqrt(va * vb)
    }
    private suspend fun register(a: Plane, b: Plane, astro: Boolean, skyBottom: Int = a.h): Transform? {
        var best = Transform(); var value = score(a, b, best, skyBottom)
        val radius = max(1, (max(a.w, a.h) * .04).toInt())
        // Global translation search followed by fractional refinement and modest breathing/rotation.
        val angles = if (astro) doubleArrayOf(-.06, -.04, -.02, 0.0, .02, .04, .06) else doubleArrayOf(0.0)
        for (angle in angles) for (y in -radius..radius step 2) for (x in -radius..radius step 2) {
            val t = Transform(x.toDouble(), y.toDouble(), angle); val s = score(a, b, t, skyBottom)
            if (s > value) { best = t; value = s }
        }
        for (step in doubleArrayOf(1.0, .5, .25)) {
            repeat(3) {
                val base = best
                val candidates = mutableListOf<Transform>()
                for (dy in -1..1) for (dx in -1..1) candidates += base.copy(dx = base.dx + dx * step, dy = base.dy + dy * step)
                for (d in listOf(-1, 1)) {
                    candidates += base.copy(scale = base.scale + d * .005 * step)
                    if (astro) candidates += base.copy(angle = base.angle + d * .015 * step)
                }
                for (t in candidates) {
                    if (abs(t.dx) > radius || abs(t.dy) > radius || abs(t.scale - 1) > .025 || abs(t.angle) > .06) continue
                    val s = score(a, b, t, skyBottom)
                    if (s > value) { best = t; value = s }
                }
            }
        }
        if (value < if (astro) .78 else .88) return null
        // A competing remote peak means repetitive detail cannot establish correspondence.
        for (y in -radius..radius step 3) for (x in -radius..radius step 3) {
            if (hypot(x - best.dx, y - best.dy) > 3 && score(a, b, best.copy(dx = x.toDouble(), dy = y.toDouble()), skyBottom) > value - .015) return null
        }
        return best
    }
    private fun exposure(a: Plane, b: Plane, t: Transform): Float {
        val ratios = ArrayList<Float>()
        for (y in 2 until a.h - 2 step 3) for (x in 2 until a.w - 2 step 3) {
            val q = t.point(x.toDouble(), y.toDouble(), a.w, a.h)
            val u = a.p[y * a.w + x]; val v = sample(b.p, b.w, b.h, q.first, q.second)
            if (u in .015f.. .7f && v in .015f.. .7f) ratios += u / v
        }
        if (ratios.size < 16) return Float.NaN
        ratios.sort(); return ratios[ratios.size / 2].takeIf { it in .0625f..16f } ?: Float.NaN
    }

    // Sky backgrounds are additive offsets, not reliable exposure measurements. Compare
    // matched star signal above robust backgrounds; require a stable distribution of ratios.
    private suspend fun starExposure(a: Plane, b: Plane, t: Transform, skyBottom: Int = a.h): Float {
        fun background(p: Plane): Pair<Float, Float> {
            val sorted = p.p.copyOf(p.w * skyBottom).also { it.sort() }
            val floor = sorted[sorted.size / 3]
            val noise = max(1e-6f, sorted[sorted.size / 2] - sorted[sorted.size / 5])
            return floor to noise
        }
        val (aFloor, aNoise) = background(a)
        val (bFloor, bNoise) = background(b)
        val ratios = ArrayList<Float>()
        for (y in 1 until (if (skyBottom < a.h) skyBottom - 2 else a.h - 1)) {
            currentCoroutineContext().ensureActive()
            for (x in 1 until a.w - 1) {
                val q = t.point(x.toDouble(), y.toDouble(), a.w, a.h)
                if (skyBottom < a.h && q.second >= skyBottom - 2) continue
                val u = a.p[y * a.w + x] - aFloor
                val v = sample(b.p, b.w, b.h, q.first, q.second) - bFloor
                if (u > 6 * aNoise && v > 6 * bNoise &&
                    u + aFloor < .7f && v + bFloor < .7f) ratios += u / v
            }
        }
        if (ratios.size < 3) return Float.NaN
        ratios.sort()
        val median = ratios[ratios.size / 2]
        val spread = ratios[ratios.size * 3 / 4] - ratios[ratios.size / 4]
        return median.takeIf { it in .0625f..16f && spread / it < .35f } ?: Float.NaN
    }

    suspend fun processFrames(frames: List<SequenceFrame>, operation: PhotoSequenceOperation,
                        progress: (Int, Int) -> Unit, astroHorizon: Float? = null): PhotoSequenceResult {
        val w = frames[0].width; val h = frames[0].height; val total = frames.size * 2
        val reference = plane(frames[0]); val transforms = arrayOfNulls<Transform>(frames.size)
        val gains = FloatArray(frames.size) { 1f }; val rejected = ArrayList<Int>()
        val trails = operation == PhotoSequenceOperation.STAR_TRAILS
        val hdr = operation == PhotoSequenceOperation.MOTION_HDR
        val astro = operation == PhotoSequenceOperation.ASTRO_STACK
        require(astroHorizon == null || (astroHorizon.isFinite() && astroHorizon in .15f.. .9f))
        val horizon = astroHorizon.takeIf { astro }
        val skyBottom = horizon?.let { (it * reference.h).toInt() } ?: reference.h
        val stellarReference = if (horizon != null) stellarPlane(reference, skyBottom) else null
        transforms[0] = Transform(); progress(0, total)
        for (i in frames.indices) {
            currentCoroutineContext().ensureActive()
            if (i > 0) {
                val src = plane(frames[i])
                val t = if (trails) Transform() else if (horizon != null) {
                    val stellarSource = stellarPlane(src, skyBottom)
                    if (stellarReference == null || stellarSource == null) null
                    else register(stellarReference, stellarSource, true, skyBottom)
                } else register(reference, src, astro)
                if (t != null) {
                    gains[i] = when {
                        trails -> 1f
                        astro -> starExposure(reference, src, t, skyBottom)
                        else -> exposure(reference, src, t)
                    }
                    if (gains[i].isFinite()) transforms[i] = t.copy(dx = t.dx * w / reference.w, dy = t.dy * h / reference.h)
                }
                if (transforms[i] == null) rejected += i
            }
            progress(i + 1, total)
        }
        require(frames.size - rejected.size >= 2) { "Fewer than two frames could be reliably aligned and exposure-matched" }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            for (start in 0 until h step 24) {
                currentCoroutineContext().ensureActive()
                val rows = min(24, h - start); val count = rows * w
                // At most 15 x 24 scanlines of RGB samples; transformed input is read per frame.
                val samples = Array(frames.size) { FloatArray(count * 3) { Float.NaN } }
                for (i in frames.indices) {
                    val t = transforms[i] ?: continue
                    currentCoroutineContext().ensureActive()
                    val corners = listOf(t.point(0.0, start.toDouble(), w, h), t.point((w - 1).toDouble(), start.toDouble(), w, h),
                        t.point(0.0, (start + rows - 1).toDouble(), w, h), t.point((w - 1).toDouble(), (start + rows - 1).toDouble(), w, h))
                    val y0 = floor(corners.minOf { it.second }).toInt().coerceIn(0, h - 1)
                    val y1 = (ceil(corners.maxOf { it.second }).toInt() + 1).coerceIn(y0, h - 1)
                    val pixels = IntArray(w * (y1 - y0 + 1))
                    frames[i].getPixels(pixels, 0, w, 0, y0, w, y1 - y0 + 1)
                    for (y in 0 until rows) {
                        currentCoroutineContext().ensureActive()
                        for (x in 0 until w) {
                            val q = t.point(x.toDouble(), (start + y).toDouble(), w, h)
                            if (i > 0 && horizon != null && q.second >= horizon * h - 1) continue
                            if (q.first < 0 || q.first > w - 1 || q.second < 0 || q.second > h - 1) continue
                            val xx = q.first.toInt(); val yy = q.second.toInt(); val fx = (q.first - xx).toFloat(); val fy = (q.second - yy).toFloat()
                            val nx = min(w - 1, xx + 1); val ny = min(y1, yy + 1)
                            for (c in 0..2) {
                                val a = color(pixels[(yy - y0) * w + xx], c); val b = color(pixels[(yy - y0) * w + nx], c)
                                val d = color(pixels[(ny - y0) * w + xx], c); val e = color(pixels[(ny - y0) * w + nx], c)
                                samples[i][(y * w + x) * 3 + c] = ((a * (1 - fx) + b * fx) * (1 - fy) + (d * (1 - fx) + e * fx) * fy) * gains[i]
                            }
                        }
                    }
                }
                val output = IntArray(count); val values = FloatArray(frames.size); val deviations = FloatArray(frames.size)
                for (p in 0 until count) {
                    if (p % w == 0) currentCoroutineContext().ensureActive()
                    val at = p * 3; var n = 0
                    for (i in frames.indices) if (samples[i][at].isFinite()) {
                        values[n++] = luminance(samples[i][at], samples[i][at + 1], samples[i][at + 2])
                    }
                    java.util.Arrays.sort(values, 0, n); val median = values[n / 2]
                    for (j in 0 until n) deviations[j] = abs(values[j] - median)
                    java.util.Arrays.sort(deviations, 0, n)
                    val threshold = max(.004f + .04f * sqrt(max(0f, median)), 3f * 1.4826f * deviations[n / 2])
                    val refLum = luminance(samples[0][at], samples[0][at + 1], samples[0][at + 2])
                    // If reference differs from consensus, preserve its instant instead of ghosts.
                    val refClipped = max(samples[0][at], max(samples[0][at + 1], samples[0][at + 2])) >= .98f
                    // A reference-relative bound cannot be inflated by a moving majority or
                    // the large MAD of a two-frame burst. Uncertain samples retain the reference.
                    val motionThreshold = .004f + .04f * sqrt(max(0f, refLum))
                    var contributedSources = 0
                    var r = 0f; var g = 0f; var b = 0f; var weights = 0f
                    for (i in frames.indices) {
                        val s = samples[i]; if (!s[at].isFinite()) continue
                        val lum = luminance(s[at], s[at + 1], s[at + 2])
                        if (trails) { r = max(r, s[at]); g = max(g, s[at + 1]); b = max(b, s[at + 2]); weights = 1f; continue }
                        if (hdr) {
                            if (i != 0 && !refClipped && abs(lum - refLum) > motionThreshold) continue
                        } else if (i != 0 && (abs(lum - refLum) > motionThreshold || abs(lum - median) > threshold)) continue
                        // Clipped JPEG channels carry no highlight information. Prefer midtones.
                        val peak = max(s[at], max(s[at + 1], s[at + 2])) / gains[i]
                        if (hdr && i != 0) {
                            // A clipped source has no recoverable channel data. In a clipped
                            // reference region, only a darker, unclipped exposure establishing
                            // radiance above reference white is evidence of highlight recovery.
                            // Dark moving objects cannot replace that bright reference region.
                            if (peak >= .98f || (refClipped && max(s[at], max(s[at + 1], s[at + 2])) <= 1f)) continue
                        }
                        val weight = if (hdr) max(.001f, min(lum / gains[i], 1f - peak).coerceAtLeast(0f)) else 1f
                        if (i != 0 && weight > .001f) contributedSources++
                        r += s[at] * weight; g += s[at + 1] * weight; b += s[at + 2] * weight; weights += weight
                    }
                    if (hdr && contributedSources == 0) {
                        r = samples[0][at]; g = samples[0][at + 1]; b = samples[0][at + 2]; weights = 1f
                    }
                    if (weights == 0f) { r = samples[0][at]; g = samples[0][at + 1]; b = samples[0][at + 2]; weights = 1f }
                    r /= weights; g /= weights; b /= weights
                    if (horizon != null) {
                        // Feather above the manual boundary; preserve reference ground below.
                        val feather = max(2f, h * .02f)
                        val skyWeight = ((horizon * h - (start + p / w)) / feather).coerceIn(0f, 1f)
                        val smooth = skyWeight * skyWeight * (3f - 2f * skyWeight)
                        r = samples[0][at] + (r - samples[0][at]) * smooth
                        g = samples[0][at + 1] + (g - samples[0][at + 1]) * smooth
                        b = samples[0][at + 2] + (b - samples[0][at + 2]) * smooth
                    }
                    if (hdr && contributedSources > 0) {
                        // Compress only recovered radiance beyond display white. Midtones,
                        // ordinary highlights and reference-only fallback keep their brightness.
                        val peak = max(r, max(g, b))
                        if (peak > 1f) {
                            r /= peak; g /= peak; b /= peak
                        }
                    }
                    output[p] = (0xff shl 24) or (encode(r) shl 16) or (encode(g) shl 8) or encode(b)
                }
                result.setPixels(output, 0, w, 0, start, w, rows)
                progress(frames.size + ((start + rows).toLong() * frames.size / h).toInt(), total)
            }
            return PhotoSequenceResult(result, alignmentOffsets = transforms.map { (it?.dx?.roundToInt() ?: 0) to (it?.dy?.roundToInt() ?: 0) },
                rejectedFrameIndices = rejected, acceptedFrameCount = frames.size - rejected.size)
        } catch (t: Throwable) { result.recycle(); throw t }
    }
}
