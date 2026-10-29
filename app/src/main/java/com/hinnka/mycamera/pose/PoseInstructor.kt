package com.hinnka.mycamera.pose

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.abs

internal data class PosePoint(val x: Float, val y: Float, val confidence: Float)
internal enum class PoseGuide { PORTRAIT, FULL_BODY }
internal enum class PoseHint { SEARCHING, CENTER, HEADROOM, SHOULDERS, HEAD, ARMS, FEET, READY, UNAVAILABLE }
internal data class PoseReading(val points: List<PosePoint> = emptyList(), val hint: PoseHint = PoseHint.SEARCHING)

/** CPU-only; constructed, called and closed on the same background dispatcher. No network. */
internal class PoseInstructor(context: Context) : AutoCloseable {
    private val landmarker = PoseLandmarker.createFromOptions(context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
            .setRunningMode(RunningMode.IMAGE).setNumPoses(1)
            .setMinPoseDetectionConfidence(0.6f).setMinPosePresenceConfidence(0.6f)
            .build())

    fun inspect(bitmap: Bitmap, guide: PoseGuide): PoseReading {
        val image = BitmapImageBuilder(bitmap).build()
        try {
            val points = landmarker.detect(image).landmarks().firstOrNull()?.map {
                PosePoint(it.x(), it.y(), minOf(it.visibility().orElse(0f), it.presence().orElse(0f)))
            }.orEmpty()
            return PoseReading(points, feedback(points, guide))
        } finally { image.close() }
    }
    override fun close() = landmarker.close()

    companion object {
        fun feedback(p: List<PosePoint>, guide: PoseGuide): PoseHint {
            fun good(i: Int) = p.getOrNull(i)?.let { it.confidence >= 0.6f && it.x in 0f..1f && it.y in 0f..1f } == true
            if (!listOf(0, 11, 12).all(::good)) return PoseHint.SEARCHING
            val nose = p[0]; val left = p[11]; val right = p[12]
            val span = abs(left.x - right.x).coerceAtLeast(0.03f)
            if (abs((left.x + right.x) / 2 - 0.5f) > 0.15f) return PoseHint.CENTER
            if (nose.y < 0.1f) return PoseHint.HEADROOM
            if (guide == PoseGuide.FULL_BODY && !listOf(23, 24, 27, 28, 31, 32).all(::good)) return PoseHint.FEET
            if (abs(left.y - right.y) / span > 0.18f) return PoseHint.SHOULDERS
            if (good(7) && good(8) && abs(p[7].y - p[8].y) / span > 0.14f) return PoseHint.HEAD
            if (listOf(13, 14, 15, 16, 23, 24).all(::good)) {
                if (abs(p[15].x - p[23].x) < span * 0.1f && abs(p[16].x - p[24].x) < span * 0.1f)
                    return PoseHint.ARMS
            }
            return PoseHint.READY
        }
    }
}
