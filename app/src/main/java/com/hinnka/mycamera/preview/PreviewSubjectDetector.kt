package com.hinnka.mycamera.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.Closeable
import kotlin.math.hypot

/** Worker-thread-only COCO detection. Spatial continuity is not identity recognition. */
internal class PreviewSubjectDetector(context: Context) : Closeable {
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder()
                .setModelAssetPath("efficientdet_lite0_int8.tflite")
                .setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.45f)
            .setMaxResults(8)
            .build(),
    )
    private var previous: PreviewEyeFocusProcessor.EyeTarget? = null
    private var confirmations = 0
    private var previousCategory: String? = null
    private var lastSeen = 0L
    private var misses = 0

    fun reset() { previous = null; confirmations = 0; previousCategory = null; lastSeen = 0L; misses = 0 }

    fun detect(bitmap: Bitmap, category: String): PreviewEyeFocusProcessor.EyeTarget? {
        if (previousCategory != category) reset()
        previousCategory = category
        val image = BitmapImageBuilder(bitmap).build()
        val candidates = try {
            detector.detect(image).detections().mapNotNull { detection ->
                val result = detection.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val label = result.categoryName()
                if (!accepts(category, label)) return@mapNotNull null
                val box = detection.boundingBox()
                val bounds = RectF(
                    (box.left / bitmap.width).coerceIn(0f, 1f),
                    (box.top / bitmap.height).coerceIn(0f, 1f),
                    (box.right / bitmap.width).coerceIn(0f, 1f),
                    (box.bottom / bitmap.height).coerceIn(0f, 1f),
                )
                if (bounds.width() < 0.035f || bounds.height() < 0.035f) return@mapNotNull null
                PreviewEyeFocusProcessor.EyeTarget(bounds.centerX(), bounds.centerY(), result.score(), label, bounds)
            }
        } finally { image.close() }
        val now = SystemClock.elapsedRealtime()
        val last = previous.takeIf { now - lastSeen <= 900L }
        fun overlap(a: RectF?, b: RectF?): Float {
            if (a == null || b == null) return 0f
            val area = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
                (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
            return area / (a.width() * a.height() + b.width() * b.height() - area).coerceAtLeast(.0001f)
        }
        // Associate to the current subject before considering another larger detection.
        val matched = if (last == null) null else candidates.filter {
            it.label == last.label && (overlap(last.bounds, it.bounds) > .15f ||
                hypot(last.x - it.x, last.y - it.y) < .12f)
        }.maxByOrNull { overlap(last.bounds, it.bounds) * 2f + it.confidence - hypot(last.x - it.x, last.y - it.y) }
        val selected = matched ?: candidates.maxByOrNull { it.bounds!!.width() * it.bounds.height() * it.confidence }
        if (selected == null) {
            misses++
            if (misses > 3 || now - lastSeen > 900L) reset()
            // Never drive AF toward a stale point while a subject is actually missing.
            return null
        }
        misses = 0
        val continuous = last != null && matched != null
        confirmations = if (continuous) confirmations + 1 else 1
        val stable = if (continuous) selected.copy(x = last!!.x * .25f + selected.x * .75f,
            y = last.y * .25f + selected.y * .75f) else selected
        previous = stable
        lastSeen = now
        return stable.takeIf { confirmations >= 2 }
    }

    override fun close() = detector.close()

    companion object {
        private val animals = setOf("bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe")
        private val vehicles = setOf("bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat")
        internal fun accepts(category: String, label: String): Boolean = when (category) {
            "person" -> label == "person"
            "animals" -> label in animals
            "vehicles" -> label in vehicles
            "auto" -> label.isNotBlank() && label !in setOf("???", "background", "__background__")
            else -> false
        }
    }
}
