package com.hinnka.mycamera.processor

import com.hinnka.mycamera.model.SafeImage
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureProcessingState(
    val pendingCount: Int = 0,
    val completedCount: Long = 0,
    val failedCount: Long = 0,
    val memoryFailureCount: Long = 0,
)

/** Owns each shot until its entire merge, render and save operation has finished. */
object CaptureProcessingQueue {
    private class Request(val images: List<SafeImage>, val process: suspend () -> Unit)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Memory is bounded at camera admission by the 50 reserved/acquired ImageReader slots.
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(CaptureProcessingState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            for (request in requests) {
                var failed = false
                var memoryFailure = false
                try {
                    request.process()
                } catch (e: OutOfMemoryError) {
                    // A failed allocation belongs to this shot. Do not permanently lose the
                    // channel consumer and strand every later capture's completion callback.
                    failed = true
                    memoryFailure = true
                    PLog.e("CaptureProcessingQueue", "Capture exceeded its memory budget", e)
                } catch (e: Exception) {
                    failed = true
                    PLog.e("CaptureProcessingQueue", "Capture processing failed", e)
                } finally {
                    request.images.forEach { image ->
                        try { image.close() }
                        catch (e: OutOfMemoryError) { failed = true; memoryFailure = true }
                        catch (e: Exception) {
                            failed = true
                            PLog.e("CaptureProcessingQueue", "Failed to release a capture image", e)
                        }
                    }
                    mutableState.update {
                        it.copy(pendingCount = (it.pendingCount - 1).coerceAtLeast(0),
                            completedCount = it.completedCount + 1,
                            failedCount = it.failedCount + if (failed) 1L else 0L,
                            memoryFailureCount = it.memoryFailureCount + if (memoryFailure) 1L else 0L)
                    }
                }
            }
        }
    }

    fun enqueue(images: List<SafeImage>, process: suspend () -> Unit) {
        mutableState.update { it.copy(pendingCount = it.pendingCount + 1) }
        if (!requests.trySend(Request(images, process)).isSuccess) {
            mutableState.update { it.copy(pendingCount = (it.pendingCount - 1).coerceAtLeast(0)) }
            images.forEach { runCatching { it.close() } }
            error("Capture processing queue is unavailable")
        }
    }
}
