package com.hinnka.mycamera.expression

import kotlin.math.*

/** Compact smooth support guarantees that pixels outside the mouth patches are copied exactly. */
object ExpressionWarp {
    data class Point(val x: Float, val y: Float)
    data class Face(val left: Point, val right: Point, val upper: Point, val lower: Point)
    fun displacement(x: Float, y: Float, face: Face, strength: Float, relax: Boolean): Point {
        val dx = face.right.x - face.left.x
        val dy = face.right.y - face.left.y
        val width = hypot(dx, dy)
        if (width < 4f || strength <= 0f) return Point(0f, 0f)
        // Orient lift toward the measured upper lip, including sideways/upside-down portraits.
        val direction = if (dy * (face.upper.x - face.lower.x) -
            dx * (face.upper.y - face.lower.y) < 0f) -1f else 1f
        val nx = dy / width * direction
        val ny = -dx / width * direction
        val midX = (face.upper.x + face.lower.x) / 2f
        val midY = (face.upper.y + face.lower.y) / 2f
        val lift = (((face.left.x + face.right.x) / 2f - midX) * nx +
            ((face.left.y + face.right.y) / 2f - midY) * ny).coerceAtLeast(0f)
        val amount = (if (relax) -min(lift, width * .075f) else width * .06f) * strength.coerceIn(0f, 1f)
        val radius = width * .34f
        fun weight(p: Point): Float {
            val r = hypot(x - p.x, y - p.y) / radius
            return if (r >= 1f) 0f else (1f - r * r).pow(3)
        }
        val w = (weight(face.left) + weight(face.right)).coerceAtMost(1f)
        return Point(nx * amount * w, ny * amount * w)
    }
}
