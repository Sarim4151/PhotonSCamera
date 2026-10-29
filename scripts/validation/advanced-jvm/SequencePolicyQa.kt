package com.hinnka.mycamera.camera

// Desktop adapter for shot-snapshot policy; no Camera2/Android lifecycle is simulated here.
data class CameraState(
    val multiFrameOutputScale: Float? = 1f,
    val useLivePhoto: Boolean = true,
    val useMultipleExposure: Boolean = true,
    val burstCapturing: Boolean = true,
    val hdrBracketCapturing: Boolean = true,
    val hdrBracketFrameCount: Int = 3,
    val iso: Int = 640,
    val zoomRatio: Float = 2f,
    val useRaw: Boolean = true,
)
fun main() {
    for (raw in listOf(false, true)) {
        val original = CameraState(useRaw = raw)
        val source = original.forSequenceSource()
        check(source.multiFrameOutputScale == null && !source.useLivePhoto && !source.useMultipleExposure)
        check(!source.burstCapturing && !source.hdrBracketCapturing && source.hdrBracketFrameCount == 0)
        check(source.useRaw == raw && source.iso == original.iso && source.zoomRatio == original.zoomRatio)
        check(original.multiFrameOutputScale == 1f && original.useLivePhoto && original.hdrBracketFrameCount == 3)
    }
    println("PASS: actual source policy disables nested captures and preserves input/RAW/exposure/zoom")
}
