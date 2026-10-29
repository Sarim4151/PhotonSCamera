package com.hinnka.mycamera.video

import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the native preprocessor for exactly one AudioRecord session. */
class DeviceVideoNoiseReduction {
    private var effect: NoiseSuppressor? = null
    private val mutableStatus = MutableStateFlow(VideoNoiseReductionStatus.OFF)
    val status = mutableStatus.asStateFlow()

    @Synchronized fun attach(sessionId: Int, requested: Boolean) {
        release()
        if (!requested) return // Preserve the platform's normal CAMCORDER path when disabled.
        if (!isAvailable()) { mutableStatus.value=VideoNoiseReductionStatus.UNAVAILABLE;return }
        try {
            val created=NoiseSuppressor.create(sessionId)
            if (created==null) { mutableStatus.value=VideoNoiseReductionStatus.UNAVAILABLE;return }
            effect=created
            created.setEnableStatusListener { _, enabled ->
                synchronized(this) {
                    if(effect===created) mutableStatus.value=if(enabled) VideoNoiseReductionStatus.ACTIVE else VideoNoiseReductionStatus.FAILED
                }
            }
            created.setControlStatusListener { _, _ ->
                synchronized(this) {
                    if(effect===created) {
                        mutableStatus.value=try {
                            if(created.enabled) VideoNoiseReductionStatus.ACTIVE else VideoNoiseReductionStatus.FAILED
                        } catch (_: RuntimeException) { VideoNoiseReductionStatus.FAILED }
                    }
                }
            }
            val result=created.setEnabled(true)
            mutableStatus.value=if(created.enabled && (result==AudioEffect.SUCCESS || !created.hasControl()))
                VideoNoiseReductionStatus.ACTIVE else VideoNoiseReductionStatus.FAILED
            PLog.i("VideoAudio", "Device NS session=$sessionId status=${mutableStatus.value}")
        } catch(e: RuntimeException) {
            PLog.w("VideoAudio", "Device noise suppression failed: ${e.message}")
            release();mutableStatus.value=VideoNoiseReductionStatus.FAILED
        } catch(e: LinkageError) {
            release();mutableStatus.value=VideoNoiseReductionStatus.UNAVAILABLE
        }
    }
    @Synchronized fun noAudio() { release();mutableStatus.value=VideoNoiseReductionStatus.NO_AUDIO }
    @Synchronized fun release() {
        val old=effect;effect=null // Late callbacks cannot change the next session's state.
        if(old!=null) {
            try { old.setEnableStatusListener(null);old.setControlStatusListener(null) } catch (_: RuntimeException) {}
            try { old.release() } catch (_: RuntimeException) {}
        }
        mutableStatus.value=VideoNoiseReductionStatus.OFF
    }
    companion object {
        fun isAvailable(): Boolean = try { NoiseSuppressor.isAvailable() }
            catch (_: RuntimeException) { false } catch (_: LinkageError) { false }
    }
}
