package com.hinnka.mycamera.camera

/** The outer advanced sequence owns stacking and timing. Never nest RAWmax/JPGmax,
 * a Live Photo video, or a second exposure bracket inside one of its sources.
 * This is a shot snapshot; it must not mutate the user's camera preferences. */
internal fun CameraState.forSequenceSource(): CameraState = copy(
    multiFrameOutputScale = null,
    useLivePhoto = false,
    useMultipleExposure = false,
    burstCapturing = false,
    hdrBracketCapturing = false,
    hdrBracketFrameCount = 0,
)

/** A brief still flash belongs to one AE-metered exposure. A RAWmax/JPGmax burst
 * would turn it into continuous torch light and lock a potentially wrong flash
 * exposure/white balance for every frame. Preserve the user's HDR+ preference
 * for the next shot while routing this shot through ordinary still capture. */
internal fun CameraState.forSingleFlashStill(): CameraState = copy(
    multiFrameOutputScale = null,
)
