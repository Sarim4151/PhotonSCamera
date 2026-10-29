package com.hinnka.mycamera.photosphere

import java.io.File
import kotlin.math.*

data class SphereVec3(val x: Double, val y: Double, val z: Double) {
    fun dot(other: SphereVec3) = x * other.x + y * other.y + z * other.z
    val length get() = sqrt(dot(this))
}
/** World axes are east/right +X, down +Y and forward at longitude zero +Z.
 * Basis maps JPEG pixel axes (right, down, optical forward) into that world.
 * JPEG orientation must already be baked: do not apply EXIF rotation again. */
data class SphereCameraBasis(val right: SphereVec3, val down: SphereVec3, val forward: SphereVec3) {
    init {
        require(listOf(right, down, forward).all { abs(it.length - 1.0) < 0.01 })
        require(abs(right.dot(down)) < 0.01 && abs(right.dot(forward)) < 0.01 && abs(down.dot(forward)) < 0.01)
        val cross = SphereVec3(right.y * down.z - right.z * down.y, right.z * down.x - right.x * down.z, right.x * down.y - right.y * down.x)
        require(cross.dot(forward) > 0.99) { "Camera basis must be right handed" }
    }
}
data class SphereIntrinsics(val width: Int, val height: Int, val fx: Double, val fy: Double, val cx: Double, val cy: Double) {
    init {
        require(width > 2 && height > 2 && fx.isFinite() && fy.isFinite() && fx > 0 && fy > 0)
        require(cx.isFinite() && cy.isFinite() && cx in 0.0..width.toDouble() && cy in 0.0..height.toDouble())
    }
}
data class SphereFrame(val jpeg: File, val basis: SphereCameraBasis, val intrinsics: SphereIntrinsics, val exposureGain: Double = 1.0) {
    init { require(exposureGain.isFinite() && exposureGain in 0.125..8.0) }
}
data class SpherePixel(val x: Double, val y: Double, val weight: Double)
object SphereGeometry {
    fun ray(longitude: Double, latitude: Double): SphereVec3 {
        val c = cos(latitude)
        return SphereVec3(sin(longitude) * c, -sin(latitude), cos(longitude) * c)
    }
    fun outputRay(x: Double, y: Double, width: Int, height: Int) = ray((x / width - 0.5) * 2 * PI, (0.5 - y / height) * PI)
    fun project(frame: SphereFrame, ray: SphereVec3): SpherePixel? {
        val z = frame.basis.forward.dot(ray)
        if (z <= 0.0) return null
        val k = frame.intrinsics
        val x = k.fx * frame.basis.right.dot(ray) / z + k.cx
        val y = k.fy * frame.basis.down.dot(ray) / z + k.cy
        if (x < 0 || y < 0 || x >= k.width - 1 || y >= k.height - 1) return null
        val edge = min(min(x, k.width - 1 - x) / k.width, min(y, k.height - 1 - y) / k.height)
        return SpherePixel(x, y, min(1.0, edge / 0.12).coerceAtLeast(0.00001).pow(2) * z * z)
    }
}
