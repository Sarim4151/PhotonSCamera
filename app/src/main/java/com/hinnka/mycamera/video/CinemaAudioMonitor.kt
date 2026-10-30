package com.hinnka.mycamera.video

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Best-effort PCM16 headphone monitor. Never opens a second microphone or blocks its producer. */
class CinemaAudioMonitor private constructor(
    private val manager: AudioManager,
    private val output: AudioDeviceInfo,
    private val track: AudioTrack,
    private val bytesPerFrame: Int,
) : Closeable {
    private val queue = ArrayBlockingQueue<ByteArray>(4)
    private val trackLock = Any()
    @Volatile private var closed = false
    @Volatile private var paused = false
    val isActive: Boolean get() = !closed
    private val handler = Handler(Looper.getMainLooper())
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == output.id }) close()
        }
    }
    private val routingListener = AudioRouting.OnRoutingChangedListener { routing ->
        // A preferred device is a request, not a guarantee: reject any actual route change.
        val routed = routing.routedDevice
        if (routed != null && routed.id != output.id) close()
    }

    private fun begin() {
        manager.registerAudioDeviceCallback(deviceCallback, handler)
        track.addOnRoutingChangedListener(routingListener, handler)
        track.play()
        thread(name = "CinemaHeadphoneMonitor", isDaemon = true) {
            try {
                val routeDeadline = SystemClock.elapsedRealtime() + 2_000L
                while (!closed && track.routedDevice?.id != output.id) {
                    if (SystemClock.elapsedRealtime() >= routeDeadline) return@thread
                    Thread.sleep(10L)
                }
                while (!closed) {
                    val pcm = queue.poll(40L, TimeUnit.MILLISECONDS) ?: continue
                    synchronized(trackLock) {
                        if (!closed && !paused) {
                            if (track.routedDevice?.id != output.id) return@thread
                            // Nonblocking output: a slow Bluetooth sink drops samples, never stalls capture.
                            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
                            if (written < 0) return@thread
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: RuntimeException) {
                // Monitoring failure must not fail or alter the recording.
            } finally { close() }
        }
    }

    /** Copies only complete PCM frames; at most four small buffers await playback. */
    fun offer(pcm: ByteArray, size: Int) {
        if (closed || paused) return
        val length = size.coerceIn(0, pcm.size).let { it - it % bytesPerFrame }
        if (length == 0) return
        val copy = pcm.copyOf(length)
        if (!queue.offer(copy)) {
            queue.poll()
            queue.offer(copy)
        }
    }

    fun pause() {
        paused = true
        queue.clear()
        synchronized(trackLock) {
            if (!closed) {
                try { track.pause(); track.flush() } catch (_: RuntimeException) { close() }
            }
        }
    }

    /** A removed or changed route requires a fresh start, never automatic speaker fallback. */
    fun resume() {
        synchronized(trackLock) {
            if (closed) return
            if (manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.id == output.id }) {
                close()
                return
            }
            try {
                track.play()
                paused = false
            } catch (_: RuntimeException) { close() }
        }
    }

    override fun close() {
        synchronized(trackLock) {
            if (closed) return
            closed = true
            paused = true
            queue.clear()
            manager.unregisterAudioDeviceCallback(deviceCallback)
            track.removeOnRoutingChangedListener(routingListener)
            try { track.pause(); track.flush() } catch (_: RuntimeException) { }
            track.release()
        }
    }

    companion object {
        /** Null means no headphone route or unsupported output; the recording can continue normally. */
        fun start(context: Context, sampleRate: Int, channelCount: Int): CinemaAudioMonitor? {
            if (sampleRate <= 0 || channelCount !in 1..2) return null
            val manager = context.applicationContext.getSystemService(AudioManager::class.java) ?: return null
            val output = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in headphoneTypes }
                .sortedBy { if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) 1 else 0 }
                .firstOrNull() ?: return null
            val channels = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minimum = AudioTrack.getMinBufferSize(sampleRate, channels, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) return null
            var track: AudioTrack? = null
            return try {
                val created = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channels)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum, sampleRate * channelCount * 2 / 20))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = created
                if (created.state != AudioTrack.STATE_INITIALIZED || !created.setPreferredDevice(output)) {
                    created.release()
                    null
                } else {
                    val monitor = CinemaAudioMonitor(manager, output, created, channelCount * 2)
                    try {
                        monitor.begin()
                        monitor
                    } catch (_: RuntimeException) {
                        monitor.close()
                        null
                    }
                }
            } catch (_: RuntimeException) {
                track?.release()
                null
            }
        }

        private val headphoneTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
