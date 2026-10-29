package com.hinnka.mycamera.camera

/** Owns temporary exposure settings; motion is frame-relative displacement per second. */
class MotionPriorityController {
    data class Exposure(val autoExposure: Boolean, val isoAuto: Boolean, val shutterAuto: Boolean,
                        val iso: Int, val shutterNs: Long, val exposureBias: Float = 0f)
    sealed interface Decision {
        data object None : Decision
        data object Unsupported : Decision
        data class Apply(val exposure: Exposure) : Decision
        data class Restore(val exposure: Exposure) : Decision
    }
    private var snapshot: Exposure? = null
    private var applied: Exposure? = null
    private var camera: String? = null
    private var candidateMoving: Boolean? = null
    private var candidateSince = 0L
    private var moving = false
    private var blocked = false

    /** Call on explicit user exposure edits; never restore across an intentional edit. */
    fun userOverride() { snapshot = null; applied = null; blocked = true; resetMotion() }
    /** Record the actual state after applying all setters, including sensor clamping. */
    fun acknowledge(exposure: Exposure) { if (snapshot != null) applied = exposure }
    private fun matches(a: Exposure, b: Exposure): Boolean = a.autoExposure == b.autoExposure &&
        a.isoAuto == b.isoAuto && a.shutterAuto == b.shutterAuto &&
        (a.isoAuto || a.iso == b.iso) && (a.shutterAuto || a.shutterNs == b.shutterNs)
    private fun resetMotion() { candidateMoving = null; candidateSince = 0L; moving = false }
    fun stop(exposure: Exposure): Decision {
        val original = snapshot
        val owned = applied?.let { matches(it, exposure) } == true
        snapshot = null; applied = null; camera = null; resetMotion()
        return if (original != null && owned) Decision.Restore(original) else Decision.None
    }
    fun update(enabled: Boolean, eligible: Boolean, supported: Boolean, cameraId: String,
               exposure: Exposure, minShutterNs: Long, maxShutterNs: Long,
               minIso: Int, maxIso: Int, relativeFrameMotion: Float?, nowMs: Long): Decision {
        if (!enabled) { blocked = false; return stop(exposure) }
        if (blocked) return Decision.None
        if (camera != null && camera != cameraId) {
            // The old lens snapshot cannot safely be restored to a new sensor.
            snapshot = null; applied = null; resetMotion(); camera = null
        }
        if (applied?.let { !matches(it, exposure) } == true) { userOverride(); return Decision.None }
        if (!eligible) return stop(exposure)
        if (!supported || minShutterNs <= 0 || maxShutterNs < minShutterNs || minIso <= 0 || maxIso < minIso) {
            val restore = stop(exposure)
            return if (restore is Decision.Restore) restore else Decision.Unsupported
        }
        val motion = relativeFrameMotion?.takeIf { it.isFinite() && it >= 0f }
        if (motion == null) return stop(exposure)
        // Separate enter/exit thresholds prevent noisy target tracking from toggling shutter.
        val desiredMoving = if (moving) motion > .035f else motion >= .08f
        if (candidateMoving != desiredMoving) { candidateMoving = desiredMoving; candidateSince = nowMs }
        if (nowMs - candidateSince < 1_000L) return Decision.None
        moving = desiredMoving
        val shutter = (if (moving) 2_000_000L else 8_000_000L).coerceIn(minShutterNs, maxShutterNs)
        val target = exposure.copy(autoExposure = false, isoAuto = true, shutterAuto = false,
            iso = exposure.iso.coerceIn(minIso, maxIso), shutterNs = shutter)
        if (applied != null && matches(target, exposure)) return Decision.None
        if (snapshot == null) snapshot = exposure
        camera = cameraId
        applied = target
        return Decision.Apply(target)
    }
}
