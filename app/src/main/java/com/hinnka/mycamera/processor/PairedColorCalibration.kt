package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import android.graphics.Color
import java.io.Writer
import java.util.Locale
import kotlin.math.*

/** A measured affine transform in linear sRGB; cube endpoints are encoded sRGB. */
object PairedColorCalibration {
    data class Patch(val source: DoubleArray, val target: DoubleArray)
    class Invalid(val reason: String, val rmse: Double? = null, val worst: Double? = null) : IllegalArgumentException(reason)
    data class Result(val matrix: Array<DoubleArray>, val validationRmse: Double, val maxError: Double, val count: Int) {
        fun apply(rgb: DoubleArray): DoubleArray {
            val x = doubleArrayOf(decode(rgb[0]), decode(rgb[1]), decode(rgb[2]), 1.0)
            return DoubleArray(3) { c -> encode(matrix[c].indices.sumOf { matrix[c][it] * x[it] }) }
        }
        fun writeCube(writer: Writer) {
            writer.write("TITLE \"Measured paired sRGB calibration\"\n# Encoded sRGB input/output; fitted in linear sRGB.\n# $count manual pairs; leave-one-out linear RMSE $validationRmse; max $maxError\nLUT_3D_SIZE 33\nDOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
            for (b in 0..32) for (g in 0..32) for (r in 0..32) {
                val out = apply(doubleArrayOf(r / 32.0, g / 32.0, b / 32.0))
                writer.write(String.format(Locale.US, "%.7f %.7f %.7f\n", out[0], out[1], out[2]))
            }
        }
        fun preview(bitmap: Bitmap): Bitmap {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val out = apply(doubleArrayOf(Color.red(p) / 255.0, Color.green(p) / 255.0, Color.blue(p) / 255.0))
                pixels[i] = Color.rgb((out[0] * 255).roundToInt(), (out[1] * 255).roundToInt(), (out[2] * 255).roundToInt())
            }
            return Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        }
    }
    fun decode(v: Double) = if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
    fun encode(v: Double): Double { val c = v.coerceIn(0.0, 1.0); return if (c <= .0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - .055 }

    /** Median rejects isolated pixel defects; variance rejects edges/textures and mixed patches. */
    fun sample(bitmap: Bitmap, u: Float, v: Float): DoubleArray {
        val cx = (u * (bitmap.width - 1)).roundToInt()
        val cy = (v * (bitmap.height - 1)).roundToInt()
        val radius = max(2, min(bitmap.width, bitmap.height) / 100)
        if (cx < radius || cy < radius || cx >= bitmap.width - radius || cy >= bitmap.height - radius) throw Invalid("edge")
        val channels = Array(3) { mutableListOf<Double>() }
        for (y in cy-radius..cy+radius) for (x in cx-radius..cx+radius) {
            val p = bitmap.getPixel(x, y)
            if (Color.alpha(p) < 255) throw Invalid("patch")
            channels[0].add(Color.red(p) / 255.0); channels[1].add(Color.green(p) / 255.0); channels[2].add(Color.blue(p) / 255.0)
        }
        return DoubleArray(3) { c ->
            val values = channels[c].sorted(); val median = values[values.size / 2]
            val deviations = values.map { abs(it - median) }.sorted()
            if (deviations[(deviations.size * .9).toInt()] > .055 || median < .015 || median > .985) throw Invalid("patch")
            decode(median)
        }
    }
    fun fit(patches: List<Patch>): Result {
        if (patches.size < 12) throw Invalid("count")
        for (c in 0..2) if (patches.maxOf { it.source[c] } - patches.minOf { it.source[c] } < .15) throw Invalid("range")
        // Every patch is predicted by a fit which has never seen that patch.
        val errors = patches.indices.map { held ->
            val m = solve(patches.filterIndexed { i, _ -> i != held })
            val p = patches[held]; val x = p.source + 1.0
            sqrt((0..2).sumOf { c -> (m[c].indices.sumOf { m[c][it] * x[it] } - p.target[c]).pow(2) } / 3)
        }
        val rmse = sqrt(errors.sumOf { it * it } / errors.size)
        if (rmse > .045 || errors.max() > .12) throw Invalid("residual", rmse, errors.max())
        val matrix = solve(patches)
        if (matrix.any { row -> row.any { !it.isFinite() || abs(it) > 4.0 } }) throw Invalid("range")
        return Result(matrix, rmse, errors.max(), patches.size)
    }
    private fun solve(patches: List<Patch>): Array<DoubleArray> {
        val a = Array(4) { DoubleArray(7) }
        for (p in patches) { val x = p.source + 1.0
            for (i in 0..3) { for (j in 0..3) a[i][j] += x[i] * x[j]
                for (c in 0..2) a[i][4+c] += x[i] * p.target[c]
            }
        }
        val scale = a.maxOf { row -> row.take(4).maxOf { abs(it) } }
        for (k in 0..3) {
            val pivot = (k..3).maxBy { abs(a[it][k]) }
            if (abs(a[pivot][k]) < scale * 1e-4) throw Invalid("rank")
            val tmp = a[k]; a[k] = a[pivot]; a[pivot] = tmp
            val divisor = a[k][k]; for (j in k..6) a[k][j] /= divisor
            for (i in 0..3) if (i != k) { val f = a[i][k]; for (j in k..6) a[i][j] -= f * a[k][j] }
        }
        return Array(3) { c -> DoubleArray(4) { a[it][4+c] } }
    }
}
